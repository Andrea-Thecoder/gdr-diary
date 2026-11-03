import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { THEME_COLORS, THEME_COOKIE_NAME, resolveTheme } from '../src/lib/theme/theme.ts';
import { rebaseWoff2Fonts, renderThemeScript } from './tokens.js';

const themeDirectory = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(themeDirectory, '..');
const buildDirectory = path.join(themeDirectory, '.build');
const resourcesDirectory = path.join(themeDirectory, 'themes', 'gdr-diary', 'login', 'resources');

const FONT_STYLESHEETS = [
	path.join(root, 'src', 'lib', 'styles', 'fonts.css'),
	...[
		'@fontsource/cinzel/latin-500.css',
		'@fontsource/cinzel/latin-600.css',
		'@fontsource/eb-garamond/latin-400.css',
		'@fontsource/eb-garamond/latin-500.css',
		'@fontsource/eb-garamond/latin-400-italic.css'
	].map((stylesheet) => path.join(root, 'node_modules', stylesheet))
];

function readFiles(directory) {
	const result = new Map();
	if (!fs.existsSync(directory)) {
		return result;
	}
	for (const entry of fs.readdirSync(directory, { withFileTypes: true, recursive: true })) {
		if (entry.isFile()) {
			const file = path.join(entry.parentPath, entry.name);
			result.set(path.relative(directory, file), fs.readFileSync(file));
		}
	}
	return result;
}

function prepareBuildDirectory() {
	fs.rmSync(buildDirectory, { recursive: true, force: true });
	fs.mkdirSync(buildDirectory, { recursive: true });
	const fonts = new Map();
	const stylesheets = FONT_STYLESHEETS.map((stylesheet) => {
		const rebased = rebaseWoff2Fonts(
			fs.readFileSync(stylesheet, 'utf8'),
			path.dirname(stylesheet),
			'../fonts'
		);
		for (const { name, source } of rebased.files) {
			fonts.set(name, source);
		}
		return rebased.css.trim();
	});
	fs.writeFileSync(path.join(buildDirectory, 'fonts.css'), `${stylesheets.join('\n\n')}\n`);
	return fonts;
}

function runTailwind(output) {
	const require = createRequire(import.meta.url);
	const packageFile = require.resolve('@tailwindcss/cli/package.json');
	const { bin } = require(packageFile);
	execFileSync(
		process.execPath,
		[
			path.join(path.dirname(packageFile), bin.tailwindcss),
			'--input',
			path.join(themeDirectory, 'theme.css'),
			'--output',
			output,
			'--minify'
		],
		{ cwd: root, stdio: ['ignore', 'ignore', 'inherit'] }
	);
}

function build(target) {
	const fonts = prepareBuildDirectory();
	fs.rmSync(path.join(target, 'css'), { recursive: true, force: true });
	fs.rmSync(path.join(target, 'fonts'), { recursive: true, force: true });
	fs.mkdirSync(path.join(target, 'css'), { recursive: true });
	fs.mkdirSync(path.join(target, 'fonts'), { recursive: true });
	fs.mkdirSync(path.join(target, 'js'), { recursive: true });
	fs.writeFileSync(
		path.join(target, 'js', 'theme.js'),
		renderThemeScript({
			light: resolveTheme('system', false),
			dark: resolveTheme('system', true),
			colors: THEME_COLORS,
			cookieName: THEME_COOKIE_NAME
		})
	);
	runTailwind(path.join(target, 'css', 'gdr.css'));
	for (const [name, source] of fonts) {
		fs.copyFileSync(source, path.join(target, 'fonts', name));
	}
}

function differences(expected, actual) {
	const names = new Set([...expected.keys(), ...actual.keys()]);
	return [...names].filter((name) => {
		const left = expected.get(name);
		const right = actual.get(name);
		return left === undefined || right === undefined || !left.equals(right);
	});
}

function main() {
	if (!process.argv.includes('--check')) {
		build(resourcesDirectory);
		return;
	}
	const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'gdr-keycloak-theme-'));
	try {
		build(temporary);
		const outdated = differences(
			readFiles(temporary),
			new Map(
				[...readFiles(resourcesDirectory)].filter(
					([name]) =>
						name.startsWith('css') ||
						name.startsWith('fonts') ||
						name === path.join('js', 'theme.js')
				)
			)
		);
		if (outdated.length > 0) {
			console.error(
				`Keycloak theme out of date (${outdated.join(', ')}). Run: npm run theme:build`
			);
			process.exitCode = 1;
		}
	} finally {
		fs.rmSync(temporary, { recursive: true, force: true });
	}
}

main();

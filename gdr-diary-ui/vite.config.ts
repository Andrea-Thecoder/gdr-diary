import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';
import { playwright } from '@vitest/browser-playwright';
import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';

// pdf.js carica da solo i suoi file di supporto (motore degli script dei moduli, font, decodificatori
// di immagini): li serviamo da /pdfjs/ in sviluppo e li copiamo nella build.
function pdfjsAssets(): Plugin {
	const root = path.resolve('node_modules/pdfjs-dist');
	const folders = ['wasm', 'standard_fonts', 'cmaps', 'iccs'];
	const types: Record<string, string> = {
		'.js': 'text/javascript',
		'.wasm': 'application/wasm'
	};

	function walk(folder: string): string[] {
		return readdirSync(folder).flatMap((name) => {
			const full = path.join(folder, name);
			return statSync(full).isDirectory() ? walk(full) : [full];
		});
	}

	function served(): Map<string, string> {
		return new Map(
			folders.flatMap((folder) =>
				walk(path.join(root, folder)).map((file): [string, string] => [
					path.relative(root, file).split(path.sep).join('/'),
					file
				])
			)
		);
	}

	return {
		name: 'pdfjs-assets',
		configureServer(server) {
			const files = served();
			server.middlewares.use('/pdfjs/', (request, response, next) => {
				const file = files.get(
					decodeURIComponent((request.url ?? '').split('?')[0] ?? '').slice(1)
				);
				if (file === undefined) {
					next();
					return;
				}
				response.setHeader('Content-Type', types[path.extname(file)] ?? 'application/octet-stream');
				response.end(readFileSync(file));
			});
		},
		generateBundle() {
			if (this.environment.name !== 'client') {
				return;
			}
			for (const [name, file] of served()) {
				this.emitFile({ type: 'asset', fileName: `pdfjs/${name}`, source: readFileSync(file) });
			}
		}
	};
}

export default defineConfig({
	plugins: [
		pdfjsAssets(),
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			adapter: adapter({ fallback: 'index.html' })
		})
	],
	test: {
		expect: { requireAssertions: true },
		projects: [
			{
				extends: './vite.config.ts',
				test: {
					name: 'client',
					browser: {
						enabled: true,
						provider: playwright(),
						instances: [{ browser: 'chromium', headless: true }]
					},
					include: ['src/**/*.svelte.{test,spec}.{js,ts}']
				}
			},

			{
				extends: './vite.config.ts',
				test: {
					name: 'server',
					environment: 'node',
					include: ['src/**/*.{test,spec}.{js,ts}', 'keycloak-theme/**/*.{test,spec}.{js,ts}'],
					exclude: ['src/**/*.svelte.{test,spec}.{js,ts}']
				}
			}
		]
	}
});

import path from 'node:path';

const FONT_SOURCE = /url\(\s*(['"]?)([^)'"]+)\1\s*\)\s*format\(\s*(['"]?)([a-z0-9]+)\3\s*\)/g;
const FONT_SRC_DECLARATION = /src:\s*([^;]+);/g;

export function rebaseWoff2Fonts(css, cssDirectory, targetDirectory) {
	const files = new Map();
	const rebased = css.replace(FONT_SRC_DECLARATION, (declaration, value) => {
		const sources = [];
		for (const match of value.matchAll(FONT_SOURCE)) {
			const url = match[2];
			if (match[4] === 'woff2' && url !== undefined) {
				const name = path.basename(url);
				files.set(name, path.resolve(cssDirectory, url));
				sources.push(`url('${targetDirectory}/${name}') format('woff2')`);
			}
		}
		if (sources.length === 0) {
			throw new Error(`No woff2 source in: ${declaration.trim()}`);
		}
		return `src: ${sources.join(', ')};`;
	});
	return { css: rebased, files: [...files].map(([name, source]) => ({ name, source })) };
}

export function renderThemeScript({ light, dark, colors, cookieName }) {
	return `(() => {
	const light = ${JSON.stringify(light)};
	const dark = ${JSON.stringify(dark)};
	const colors = ${JSON.stringify(colors)};
	const cookieName = ${JSON.stringify(cookieName)};
	const media = window.matchMedia('(prefers-color-scheme: dark)');
	const stored = () => {
		const entry = document.cookie.split('; ').find((part) => part.startsWith(cookieName + '='));
		const value = entry === undefined ? null : decodeURIComponent(entry.slice(cookieName.length + 1));
		return value === light || value === dark ? value : null;
	};
	const apply = () => {
		const theme = stored() ?? (media.matches ? dark : light);
		document.documentElement.setAttribute('data-theme', theme);
		document.querySelector('meta[name="theme-color"]')?.setAttribute('content', colors[theme]);
	};
	apply();
	media.addEventListener('change', apply);
})();
`;
}

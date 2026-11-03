import { describe, expect, it } from 'vitest';
import { rebaseWoff2Fonts, renderThemeScript } from './tokens.js';

describe('rebaseWoff2Fonts', () => {
	const FONT = `@font-face {
	font-family: 'Cinzel';
	src: url(./files/cinzel-latin-500-normal.woff2) format('woff2'), url(./files/cinzel-latin-500-normal.woff) format('woff');
}`;

	it('keeps only the woff2 source and points it to the target directory', () => {
		const { css } = rebaseWoff2Fonts(FONT, '/pkg/cinzel', '../fonts');
		expect(css).toContain("src: url('../fonts/cinzel-latin-500-normal.woff2') format('woff2');");
		expect(css).not.toContain(".woff'");
	});

	it('lists the files to copy with their absolute source', () => {
		const { files } = rebaseWoff2Fonts(FONT, '/pkg/cinzel', '../fonts');
		expect(files).toEqual([
			{
				name: 'cinzel-latin-500-normal.woff2',
				source: '/pkg/cinzel/files/cinzel-latin-500-normal.woff2'
			}
		]);
	});

	it('resolves quoted urls with parent directories', () => {
		const css = "@font-face { src: url('../../node_modules/x/files/a.woff2') format('woff2'); }";
		const { files } = rebaseWoff2Fonts(css, '/app/src/lib/styles', '../fonts');
		expect(files).toEqual([{ name: 'a.woff2', source: '/app/src/node_modules/x/files/a.woff2' }]);
	});

	it('fails when a font has no woff2 source', () => {
		const css = "@font-face { src: url(a.woff) format('woff'); }";
		expect(() => rebaseWoff2Fonts(css, '/x', '../fonts')).toThrow('No woff2 source');
	});
});

describe('renderThemeScript', () => {
	const options = {
		light: 'pergamena',
		dark: 'viola',
		colors: { pergamena: '#F1E6CC', viola: '#12031F' },
		cookieName: 'gdr-theme'
	};

	function run({ cookie, systemDark }) {
		const attributes = {};
		const meta = { content: '#F1E6CC' };
		const listeners = [];
		const media = {
			matches: systemDark,
			addEventListener: (type, listener) => listeners.push({ type, listener })
		};
		const document = {
			cookie,
			documentElement: { setAttribute: (name, value) => (attributes[name] = value) },
			querySelector: (selector) =>
				selector === 'meta[name="theme-color"]'
					? { setAttribute: (name, value) => (meta[name] = value) }
					: null
		};
		const window = { matchMedia: () => media };
		new Function('window', 'document', renderThemeScript(options))(window, document);
		return { attributes, meta, media, listeners };
	}

	it('uses the theme saved in the cookie, even against the system', () => {
		expect(run({ cookie: 'a=1; gdr-theme=viola; b=2', systemDark: false }).attributes).toEqual({
			'data-theme': 'viola'
		});
		expect(run({ cookie: 'gdr-theme=pergamena', systemDark: true }).attributes).toEqual({
			'data-theme': 'pergamena'
		});
	});

	it('follows the system when the cookie is missing or invalid', () => {
		expect(run({ cookie: '', systemDark: true }).attributes['data-theme']).toBe('viola');
		expect(run({ cookie: 'x=1', systemDark: false }).attributes['data-theme']).toBe('pergamena');
		expect(run({ cookie: 'gdr-theme=rosa', systemDark: true }).attributes['data-theme']).toBe(
			'viola'
		);
	});

	it('does not confuse a cookie that only ends with the same name', () => {
		expect(
			run({ cookie: 'other-gdr-theme=viola', systemDark: false }).attributes['data-theme']
		).toBe('pergamena');
	});

	it('updates the browser bar color', () => {
		expect(run({ cookie: 'gdr-theme=viola', systemDark: false }).meta.content).toBe('#12031F');
	});

	it('reacts when the system theme changes', () => {
		const { listeners } = run({ cookie: '', systemDark: false });
		expect(listeners.map(({ type }) => type)).toEqual(['change']);
	});
});

import { Editor } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';
import '../../routes/layout.css';
import { required } from '#lib/test-support.js';
import { diaryExtensions } from './extensions.js';
import { TEXT_COLORS } from './text-color.js';

const WORST_PAPER = [227, 214, 186];
const THEMES = ['pergamena', 'viola'] as const;
const created: { editor: Editor; host: HTMLElement }[] = [];

function channels(css: string): number[] {
	return (css.match(/\d+(\.\d+)?/g) ?? []).slice(0, 3).map(Number);
}

function luminance(rgb: number[]): number {
	const [r = 0, g = 0, b = 0] = rgb.map((value) => {
		const unit = value / 255;
		return Math.pow((unit + 0.055) / 1.055, 2.4);
	});
	return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(foreground: number[], background: number[]): number {
	const [light = 0, dark = 0] = [luminance(foreground), luminance(background)].sort(
		(a, b) => b - a
	);
	return (light + 0.05) / (dark + 0.05);
}

function make(html: string): Editor {
	const host = document.createElement('div');
	host.style.cssText = "width:378px;--lh:36px;--diary-font:'Georgia';--diary-size:20px;";
	const element = document.createElement('div');
	host.append(element);
	document.body.append(host);
	const editor = new Editor({
		element,
		extensions: diaryExtensions(),
		content: html,
		editorProps: { attributes: { class: 'diary-prose' } }
	});
	created.push({ editor, host });
	return editor;
}

function tokenColor(editor: Editor, token: string): string {
	const probe = document.createElement('span');
	probe.style.color = `var(${token})`;
	editor.view.dom.append(probe);
	const resolved = getComputedStyle(probe).color;
	probe.remove();
	return resolved;
}

afterEach(() => {
	document.documentElement.removeAttribute('data-theme');
	for (const { editor, host } of created.splice(0)) {
		editor.destroy();
		host.remove();
	}
});

describe.each(THEMES)('legibility of the page in the %s theme', (theme) => {
	it.each(TEXT_COLORS.map((color) => color.key))(
		'reads the %s ink on the darkest part of the paper',
		(key) => {
			document.documentElement.setAttribute('data-theme', theme);
			const editor = make('<p>ciao</p>');

			expect(contrast(channels(tokenColor(editor, `--ink-${key}`)), WORST_PAPER)).toBeGreaterThan(
				4.4
			);
		}
	);

	it('draws the separator line in a color that stands out from the paper', () => {
		document.documentElement.setAttribute('data-theme', theme);
		const editor = make('<p>sopra</p><hr><p>sotto</p>');
		const line = getComputedStyle(required(editor.view.dom.querySelector('hr'), 'Linea'));

		expect(line.backgroundImage).toContain(tokenColor(editor, '--page-line'));
		expect(line.backgroundSize).toContain('2px');
		expect(contrast(channels(tokenColor(editor, '--page-line')), WORST_PAPER)).toBeGreaterThan(3);
	});

	it('draws the quote bar and the list markers in the same readable color', () => {
		document.documentElement.setAttribute('data-theme', theme);
		const editor = make('<blockquote><p>citazione</p></blockquote><ul><li><p>voce</p></li></ul>');
		const bar = getComputedStyle(
			required(editor.view.dom.querySelector('blockquote'), 'Citazione')
		);
		const marker = getComputedStyle(
			required(editor.view.dom.querySelector('li'), 'Voce'),
			'::marker'
		);

		expect(bar.borderLeftColor).toBe(tokenColor(editor, '--page-line'));
		expect(marker.color).toBe(tokenColor(editor, '--page-line'));
	});

	it('thickens the colored words so the handwriting does not look washed out', () => {
		document.documentElement.setAttribute('data-theme', theme);
		const editor = make('<p><span data-color="magenta">viola</span> e normale</p>');
		const span = getComputedStyle(required(editor.view.dom.querySelector('[data-color]'), 'Testo'));

		expect(span.webkitTextStrokeWidth).toBe('0.35px');
	});
});

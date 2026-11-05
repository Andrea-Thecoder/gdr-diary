import { Editor, type JSONContent } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';
import '../../routes/layout.css';
import { required } from '#lib/test-support.js';
import { diaryExtensions } from './extensions.js';
import { isTextColor, TEXT_COLORS } from './text-color.js';

const created: { editor: Editor; host: HTMLElement }[] = [];

function make(content: string | JSONContent): Editor {
	const host = document.createElement('div');
	host.style.cssText = "width:378px;--lh:36px;--diary-font:'Georgia';--diary-size:20px;";
	const element = document.createElement('div');
	host.append(element);
	document.body.append(host);
	const editor = new Editor({
		element,
		extensions: diaryExtensions(),
		content,
		editorProps: { attributes: { class: 'diary-prose' } }
	});
	created.push({ editor, host });
	return editor;
}

function plain(text: string): JSONContent {
	return { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] };
}

function colored(editor: Editor): string[] {
	return Array.from(editor.view.dom.querySelectorAll('[data-color]')).map(
		(node) => node.textContent
	);
}

function tokenColor(key: string): string {
	const probe = document.createElement('span');
	probe.style.color = `var(--ink-${key})`;
	required(created[0], 'Editor').host.append(probe);
	return getComputedStyle(probe).color;
}

afterEach(() => {
	for (const { editor, host } of created.splice(0)) {
		editor.destroy();
		host.remove();
	}
});

describe('text color', () => {
	it('colors only the selected words and saves the palette key in the content', () => {
		const editor = make(plain('ciao come stai'));
		editor.commands.setTextSelection({ from: 6, to: 10 });
		editor.commands.setTextColor('red');

		expect(colored(editor)).toEqual(['come']);
		const marks = editor.getJSON().content?.[0]?.content?.flatMap((node) => node.marks ?? []);
		expect(marks).toEqual([{ type: 'textColor', attrs: { color: 'red' } }]);
	});

	it('applies to the words typed after choosing it at the cursor', () => {
		const editor = make(plain('ciao '));
		editor.commands.focus('end');
		editor.commands.setTextColor('green');
		editor.commands.insertContent('verde');
		editor.commands.unsetTextColor();
		editor.commands.insertContent(' dopo');

		expect(colored(editor)).toEqual(['verde']);
		expect(editor.getText()).toBe('ciao verde dopo');
	});

	it('replaces the color instead of stacking and goes back to automatic when removed', () => {
		const editor = make(plain('ciao come stai'));
		editor.commands.setTextSelection({ from: 6, to: 10 });
		editor.commands.setTextColor('red');
		editor.commands.setTextColor('blue');

		expect(editor.isActive('textColor', { color: 'red' })).toBe(false);
		expect(editor.isActive('textColor', { color: 'blue' })).toBe(true);
		expect(editor.view.dom.querySelectorAll('[data-color="blue"]')).toHaveLength(1);

		editor.commands.unsetTextColor();
		expect(colored(editor)).toEqual([]);
	});

	it('colors one line and leaves the others alone', () => {
		const editor = make({
			type: 'doc',
			content: [
				{ type: 'paragraph', content: [{ type: 'text', text: 'prima riga' }] },
				{ type: 'paragraph', content: [{ type: 'text', text: 'seconda riga' }] }
			]
		});
		editor.commands.setTextSelection({ from: 13, to: 25 });
		editor.commands.setTextColor('purple');

		expect(colored(editor)).toEqual(['seconda riga']);
	});

	it.each(TEXT_COLORS.map((color) => color.key))(
		'paints the text with the palette color %s, different from the ink of the page',
		(key) => {
			const editor = make(plain('ciao come stai'));
			editor.commands.setTextSelection({ from: 6, to: 10 });
			editor.commands.setTextColor(key);
			const span = required(editor.view.dom.querySelector('[data-color]'), 'Testo colorato');

			expect(getComputedStyle(span).color).toBe(tokenColor(key));
			expect(getComputedStyle(span).color).not.toBe(getComputedStyle(editor.view.dom).color);
		}
	);

	it('wins over the color of a title written in the same words', () => {
		const editor = make(plain('ciao come stai'));
		editor.commands.setTextSelection({ from: 6, to: 10 });
		editor.commands.toggleTitle(1);
		editor.commands.setTextColor('blue');
		const span = required(editor.view.dom.querySelector('[data-color]'), 'Testo colorato');

		expect(getComputedStyle(span).color).toBe(tokenColor('blue'));
	});

	it('keeps only the colors of the palette from pasted html', () => {
		const editor = make(
			'<p>a <span data-color="red">rosso</span> <span data-color="url(x)">no</span> <span data-color="">vuoto</span></p>'
		);

		expect(colored(editor)).toEqual(['rosso']);
		expect(editor.getText()).toBe('a rosso no vuoto');
	});

	it.each([
		['red', true],
		['brown', true],
		['#ff0000', false],
		['', false],
		[null, false],
		[3, false]
	])('knows whether %j is a palette key', (value, expected) => {
		expect(isTextColor(value)).toBe(expected);
	});
});

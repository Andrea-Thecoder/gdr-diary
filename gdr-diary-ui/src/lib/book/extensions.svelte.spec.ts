import { Editor, type JSONContent } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';
import { diaryExtensions } from './extensions.js';

const created: { editor: Editor; host: HTMLElement }[] = [];

function make(content: string): Editor {
	const host = document.createElement('div');
	const element = document.createElement('div');
	host.append(element);
	document.body.append(host);
	const editor = new Editor({ element, extensions: diaryExtensions(), content });
	created.push({ editor, host });
	return editor;
}

function listAttrs(editor: Editor): JSONContent['attrs'] {
	return editor.getJSON().content.find((node) => node.type === 'orderedList')?.attrs;
}

afterEach(() => {
	for (const { editor, host } of created.splice(0)) {
		editor.destroy();
		host.remove();
	}
});

describe('ordered list numbering type', () => {
	it.each(['1', 'a', 'A', 'i', 'I'])('keeps the allowed type %s from pasted html', (type) => {
		const editor = make(`<ol type="${type}"><li><p>uno</p></li></ol>`);

		expect(listAttrs(editor)?.['type']).toBe(type);
	});

	it.each(['x', 'disc', 'decimal', 'abc', ''])(
		'drops the type %j that the backend would refuse',
		(type) => {
			const editor = make(`<ol type="${type}"><li><p>uno</p></li></ol>`);

			expect(listAttrs(editor)?.['type']).toBeNull();
		}
	);

	it('maps a word-style list-style-type to an allowed type', () => {
		const editor = make('<ol style="list-style-type: lower-roman"><li><p>uno</p></li></ol>');

		expect(listAttrs(editor)?.['type']).toBe('i');
	});

	it('drops a list-style-type that has no allowed type', () => {
		const editor = make('<ol style="list-style-type: hebrew"><li><p>uno</p></li></ol>');

		expect(listAttrs(editor)?.['type']).toBeNull();
	});

	it('keeps the start number next to the type', () => {
		const editor = make('<ol type="a" start="3"><li><p>uno</p></li></ol>');

		expect(listAttrs(editor)).toEqual({ start: 3, type: 'a' });
	});

	it('still creates and removes lists from the toolbar commands', () => {
		const editor = make('<p>uno</p>');

		editor.commands.toggleOrderedList();
		expect(listAttrs(editor)).toEqual({ start: 1, type: null });

		editor.commands.toggleOrderedList();
		expect(listAttrs(editor)).toBeUndefined();
	});
});

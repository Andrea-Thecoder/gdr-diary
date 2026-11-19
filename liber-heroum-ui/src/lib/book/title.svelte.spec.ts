import { Editor } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';
import '../../routes/layout.css';
import { required } from '#lib/test-support.js';
import { diaryExtensions } from './extensions.js';

const created: { editor: Editor; host: HTMLElement }[] = [];

function make(text: string): Editor {
	const host = document.createElement('div');
	host.style.cssText = "width:378px;--lh:36px;--diary-font:'Georgia';--diary-size:20px;";
	const element = document.createElement('div');
	host.append(element);
	document.body.append(host);
	const editor = new Editor({
		element,
		extensions: diaryExtensions(),
		content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text }] }] },
		editorProps: { attributes: { class: 'diary-prose' } }
	});
	created.push({ editor, host });
	return editor;
}

function titled(editor: Editor): string[] {
	return Array.from(editor.view.dom.querySelectorAll('[data-title]')).map(
		(node) => node.textContent
	);
}

afterEach(() => {
	for (const { editor, host } of created.splice(0)) {
		editor.destroy();
		host.remove();
	}
});

describe('inline titles', () => {
	it('styles only the selected words and leaves the rest of the line alone', () => {
		const editor = make('ciao come stai');
		editor.commands.setTextSelection({ from: 6, to: 10 });
		editor.commands.toggleTitle(2);

		expect(titled(editor)).toEqual(['come']);
		expect(editor.view.dom.querySelectorAll('h1, h2')).toHaveLength(0);
		expect(editor.isActive('title', { level: 2 })).toBe(true);
	});

	it('applies to the words typed after switching it on at the cursor', () => {
		const editor = make('ciao ');
		editor.commands.focus('end');
		editor.commands.toggleTitle(1);
		editor.commands.insertContent('titolo');
		editor.commands.toggleTitle(1);
		editor.commands.insertContent(' dopo');

		expect(titled(editor)).toEqual(['titolo']);
		expect(editor.getText()).toBe('ciao titolo dopo');
	});

	it('switches level instead of stacking and removes it when toggled twice', () => {
		const editor = make('ciao come stai');
		editor.commands.setTextSelection({ from: 6, to: 10 });
		editor.commands.toggleTitle(1);
		editor.commands.toggleTitle(2);

		expect(editor.isActive('title', { level: 1 })).toBe(false);
		expect(editor.isActive('title', { level: 2 })).toBe(true);
		expect(editor.view.dom.querySelectorAll('[data-title="2"]')).toHaveLength(1);

		editor.commands.toggleTitle(2);
		expect(titled(editor)).toEqual([]);
	});

	it('makes the titled words larger without touching the other lines', () => {
		const editor = make('ciao come stai');
		const body = Number.parseFloat(getComputedStyle(editor.view.dom).fontSize);
		editor.commands.setTextSelection({ from: 6, to: 10 });
		editor.commands.toggleTitle(1);
		const span = required(editor.view.dom.querySelector('[data-title]'), 'Titolo');

		expect(Number.parseFloat(getComputedStyle(span).fontSize)).toBeGreaterThan(body * 1.5);
	});
});

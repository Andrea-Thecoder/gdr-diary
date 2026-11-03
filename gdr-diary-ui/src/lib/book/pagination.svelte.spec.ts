import { Editor, type JSONContent } from '@tiptap/core';
import { afterEach, describe, expect, it } from 'vitest';
import type { Node as ProseNode } from '@tiptap/pm/model';
import '../../routes/layout.css';
import { diaryExtensions } from './extensions.js';
import { BODY_HEIGHT } from './layout.js';
import {
	isBlankDoc,
	isFull,
	overflows,
	isBlankRest,
	prependDoc,
	prependToEditor,
	splitOverflow
} from './pagination.js';

const created: { editor: Editor; host: HTMLElement }[] = [];

function paragraph(text: string): JSONContent {
	return { type: 'paragraph', content: [{ type: 'text', text }] };
}

function make(content: JSONContent[], scale = 1): Editor {
	const host = document.createElement('div');
	host.style.cssText = `width:${String(378)}px;height:${String(BODY_HEIGHT)}px;--lh:36px;--diary-font:'Georgia';--diary-size:20px;transform:scale(${String(scale)});transform-origin:top left;`;
	const element = document.createElement('div');
	element.className = 'size-full overflow-hidden';
	element.style.cssText = 'height:100%;overflow:hidden;';
	host.append(element);
	document.body.append(host);
	const editor = new Editor({
		element,
		extensions: diaryExtensions(),
		content: { type: 'doc', content },
		editorProps: { attributes: { class: 'diary-prose' } }
	});
	created.push({ editor, host });
	return editor;
}

function textOf(doc: JSONContent | undefined): string {
	if (doc === undefined) {
		return '';
	}
	if (doc.type === 'text') {
		return doc.text ?? '';
	}
	return (doc.content ?? []).map((child) => textOf(child)).join('\n');
}

afterEach(() => {
	for (const { editor, host } of created.splice(0)) {
		editor.destroy();
		host.remove();
	}
});

function emptyItems(list: ProseNode | null | undefined): number[] {
	const empty: number[] = [];
	list?.forEach((item, _offset, index) => {
		if (item.textContent.trim() === '') {
			empty.push(index);
		}
	});
	return empty;
}

describe('overflows', () => {
	it('is false while the text fits in fifteen lines', () => {
		const editor = make(
			Array.from({ length: 15 }, (_, index) => paragraph(`riga ${String(index)}`))
		);
		expect(overflows(editor, BODY_HEIGHT)).toBe(false);
	});

	it('is true from the sixteenth line', () => {
		const editor = make(
			Array.from({ length: 16 }, (_, index) => paragraph(`riga ${String(index)}`))
		);
		expect(overflows(editor, BODY_HEIGHT)).toBe(true);
	});

	it('measures the same when the page is scaled down', () => {
		const fits = make(
			Array.from({ length: 15 }, (_, index) => paragraph(`riga ${String(index)}`)),
			0.7
		);
		expect(overflows(fits, BODY_HEIGHT)).toBe(false);
		const over = make(
			Array.from({ length: 16 }, (_, index) => paragraph(`riga ${String(index)}`)),
			0.7
		);
		expect(overflows(over, BODY_HEIGHT)).toBe(true);
	});
});

describe('splitOverflow', () => {
	it('returns null when nothing overflows', () => {
		const editor = make([paragraph('una riga')]);
		expect(splitOverflow(editor, BODY_HEIGHT)).toBeNull();
	});

	it('moves whole paragraphs that do not fit and keeps the first fifteen', () => {
		const lines = Array.from({ length: 20 }, (_, index) => `riga ${String(index + 1)}`);
		const editor = make(lines.map((line) => paragraph(line)));
		const split = splitOverflow(editor, BODY_HEIGHT);
		expect(split).not.toBeNull();
		expect(overflows(editor, BODY_HEIGHT)).toBe(false);
		expect(editor.state.doc.childCount).toBe(15);
		expect(textOf(split?.rest)).toBe(lines.slice(15).join('\n'));
	});

	it('splits a long paragraph between words', () => {
		const words = Array.from({ length: 400 }, (_, index) => `parola${String(index)}`);
		const original = words.join(' ');
		const editor = make([paragraph(original)]);
		const split = splitOverflow(editor, BODY_HEIGHT);
		expect(split).not.toBeNull();
		expect(overflows(editor, BODY_HEIGHT)).toBe(false);
		const kept = editor.getText();
		const rest = textOf(split?.rest);
		expect(`${kept}${rest}`.replace(/\s+/g, ' ').trim()).toBe(original);
		expect(kept.endsWith(' ') || rest.startsWith('parola')).toBe(true);
		expect(/parola\d+$/.test(kept.trim())).toBe(true);
	});

	it('keeps list items whole and does not leave an empty item behind', () => {
		const items = Array.from({ length: 20 }, (_, index) => ({
			type: 'listItem',
			content: [paragraph(`voce ${String(index + 1)}`)]
		}));
		const editor = make([{ type: 'bulletList', content: items }]);
		const split = splitOverflow(editor, BODY_HEIGHT);
		expect(split?.rest.content?.[0]?.type).toBe('bulletList');
		const list = editor.state.doc.firstChild;
		expect(list?.childCount).toBe(15);
		expect(emptyItems(list)).toEqual([]);
		expect(split?.rest.content?.[0]?.content?.length).toBe(5);
	});

	it('moves a horizontal rule that falls outside the page', () => {
		const content: JSONContent[] = Array.from({ length: 15 }, (_, index) =>
			paragraph(`riga ${String(index)}`)
		);
		content.push({ type: 'horizontalRule' }, paragraph('dopo'));
		const editor = make(content);
		const split = splitOverflow(editor, BODY_HEIGHT);
		expect(split?.rest.content?.map((node) => node.type)).toEqual(['horizontalRule', 'paragraph']);
	});

	it('reports where the cursor lands in the moved text', () => {
		const editor = make(
			Array.from({ length: 17 }, (_, index) => paragraph(`riga ${String(index + 1)}`))
		);
		editor.commands.focus('end');
		const split = splitOverflow(editor, BODY_HEIGHT);
		expect(split?.cursor).not.toBeNull();
		const copy = make([paragraph('esistente')]);
		prependToEditor(copy, split?.rest ?? { type: 'doc' }, split?.cursor ?? null);
		expect(copy.state.selection.head).toBe(split?.cursor);
		expect(textOf(copy.getJSON())).toBe('riga 16\nriga 17\nesistente');
	});

	it('leaves the cursor alone when it stays in the kept text', () => {
		const editor = make(
			Array.from({ length: 17 }, (_, index) => paragraph(`riga ${String(index + 1)}`))
		);
		editor.commands.focus('start');
		const split = splitOverflow(editor, BODY_HEIGHT);
		expect(split?.cursor).toBeNull();
	});

	it('is not undone by the history of the editor', () => {
		const editor = make(
			Array.from({ length: 17 }, (_, index) => paragraph(`riga ${String(index + 1)}`))
		);
		splitOverflow(editor, BODY_HEIGHT);
		editor.commands.undo();
		expect(editor.state.doc.childCount).toBe(15);
	});
});

describe('a full page', () => {
	function lines(count: number): JSONContent[] {
		return Array.from({ length: count }, (_, index) => paragraph(`riga ${String(index + 1)}`));
	}

	it('is full only when one more line would not fit', () => {
		expect(isFull(make(lines(14)), BODY_HEIGHT, 36)).toBe(false);
		expect(isFull(make(lines(15)), BODY_HEIGHT, 36)).toBe(true);
	});

	it('is never full when empty', () => {
		expect(isFull(make([{ type: 'paragraph' }]), BODY_HEIGHT, 36)).toBe(false);
	});

	it('is measured the same way when the page is scaled down', () => {
		expect(isFull(make(lines(14), 0.6), BODY_HEIGHT, 36)).toBe(false);
		expect(isFull(make(lines(15), 0.6), BODY_HEIGHT, 36)).toBe(true);
	});
});

describe('moving text onto a closed page', () => {
	it('opens the page to writing so the cursor can follow the text', () => {
		const editor = make([{ type: 'paragraph' }]);
		editor.setEditable(false, false);
		prependToEditor(editor, { type: 'doc', content: [paragraph('avanzo')] }, null);

		expect(editor.isEditable).toBe(true);
		expect(editor.getText()).toBe('avanzo');
	});
});

describe('document helpers', () => {
	it('recognises a blank page', () => {
		expect(isBlankDoc({ type: 'doc' })).toBe(true);
		expect(isBlankDoc({ type: 'doc', content: [{ type: 'paragraph' }] })).toBe(true);
		expect(isBlankDoc({ type: 'doc', content: [paragraph('x')] })).toBe(false);
		expect(
			isBlankDoc({ type: 'doc', content: [{ type: 'paragraph' }, { type: 'paragraph' }] })
		).toBe(false);
	});

	it('puts the moved text before the existing text, or replaces a blank page', () => {
		const rest: JSONContent = { type: 'doc', content: [paragraph('avanzo')] };
		const existing: JSONContent = { type: 'doc', content: [paragraph('esistente')] };
		expect(prependDoc(existing, rest).content?.map((node) => textOf(node))).toEqual([
			'avanzo',
			'esistente'
		]);
		expect(prependDoc({ type: 'doc', content: [{ type: 'paragraph' }] }, rest)).toEqual(rest);
	});

	it('does not carry a bare line break onto the next page', () => {
		const blank: JSONContent = { type: 'doc', content: [{ type: 'paragraph' }] };
		const existing: JSONContent = { type: 'doc', content: [paragraph('esistente')] };
		expect(isBlankRest(blank)).toBe(true);
		expect(isBlankRest(existing)).toBe(false);
		expect(prependDoc(existing, blank)).toEqual(existing);
	});
});

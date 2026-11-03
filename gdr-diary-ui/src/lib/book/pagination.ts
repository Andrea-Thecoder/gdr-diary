import type { Editor, JSONContent } from '@tiptap/core';
import type { Node as ProseNode } from '@tiptap/pm/model';
import { hasText } from './pages.js';

export interface Overflow {
	rest: JSONContent;
	cursor: number | null;
}

interface Candidate {
	pos: number;
	node: ProseNode;
}

function firstOverflowing(editor: Editor, bottom: number): Candidate | null {
	let found: Candidate | null = null;
	editor.state.doc.descendants((node, pos) => {
		if (found !== null) {
			return false;
		}
		const isUnit = node.isTextblock || (node.isLeaf && node.isBlock);
		if (!isUnit) {
			return true;
		}
		const dom = editor.view.nodeDOM(pos);
		if (dom instanceof HTMLElement && dom.getBoundingClientRect().bottom > bottom + 1) {
			found = { pos, node };
		}
		return false;
	});
	return found;
}

function boundaryBefore(editor: Editor, pos: number): number {
	let current = pos;
	let resolved = editor.state.doc.resolve(current);
	while (resolved.parentOffset === 0 && resolved.depth > 0) {
		current = resolved.before();
		resolved = editor.state.doc.resolve(current);
	}
	return current;
}

function firstLineOutside(editor: Editor, start: number, end: number, bottom: number): number {
	let low = start;
	let high = end;
	while (low < high) {
		const middle = Math.floor((low + high) / 2);
		if (editor.view.coordsAtPos(middle).bottom > bottom + 1) {
			high = middle;
		} else {
			low = middle + 1;
		}
	}
	return low;
}

function splitPosition(editor: Editor, candidate: Candidate, bottom: number): number {
	const dom = editor.view.nodeDOM(candidate.pos);
	const top = dom instanceof HTMLElement ? dom.getBoundingClientRect().top : bottom;
	if (candidate.node.isTextblock && top < bottom - 1) {
		const start = candidate.pos + 1;
		const end = candidate.pos + candidate.node.nodeSize - 1;
		const inside = firstLineOutside(editor, start, end, bottom);
		if (inside > start) {
			return inside;
		}
	}
	return boundaryBefore(editor, candidate.pos);
}

function scaleOf(editor: Editor): number {
	const element = editor.view.dom;
	return element.offsetWidth > 0 ? element.getBoundingClientRect().width / element.offsetWidth : 1;
}

function limitOf(editor: Editor, limit: number): number {
	return editor.view.dom.getBoundingClientRect().top + limit * scaleOf(editor);
}

export function isFull(editor: Editor, limit: number, line: number): boolean {
	const last = editor.view.dom.lastElementChild;
	if (!(last instanceof HTMLElement)) {
		return false;
	}
	return last.getBoundingClientRect().bottom + line * scaleOf(editor) > limitOf(editor, limit) + 1;
}

export function overflows(editor: Editor, limit: number): boolean {
	return firstOverflowing(editor, limitOf(editor, limit)) !== null;
}

export function splitOverflow(editor: Editor, limit: number): Overflow | null {
	const bottom = limitOf(editor, limit);
	const candidate = firstOverflowing(editor, bottom);
	if (candidate === null) {
		return null;
	}
	let position: number;
	try {
		position = splitPosition(editor, candidate, bottom);
	} catch {
		return null;
	}
	const { doc, selection } = editor.state;
	if (position <= 0 || position >= doc.content.size) {
		return null;
	}
	const rest = doc.cut(position).toJSON() as JSONContent;
	if ((rest.content ?? []).length === 0) {
		return null;
	}
	const cursor =
		selection.head >= position ? selection.head - position + doc.resolve(position).depth : null;
	const transaction = editor.state.tr.delete(position, doc.content.size);
	transaction.setMeta('addToHistory', false);
	editor.view.dispatch(transaction);
	return { rest, cursor };
}

export function isBlankDoc(doc: JSONContent): boolean {
	const blocks = doc.content ?? [];
	return (
		blocks.length === 0 ||
		(blocks.length === 1 && blocks[0]?.type === 'paragraph' && !hasText(blocks[0]))
	);
}

export function isBlankRest(rest: JSONContent): boolean {
	return (rest.content ?? []).every((block) => block.type === 'paragraph' && !hasText(block));
}

export function prependDoc(existing: JSONContent, rest: JSONContent): JSONContent {
	if (isBlankRest(rest)) {
		return (existing.content ?? []).length > 0 ? existing : rest;
	}
	if (isBlankDoc(existing)) {
		return rest;
	}
	return { type: 'doc', content: [...(rest.content ?? []), ...(existing.content ?? [])] };
}

export function prependToEditor(editor: Editor, rest: JSONContent, cursor: number | null): void {
	if (!editor.isEditable) {
		editor.setEditable(true, false);
	}
	const merged = prependDoc(editor.getJSON(), rest);
	const target = isBlankRest(rest) ? 1 : cursor;
	editor.commands.setContent(merged, { emitUpdate: true });
	if (target !== null) {
		editor.commands.setTextSelection(
			Math.min(Math.max(target, 1), editor.state.doc.content.size - 1)
		);
	}
	editor.commands.focus();
}

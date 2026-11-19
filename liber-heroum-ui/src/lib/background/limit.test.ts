import { getSchema } from '@tiptap/core';
import { Node as ProseNode } from '@tiptap/pm/model';
import { describe, expect, it } from 'vitest';
import { diaryExtensions } from '#lib/book/extensions.js';
import { doc } from '#lib/book/test-support.js';
import { docTextLength, exceedsLimit } from './limit.js';

const schema = getSchema(diaryExtensions());

function node(text: string): ProseNode {
	return ProseNode.fromJSON(schema, doc(text));
}

describe('docTextLength', () => {
	it('counts the letters of the text', () => {
		expect(docTextLength(node('Nata tra i salici.'))).toBe(18);
	});

	it('counts an emoji as one character, like the server', () => {
		expect(docTextLength(node('ok 🐉'))).toBe(4);
	});
});

describe('exceedsLimit', () => {
	it.each([
		[90, 100, 100, false],
		[100, 101, 100, true],
		[120, 110, 100, false],
		[120, 120, 100, false]
	])('from %i to %i with a limit of %i is refused: %s', (before, after, max, refused) => {
		expect(exceedsLimit(before, after, max)).toBe(refused);
	});
});

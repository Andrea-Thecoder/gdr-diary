import { describe, expect, it } from 'vitest';
import { doc } from '#lib/book/test-support.js';
import { emptyDoc } from '#lib/book/pages.js';
import { fillLabel, fillPercent, formatCount, isNearlyFull, textLength } from './text.js';

describe('textLength', () => {
	it('is zero for an empty page', () => {
		expect(textLength(emptyDoc())).toBe(0);
	});

	it('is zero when there is no document', () => {
		expect(textLength(undefined)).toBe(0);
	});

	it('counts the characters of the text, spaces included', () => {
		expect(textLength(doc('Nato a Erebor'))).toBe(13);
	});

	it('counts a character outside the basic plane once', () => {
		expect(textLength(doc('a\u{1F409}b'))).toBe(3);
	});

	it('adds up the text of every paragraph', () => {
		const content = {
			type: 'doc',
			content: [
				{ type: 'paragraph', content: [{ type: 'text', text: 'uno' }] },
				{ type: 'paragraph', content: [{ type: 'text', text: 'due' }] }
			]
		};
		expect(textLength(content)).toBe(6);
	});

	it('adds up the runs of text inside a list', () => {
		const content = {
			type: 'doc',
			content: [
				{
					type: 'bulletList',
					content: [
						{
							type: 'listItem',
							content: [
								{
									type: 'paragraph',
									content: [
										{ type: 'text', text: 'ab', marks: [{ type: 'bold' }] },
										{ type: 'text', text: 'cd' }
									]
								}
							]
						}
					]
				}
			]
		};
		expect(textLength(content)).toBe(4);
	});

	it('does not count hard breaks', () => {
		const content = {
			type: 'doc',
			content: [
				{
					type: 'paragraph',
					content: [{ type: 'text', text: 'a' }, { type: 'hardBreak' }, { type: 'text', text: 'b' }]
				}
			]
		};
		expect(textLength(content)).toBe(2);
	});
});

describe('formatCount', () => {
	it.each([
		[0, '0'],
		[999, '999'],
		[1000, '1.000'],
		[3412, '3.412'],
		[50000, '50.000'],
		[1234567, '1.234.567']
	])('writes %i as %s', (value, written) => {
		expect(formatCount(value)).toBe(written);
	});
});

describe('fillPercent', () => {
	it.each([
		[0, 0],
		[5000, 10],
		[50000, 100],
		[60000, 100],
		[-3, 0]
	])('is %i characters out of 50000 -> %i percent', (count, percent) => {
		expect(fillPercent(count, 50000)).toBe(percent);
	});
});

describe('isNearlyFull', () => {
	it.each([
		[44999, false],
		[45000, true],
		[50000, true]
	])('says %i out of 50000 is nearly full: %s', (count, nearly) => {
		expect(isNearlyFull(count, 50000)).toBe(nearly);
	});
});

describe('fillLabel', () => {
	it.each([
		[0, 'Il rotolo è ancora bianco'],
		[1, 'Il rotolo è pieno al 1%'],
		[3412, 'Il rotolo è pieno al 7%'],
		[25000, 'Il rotolo è pieno al 50%'],
		[49999, 'Il rotolo è pieno al 100%'],
		[50000, 'Il rotolo è pieno']
	])('describes %i characters as "%s"', (count, label) => {
		expect(fillLabel(count, 50000)).toBe(label);
	});
});

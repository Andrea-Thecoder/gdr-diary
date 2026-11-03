import { describe, expect, it } from 'vitest';
import { BODY_HEIGHT, bookScale, fontPixels, lineHeightFor, linesPerPage } from './layout.js';

describe('book layout', () => {
	it('fits a whole number of lines in the page body for every spacing', () => {
		expect(lineHeightFor('normal')).toBe(36);
		expect(linesPerPage('compact')).toBe(18);
		expect(linesPerPage('normal')).toBe(15);
		expect(linesPerPage('wide')).toBe(12);
	});

	it.each(['compact', 'normal', 'wide'] as const)('fits whole lines with %s spacing', (spacing) => {
		expect(BODY_HEIGHT % lineHeightFor(spacing)).toBe(0);
	});

	it('converts points to pixels with a bigger factor for the handwritten font', () => {
		expect(fontPixels('Georgia', 12)).toBe(16);
		expect(fontPixels('EB Garamond', 18)).toBe(24);
		expect(fontPixels('Caveat', 16)).toBeGreaterThan(fontPixels('Georgia', 16));
	});

	it('never scales the pages up and keeps a minimum', () => {
		expect(bookScale(5000, false)).toBe(1);
		expect(bookScale(474, true)).toBe(1);
		expect(bookScale(237, true)).toBe(0.5);
		expect(bookScale(100, true)).toBe(0.5);
		expect(bookScale(0, false)).toBe(1);
		expect(bookScale(474, false)).toBe(0.5);
	});
});

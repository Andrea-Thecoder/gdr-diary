import { describe, expect, it } from 'vitest';
import {
	clampPage,
	counterLabel,
	hasNext,
	hasPrevious,
	leftPageOf,
	nextFocus,
	parseSpread,
	spreadOf,
	thickness,
	visiblePages
} from './spread.js';

describe('spread maths', () => {
	it('maps pages to spreads with an odd page on the left', () => {
		expect([1, 2, 3, 4, 7, 8, 99, 100].map(spreadOf)).toEqual([1, 1, 2, 2, 4, 4, 50, 50]);
		expect([1, 2, 4, 50].map(leftPageOf)).toEqual([1, 3, 7, 99]);
	});

	it('lists the visible pages for both layouts', () => {
		expect(visiblePages({ single: false, focus: 8 })).toEqual([7, 8]);
		expect(visiblePages({ single: false, focus: 7 })).toEqual([7, 8]);
		expect(visiblePages({ single: true, focus: 8 })).toEqual([8]);
	});

	it('parses the spread from the address', () => {
		expect(parseSpread(null)).toBe(1);
		expect(parseSpread('abc')).toBe(1);
		expect(parseSpread('0')).toBe(1);
		expect(parseSpread('4')).toBe(4);
		expect(parseSpread('999')).toBe(50);
	});

	it('clamps a page between the first and the last', () => {
		expect(clampPage(0, 23)).toBe(1);
		expect(clampPage(40, 23)).toBe(23);
		expect(clampPage(5.7, 23)).toBe(5);
		expect(clampPage(3, 0)).toBe(1);
	});

	it('has no previous view on the first spread', () => {
		expect(hasPrevious({ single: false, focus: 1 })).toBe(false);
		expect(hasPrevious({ single: false, focus: 4 })).toBe(true);
		expect(hasPrevious({ single: true, focus: 1 })).toBe(false);
		expect(hasPrevious({ single: true, focus: 2 })).toBe(true);
	});

	it('has no next view after the last page', () => {
		expect(hasNext({ single: false, focus: 3 }, 4)).toBe(false);
		expect(hasNext({ single: false, focus: 1 }, 4)).toBe(true);
		expect(hasNext({ single: false, focus: 1 }, 0)).toBe(false);
		expect(hasNext({ single: false, focus: 99 }, 100)).toBe(false);
	});

	it('stops the single page layout at the last page', () => {
		expect(hasNext({ single: true, focus: 23 }, 23)).toBe(false);
		expect(hasNext({ single: true, focus: 22 }, 23)).toBe(true);
		expect(hasNext({ single: true, focus: 100 }, 100)).toBe(false);
		expect(hasNext({ single: true, focus: 99 }, 100)).toBe(true);
	});

	it('moves by a spread or by a page', () => {
		expect(nextFocus({ single: false, focus: 8 }, 'forward')).toBe(9);
		expect(nextFocus({ single: false, focus: 8 }, 'backward')).toBe(5);
		expect(nextFocus({ single: true, focus: 8 }, 'forward')).toBe(9);
		expect(nextFocus({ single: true, focus: 8 }, 'backward')).toBe(7);
	});

	it('words the counter for every state', () => {
		expect(counterLabel({ single: false, focus: 1 }, 0)).toBe('Nessuna pagina ancora');
		expect(counterLabel({ single: false, focus: 7 }, 23)).toBe('Pagine 7 e 8 di 23');
		expect(counterLabel({ single: false, focus: 23 }, 23)).toBe('Pagina 23 di 23');
		expect(counterLabel({ single: true, focus: 7 }, 23)).toBe('Pagina 7 di 23');
		expect(counterLabel({ single: true, focus: 24 }, 23)).toBe('Nuova pagina');
		expect(counterLabel({ single: false, focus: 25 }, 23)).toBe('Nuova pagina');
	});

	it('grows the book edges in proportion to the written pages', () => {
		expect(thickness(1, 0)).toEqual({ before: 0, after: 0 });
		const early = thickness(1, 100);
		const late = thickness(99, 100);
		expect(early.before).toBe(0);
		expect(early.after).toBe(6);
		expect(late.before).toBe(6);
		expect(late.after).toBeLessThanOrEqual(1);
	});
});

import { describe, expect, it } from 'vitest';
import { openingSpread } from './opening.js';

describe('openingSpread', () => {
	it('honors the spread asked for in the address', () => {
		const result = openingSpread('2', { pageId: 'p9', pageNumber: 9 }, 12);

		expect(result).toBe(2);
	});

	it('does not go past the last spread when the address is too far', () => {
		const result = openingSpread('40', null, 7);

		expect(result).toBe(4);
	});

	it('opens on the spread of the bookmark when the address does not say', () => {
		const result = openingSpread(null, { pageId: 'p9', pageNumber: 9 }, 12);

		expect(result).toBe(5);
	});

	it('prefers the bookmark over the last page', () => {
		const result = openingSpread(null, { pageId: 'p2', pageNumber: 2 }, 12);

		expect(result).toBe(1);
	});

	it('opens on the spread of the last page without a bookmark', () => {
		const result = openingSpread(null, null, 7);

		expect(result).toBe(4);
	});

	it('opens on the first spread of an empty diary', () => {
		const result = openingSpread(null, null, 0);

		expect(result).toBe(1);
	});

	it('does not go past the last spread when the bookmark is stale', () => {
		const result = openingSpread(null, { pageId: 'p30', pageNumber: 30 }, 6);

		expect(result).toBe(3);
	});
});

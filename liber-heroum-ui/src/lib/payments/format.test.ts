import { describe, expect, it } from 'vitest';
import { formatAmount, statusLabel } from './format.js';

describe('payment format', () => {
	it.each([
		['PENDING', 'In attesa'],
		['SUCCEEDED', 'Pagato'],
		['FAILED', 'Non riuscito'],
		['REFUNDED', 'Rimborsato']
	] as const)('calls the status %s "%s"', (status, label) => {
		expect(statusLabel(status)).toBe(label);
	});

	it('writes the amount in euro the Italian way', () => {
		expect(formatAmount(1999, 'EUR').replace(/\s/g, ' ')).toBe('19,99 €');
	});

	it('writes nothing when the amount is missing', () => {
		expect(formatAmount(undefined, 'EUR')).toBe('');
	});
});

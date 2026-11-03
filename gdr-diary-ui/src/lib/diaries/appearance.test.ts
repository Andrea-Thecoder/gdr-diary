import { describe, expect, it } from 'vitest';
import { coverTints, mixHex, titleSize } from '#lib/diaries/appearance.js';

describe('mixHex', () => {
	it('lightens towards white', () => {
		expect(mixHex('#000000', 'white', 0.5)).toBe('#808080');
	});

	it('darkens towards black', () => {
		expect(mixHex('#FFFFFF', 'black', 0.5)).toBe('#808080');
	});

	it('keeps the color with a zero amount', () => {
		expect(mixHex('#3B2A6B', 'white', 0)).toBe('#3b2a6b');
	});
});

describe('coverTints', () => {
	it('gives a lighter and a darker tint of the color', () => {
		expect(coverTints('#7C2A22')).toEqual({ light: '#8e4841', dark: '#571d18' });
	});

	it('falls back to the default color', () => {
		expect(coverTints(null)).toEqual(coverTints('#3B2A6B'));
		expect(coverTints('rosso')).toEqual(coverTints('#3B2A6B'));
	});
});

describe('titleSize', () => {
	it('shrinks the title as it gets longer', () => {
		expect(titleSize('Cronache del Nord')).toBe(25);
		expect(titleSize('Cronache del Nord e delle Terre Spezzate')).toBe(19);
		expect(
			titleSize('Le lunghissime cronache del Nord e di tutte le Terre Spezzate dell’Ovest')
		).toBe(14);
	});

	it('ignores surrounding spaces', () => {
		expect(titleSize(`   ${'a'.repeat(22)}   `)).toBe(25);
	});
});

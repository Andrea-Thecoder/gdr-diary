import { describe, expect, it } from 'vitest';
import { COLOR_PRESETS } from '#lib/characters/appearance.js';
import { sheetColorFor } from '#lib/sheets/appearance.js';

describe('sheetColorFor', () => {
	it('is stable for the same id', () => {
		const id = '0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10';
		expect(sheetColorFor(id)).toBe(sheetColorFor(id));
	});

	it('always returns one of the preset colours, even for an empty id', () => {
		const hexes = COLOR_PRESETS.map((preset) => preset.hex);
		for (const id of ['', 'a', 'sheet-1', 'sheet-2', 'x'.repeat(40)]) {
			expect(hexes).toContain(sheetColorFor(id));
		}
	});

	it('does not give every sheet the same colour', () => {
		const colours = new Set(['a', 'b', 'c', 'd', 'e', 'f'].map((id) => sheetColorFor(id)));
		expect(colours.size).toBeGreaterThan(1);
	});

	it('uses every preset over a good number of random ids', () => {
		const ids = Array.from({ length: 200 }, () => crypto.randomUUID());
		const colours = new Set(ids.map((id) => sheetColorFor(id)));

		expect(colours.size).toBe(COLOR_PRESETS.length);
	});
});

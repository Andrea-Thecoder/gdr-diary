import { describe, expect, it } from 'vitest';
import { characterTabs } from '#lib/characters/tabs.js';

describe('characterTabs', () => {
	const tabs = characterTabs('id-alira');

	it('lists the three tabs in order', () => {
		expect(tabs.map((tab) => tab.id)).toEqual(['diaries', 'sheets', 'other']);
		expect(tabs.map((tab) => tab.label)).toEqual(['Diari', 'Schede', 'Altro']);
	});

	it('points every tab to its own page of the character', () => {
		expect(tabs.map((tab) => tab.href)).toEqual([
			'/characters/id-alira',
			'/characters/id-alira/sheets',
			'/characters/id-alira/other'
		]);
	});

	it('spells the long name of the sheets tab as a suffix that can be hidden', () => {
		expect(tabs[1]?.suffix).toBe(' del personaggio');
	});

	it('words the count of diaries and sheets, in singular and plural', () => {
		expect(tabs[0]?.countLabel?.(1)).toBe('1 diario');
		expect(tabs[0]?.countLabel?.(3)).toBe('3 diari');
		expect(tabs[1]?.countLabel?.(1)).toBe('1 scheda');
		expect(tabs[1]?.countLabel?.(0)).toBe('0 schede');
	});

	it('has no count on the other tab', () => {
		expect(tabs[2]?.countLabel).toBeUndefined();
	});
});

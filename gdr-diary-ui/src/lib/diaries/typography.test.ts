import { describe, expect, it } from 'vitest';
import { fontOptions, sizeOptions } from '#lib/diaries/typography.js';

describe('fontOptions', () => {
	it('starts with the settings choice', () => {
		expect(fontOptions('')[0]).toEqual({ value: '', label: 'Come le impostazioni' });
	});

	it('adds a font that is not in the list', () => {
		expect(fontOptions('Lora').at(-1)).toEqual({ value: 'Lora', label: 'Lora' });
	});

	it('does not repeat a known font', () => {
		expect(fontOptions('Caveat').filter((option) => option.value === 'Caveat')).toHaveLength(1);
	});
});

describe('sizeOptions', () => {
	it('lists sizes in points after the settings choice', () => {
		expect(sizeOptions('').map((option) => option.label)).toEqual([
			'Come le impostazioni',
			'12 pt',
			'14 pt',
			'16 pt',
			'18 pt',
			'20 pt',
			'24 pt'
		]);
	});

	it('adds a size that is not in the list', () => {
		expect(sizeOptions('17').at(-1)).toEqual({ value: '17', label: '17 pt' });
	});
});

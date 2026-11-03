import { describe, expect, it } from 'vitest';
import {
	changedPreferences,
	DEFAULT_PREFERENCES,
	preferencesFromSettings
} from '#lib/settings/preferences.js';

describe('preferencesFromSettings', () => {
	it('uses the defaults when nothing is stored', () => {
		expect(preferencesFromSettings(null)).toEqual(DEFAULT_PREFERENCES);
		expect(preferencesFromSettings({})).toEqual(DEFAULT_PREFERENCES);
	});

	it('reads the stored values', () => {
		expect(
			preferencesFromSettings({
				font: 'Lora',
				fontSize: 20,
				lineSpacing: 'wide',
				autoSave: false,
				pageTurn: false,
				reduceMotion: true,
				theme: 'viola'
			})
		).toEqual({
			font: 'Lora',
			fontSize: 20,
			lineSpacing: 'wide',
			autoSave: false,
			pageTurn: false,
			reduceMotion: true
		});
	});

	it('ignores values of the wrong kind or out of range', () => {
		expect(
			preferencesFromSettings({
				font: ' ',
				fontSize: 99,
				lineSpacing: 'huge',
				autoSave: 'yes',
				pageTurn: 1,
				reduceMotion: null
			})
		).toEqual(DEFAULT_PREFERENCES);
		expect(preferencesFromSettings({ fontSize: 16.5 }).fontSize).toBe(16);
	});
});

describe('changedPreferences', () => {
	it('lists only the keys that differ', () => {
		const draft = { ...DEFAULT_PREFERENCES, fontSize: 18, reduceMotion: true };

		expect(changedPreferences(DEFAULT_PREFERENCES, draft)).toEqual({
			fontSize: 18,
			reduceMotion: true
		});
	});

	it('is empty when nothing changed', () => {
		expect(changedPreferences(DEFAULT_PREFERENCES, { ...DEFAULT_PREFERENCES })).toEqual({});
	});
});

import { describe, expect, it } from 'vitest';
import { themeFromSettings } from '#lib/settings/settings.js';

describe('themeFromSettings', () => {
	it('reads a valid theme', () => {
		expect(themeFromSettings({ theme: 'viola' })).toBe('viola');
		expect(themeFromSettings({ theme: 'pergamena' })).toBe('pergamena');
		expect(themeFromSettings({ theme: 'system' })).toBe('system');
	});

	it('ignores missing or unknown values', () => {
		expect(themeFromSettings(undefined)).toBeNull();
		expect(themeFromSettings(null)).toBeNull();
		expect(themeFromSettings({})).toBeNull();
		expect(themeFromSettings({ theme: 'rosa' })).toBeNull();
		expect(themeFromSettings({ theme: 3 })).toBeNull();
	});
});

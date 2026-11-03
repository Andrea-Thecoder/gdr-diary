import { describe, expect, it } from 'vitest';
import {
	buildThemeCookie,
	isThemeName,
	nextPreference,
	parseThemeCookie,
	resolveTheme
} from '#lib/theme/theme.js';

describe('isThemeName', () => {
	it('accepts only the two known themes', () => {
		expect(isThemeName('pergamena')).toBe(true);
		expect(isThemeName('viola')).toBe(true);
		expect(isThemeName('system')).toBe(false);
		expect(isThemeName('dark')).toBe(false);
		expect(isThemeName(null)).toBe(false);
	});
});

describe('resolveTheme', () => {
	it('follows the system when the preference is system', () => {
		expect(resolveTheme('system', true)).toBe('viola');
		expect(resolveTheme('system', false)).toBe('pergamena');
	});

	it('ignores the system when a theme is chosen', () => {
		expect(resolveTheme('pergamena', true)).toBe('pergamena');
		expect(resolveTheme('viola', false)).toBe('viola');
	});
});

describe('nextPreference', () => {
	it('cycles through system, light and dark', () => {
		expect(nextPreference('system')).toBe('pergamena');
		expect(nextPreference('pergamena')).toBe('viola');
		expect(nextPreference('viola')).toBe('system');
	});
});

describe('buildThemeCookie', () => {
	it('saves a chosen theme for a year, readable from the whole site', () => {
		expect(buildThemeCookie('viola', { secure: false })).toBe(
			'gdr-theme=viola; Path=/; SameSite=Lax; Max-Age=31536000'
		);
	});

	it('deletes the cookie when the preference is system', () => {
		expect(buildThemeCookie('system', { secure: false })).toBe(
			'gdr-theme=; Path=/; SameSite=Lax; Max-Age=0'
		);
	});

	it('adds the domain and Secure only when asked', () => {
		expect(buildThemeCookie('pergamena', { domain: 'example.com', secure: true })).toBe(
			'gdr-theme=pergamena; Path=/; SameSite=Lax; Max-Age=31536000; Domain=example.com; Secure'
		);
	});
});

describe('parseThemeCookie', () => {
	it('reads the chosen theme among other cookies', () => {
		expect(parseThemeCookie('a=1; gdr-theme=viola; b=2')).toBe('viola');
		expect(parseThemeCookie('gdr-theme=pergamena')).toBe('pergamena');
	});

	it('means system when the cookie is missing or invalid', () => {
		expect(parseThemeCookie('')).toBe('system');
		expect(parseThemeCookie('a=1')).toBe('system');
		expect(parseThemeCookie('gdr-theme=')).toBe('system');
		expect(parseThemeCookie('gdr-theme=rosa')).toBe('system');
	});

	it('does not confuse a cookie that only ends with the same name', () => {
		expect(parseThemeCookie('other-gdr-theme=viola')).toBe('system');
	});
});

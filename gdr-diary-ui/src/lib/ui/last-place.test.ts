import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('$app/paths', () => ({
	resolve: (path: string) => path.replace('/(app)', '')
}));

import { isAccountPage, lastPlace, placeLabel } from './last-place.svelte';

function memoryStorage(initial: Record<string, string> = {}): void {
	const data = new Map(Object.entries(initial));
	vi.stubGlobal('sessionStorage', {
		getItem: (key: string) => data.get(key) ?? null,
		setItem: (key: string, value: string) => {
			data.set(key, value);
		}
	});
}

describe('lastPlace', () => {
	beforeEach(() => {
		memoryStorage();
		lastPlace.record(new URL('http://app.test/characters'));
	});

	it('leads back to the page the user was on', () => {
		lastPlace.record(new URL('http://app.test/diaries/d1?page=4'));

		expect(lastPlace.href).toBe('/diaries/d1?page=4');
		expect(lastPlace.label).toBe('Torna al diario');
	});

	it('does not count the profile and the settings as a place to go back to', () => {
		lastPlace.record(new URL('http://app.test/characters/c1'));
		lastPlace.record(new URL('http://app.test/profile'));
		lastPlace.record(new URL('http://app.test/settings'));

		expect(lastPlace.href).toBe('/characters/c1');
		expect(lastPlace.label).toBe('Torna al personaggio');
	});

	it('remembers the place after a reload of the profile', () => {
		memoryStorage({ 'gdr.last-place': '/diaries/d9' });
		vi.resetModules();

		return import('./last-place.svelte').then(({ lastPlace: fresh }) => {
			expect(fresh.href).toBe('/diaries/d9');
		});
	});

	it('falls back to the home when nothing is known or the stored place is not local', () => {
		memoryStorage({ 'gdr.last-place': 'https://evil.test/' });
		vi.resetModules();

		return import('./last-place.svelte').then(({ lastPlace: fresh }) => {
			expect(fresh.href).toBe('/');
			expect(fresh.label).toBe('Tutti i personaggi');
		});
	});

	it('works when the storage is not available', () => {
		vi.stubGlobal('sessionStorage', undefined);

		lastPlace.record(new URL('http://app.test/characters'));

		expect(lastPlace.href).toBe('/characters');
	});
});

describe('isAccountPage and placeLabel', () => {
	it('knows the account pages', () => {
		expect(isAccountPage('/profile')).toBe(true);
		expect(isAccountPage('/settings')).toBe(true);
		expect(isAccountPage('/characters')).toBe(false);
	});

	it('names the places', () => {
		expect(placeLabel('/characters')).toBe('Tutti i personaggi');
		expect(placeLabel('/characters/c1?x=1')).toBe('Torna al personaggio');
		expect(placeLabel('/diaries/d1')).toBe('Torna al diario');
	});
});

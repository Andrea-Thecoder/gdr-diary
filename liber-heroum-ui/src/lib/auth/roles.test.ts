import { describe, expect, it } from 'vitest';
import { decideAccess, extractRoles } from '#lib/auth/roles.js';

describe('extractRoles', () => {
	it('keeps only the known roles, ignoring case', () => {
		expect(extractRoles(['User', 'admin', 'game_master', 'offline_access'])).toEqual([
			'user',
			'admin'
		]);
	});

	it('returns an empty list when the token has no roles', () => {
		expect(extractRoles(undefined)).toEqual([]);
	});
});

describe('decideAccess', () => {
	it('waits while Keycloak is initialising', () => {
		expect(decideAccess('loading', [])).toBe('wait');
	});

	it('asks anonymous and failed sessions to log in', () => {
		expect(decideAccess('anonymous', [])).toBe('login');
		expect(decideAccess('error', ['user'])).toBe('login');
	});

	it('forbids authenticated users without a role', () => {
		expect(decideAccess('authenticated', [])).toBe('forbidden');
	});

	it('allows authenticated users with a role', () => {
		expect(decideAccess('authenticated', ['admin'])).toBe('allow');
	});
});

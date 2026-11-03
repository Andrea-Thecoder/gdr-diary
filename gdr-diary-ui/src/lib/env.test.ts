import { describe, expect, it } from 'vitest';
import { parseEnv, parsePrivacyContact, parseThemeCookieDomain } from '#lib/env.js';

const valid = {
	VITE_KEYCLOAK_URL: 'http://localhost:8180',
	VITE_KEYCLOAK_REALM: 'gdr-diary-dev',
	VITE_KEYCLOAK_CLIENT_ID: 'gdr-diary-api',
	VITE_API_BASE_URL: 'http://localhost:8080/api/v1/gdr-diary-api'
};

describe('parseEnv', () => {
	it('accepts a complete configuration', () => {
		expect(parseEnv(valid).VITE_KEYCLOAK_REALM).toBe('gdr-diary-dev');
	});

	it('rejects a missing variable with a readable message', () => {
		const incomplete: Record<string, string> = { ...valid };
		delete incomplete['VITE_API_BASE_URL'];
		expect(() => parseEnv(incomplete)).toThrow(/VITE_API_BASE_URL/);
	});

	it('rejects an invalid url', () => {
		expect(() => parseEnv({ ...valid, VITE_KEYCLOAK_URL: 'not-a-url' })).toThrow(
			/VITE_KEYCLOAK_URL/
		);
	});
});

describe('parseThemeCookieDomain', () => {
	it('returns the domain when it is set', () => {
		expect(parseThemeCookieDomain('example.com')).toBe('example.com');
		expect(parseThemeCookieDomain('  .example.com ')).toBe('.example.com');
	});

	it('returns nothing when it is missing or empty', () => {
		expect(parseThemeCookieDomain(undefined)).toBeUndefined();
		expect(parseThemeCookieDomain('')).toBeUndefined();
		expect(parseThemeCookieDomain('   ')).toBeUndefined();
	});
});

describe('parsePrivacyContact', () => {
	it('returns the controller name and email when both are set', () => {
		expect(
			parsePrivacyContact({
				VITE_PRIVACY_CONTROLLER_NAME: ' Andrea ',
				VITE_PRIVACY_CONTACT_EMAIL: 'privacy@example.com'
			})
		).toEqual({ name: 'Andrea', email: 'privacy@example.com' });
	});

	it('returns nothing when one of them is missing, empty or not an email', () => {
		expect(parsePrivacyContact({})).toBeUndefined();
		expect(parsePrivacyContact({ VITE_PRIVACY_CONTROLLER_NAME: 'Andrea' })).toBeUndefined();
		expect(
			parsePrivacyContact({
				VITE_PRIVACY_CONTROLLER_NAME: '  ',
				VITE_PRIVACY_CONTACT_EMAIL: 'privacy@example.com'
			})
		).toBeUndefined();
		expect(
			parsePrivacyContact({
				VITE_PRIVACY_CONTROLLER_NAME: 'Andrea',
				VITE_PRIVACY_CONTACT_EMAIL: 'not-an-email'
			})
		).toBeUndefined();
	});
});

import { describe, expect, it } from 'vitest';
import { withoutAuthCallback } from './callback.js';

describe('withoutAuthCallback', () => {
	it('drops what Keycloak leaves in the address after a login', () => {
		const clean = withoutAuthCallback(
			'http://localhost:5173/characters#state=abc&session_state=def&iss=http%3A%2F%2Fkc&code=xyz'
		);

		expect(clean).toBe('http://localhost:5173/characters');
	});

	it('drops what Keycloak leaves after an account action', () => {
		const clean = withoutAuthCallback(
			'http://localhost:5173/profile#state=abc&kc_action_status=success&kc_action=UPDATE_EMAIL'
		);

		expect(clean).toBe('http://localhost:5173/profile');
	});

	it('keeps the query and the rest of the fragment', () => {
		const clean = withoutAuthCallback(
			'http://localhost:5173/diaries/d1?vista=2#state=abc&code=xyz&nota=3'
		);

		expect(clean).toBe('http://localhost:5173/diaries/d1?vista=2#nota=3');
	});

	it.each([
		'http://localhost:5173/characters',
		'http://localhost:5173/settings#aspetto',
		'http://localhost:5173/characters#code=xyz'
	])('leaves %s alone', (href) => {
		expect(withoutAuthCallback(href)).toBeNull();
	});
});

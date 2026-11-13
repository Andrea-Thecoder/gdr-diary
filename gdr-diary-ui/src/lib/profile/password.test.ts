import { beforeEach, describe, expect, it, vi } from 'vitest';

const { auth, fetcher } = vi.hoisted(() => ({
	auth: { getToken: vi.fn<() => Promise<string>>() },
	fetcher: vi.fn<(url: URL, init: RequestInit) => Promise<{ ok: boolean; status: number }>>()
}));

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('#lib/env.js', () => ({
	getEnv: () => ({
		VITE_KEYCLOAK_URL: 'http://kc.test/',
		VITE_KEYCLOAK_REALM: 'dev'
	})
}));

import { passwordReturnUrl, passwordWasChanged, requestPasswordChange } from './password';

describe('requestPasswordChange', () => {
	beforeEach(() => {
		auth.getToken.mockReset();
		auth.getToken.mockResolvedValue('tok');
		fetcher.mockReset();
		fetcher.mockResolvedValue({ ok: true, status: 200 });
		vi.stubGlobal('fetch', fetcher);
	});

	it('asks Keycloak to write to the user, with the token, and to come back to the profile', async () => {
		expect(await requestPasswordChange('http://app.test/profile')).toBe('sent');

		const [url, init] = fetcher.mock.calls[0] ?? [];
		expect(`${url?.origin ?? ''}${url?.pathname ?? ''}`).toBe(
			'http://kc.test/realms/dev/gdr-password/request'
		);
		expect(url?.searchParams.get('redirect_uri')).toBe('http://app.test/profile?password=cambiata');
		expect(init?.method).toBe('POST');
		expect(init?.headers).toEqual({ Authorization: 'Bearer tok' });
	});

	it('tells when the last request was less than a minute ago', async () => {
		fetcher.mockResolvedValue({ ok: false, status: 429 });

		expect(await requestPasswordChange('http://app.test/profile')).toBe('too-soon');
	});

	it('fails on any other answer, on a network error and without a session', async () => {
		fetcher.mockResolvedValue({ ok: false, status: 502 });
		expect(await requestPasswordChange('http://app.test/profile')).toBe('failed');

		fetcher.mockRejectedValue(new Error('offline'));
		expect(await requestPasswordChange('http://app.test/profile')).toBe('failed');

		auth.getToken.mockRejectedValue(new Error('Session expired'));
		expect(await requestPasswordChange('http://app.test/profile')).toBe('failed');
	});
});

describe('the mark left in the address when the password was changed', () => {
	it('is added to the return address and recognised', () => {
		expect(passwordWasChanged(passwordReturnUrl('http://app.test/profile'))).toBe(true);
		expect(passwordWasChanged('http://app.test/profile')).toBe(false);
		expect(passwordWasChanged('http://app.test/profile?password=altro')).toBe(false);
	});
});

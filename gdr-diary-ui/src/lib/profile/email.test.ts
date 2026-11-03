import { beforeEach, describe, expect, it, vi } from 'vitest';

const { auth, service } = vi.hoisted(() => ({
	auth: {
		user: { email: 'old@e.it' },
		changeEmail: vi.fn<(returnTo: string) => Promise<void>>(),
		takeActionResult:
			vi.fn<() => Promise<{ status: string; action: string | undefined } | undefined>>(),
		refreshUser: vi.fn<() => Promise<void>>(),
		getToken: vi.fn<() => Promise<string>>()
	},
	service: { sync: vi.fn<() => Promise<string>>() }
}));

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('#lib/env.js', () => ({
	getEnv: () => ({ VITE_KEYCLOAK_URL: 'http://kc.test/', VITE_KEYCLOAK_REALM: 'dev' })
}));
vi.mock('#lib/api/me.js', () => ({ syncAppUser: service.sync }));

import { finishEmailChange, resendEmailChange, startEmailChange } from '#lib/profile/email.js';

describe('startEmailChange', () => {
	it('leaves the typing of the new address to Keycloak', async () => {
		auth.changeEmail.mockResolvedValue();

		await startEmailChange('http://app.test/profile');

		expect(auth.changeEmail).toHaveBeenCalledWith('http://app.test/profile');
	});
});

describe('finishEmailChange', () => {
	beforeEach(() => {
		auth.user = { email: 'old@e.it' };
		auth.takeActionResult.mockReset();
		auth.takeActionResult.mockResolvedValue({ status: 'success', action: 'UPDATE_EMAIL' });
		auth.refreshUser.mockReset();
		auth.refreshUser.mockResolvedValue();
		service.sync.mockReset();
		service.sync.mockResolvedValue('id');
	});

	it('does nothing when the user did not come back from the email page', async () => {
		auth.takeActionResult.mockResolvedValue(undefined);
		expect(await finishEmailChange('old@e.it')).toEqual({ status: 'none' });

		auth.takeActionResult.mockResolvedValue({ status: 'success', action: 'UPDATE_PASSWORD' });
		expect(await finishEmailChange('old@e.it')).toEqual({ status: 'none' });
		expect(auth.refreshUser).not.toHaveBeenCalled();
	});

	it('does nothing when the user gave up on the email page', async () => {
		auth.takeActionResult.mockResolvedValue({ status: 'cancelled', action: 'UPDATE_EMAIL' });

		expect(await finishEmailChange('old@e.it')).toEqual({ status: 'none' });
	});

	it('reports a failure of the email page', async () => {
		auth.takeActionResult.mockResolvedValue({ status: 'error', action: 'UPDATE_EMAIL' });

		const outcome = await finishEmailChange('old@e.it');

		expect(outcome.status).toBe('failed');
		expect(service.sync).not.toHaveBeenCalled();
	});

	it('is pending while Keycloak waits for the link sent to the new address', async () => {
		expect(await finishEmailChange('old@e.it')).toEqual({ status: 'pending' });
		expect(service.sync).not.toHaveBeenCalled();
	});

	it('updates the service when the new address is already in the token', async () => {
		auth.refreshUser.mockImplementation(() => {
			auth.user = { email: 'new@e.it' };
			return Promise.resolve();
		});

		expect(await finishEmailChange('old@e.it')).toEqual({ status: 'changed', email: 'new@e.it' });
		expect(service.sync).toHaveBeenCalledOnce();
	});

	it('says so when the session cannot be renewed', async () => {
		auth.refreshUser.mockRejectedValue(new Error('x'));

		expect(await finishEmailChange('old@e.it')).toMatchObject({ status: 'failed' });
	});

	it('says so when the service does not follow, without losing the change', async () => {
		auth.refreshUser.mockImplementation(() => {
			auth.user = { email: 'new@e.it' };
			return Promise.resolve();
		});
		service.sync.mockRejectedValue(new Error('down'));

		const outcome = await finishEmailChange('old@e.it');

		expect(outcome).toMatchObject({ status: 'failed' });
		expect('message' in outcome && outcome.message).toContain('prossimo accesso');
	});
});

describe('resendEmailChange', () => {
	const fetcher = vi.fn<(url: URL, init: RequestInit) => Promise<Response>>();

	beforeEach(() => {
		fetcher.mockReset();
		vi.stubGlobal('fetch', fetcher);
		auth.getToken.mockReset();
		auth.getToken.mockResolvedValue('tok');
		auth.refreshUser.mockReset();
		auth.refreshUser.mockResolvedValue();
	});

	it('asks Keycloak with the access token, saying where the link has to lead back', async () => {
		fetcher.mockResolvedValue(new Response('{}', { status: 200 }));

		expect(await resendEmailChange('http://app.test/profile')).toBe('sent');
		expect(fetcher).toHaveBeenCalledWith(expect.anything(), {
			method: 'POST',
			headers: { Authorization: 'Bearer tok' }
		});
		const url = new URL(String(fetcher.mock.calls[0]?.[0]));
		expect(url.origin + url.pathname).toBe('http://kc.test/realms/dev/gdr-email/resend');
		expect(url.searchParams.get('redirect_uri')).toBe('http://app.test/profile');
	});

	it('tells that the last mail was sent less than a minute ago', async () => {
		fetcher.mockResolvedValue(new Response('{}', { status: 429 }));

		expect(await resendEmailChange('http://app.test/profile')).toBe('too-soon');
	});

	it('refreshes the session when no change is waiting any more', async () => {
		fetcher.mockResolvedValue(new Response('{}', { status: 409 }));

		expect(await resendEmailChange('http://app.test/profile')).toBe('nothing-pending');
		expect(auth.refreshUser).toHaveBeenCalledOnce();
	});

	it('fails on any other answer and when the request cannot be made', async () => {
		fetcher.mockResolvedValue(new Response('{}', { status: 502 }));
		expect(await resendEmailChange('http://app.test/profile')).toBe('failed');

		fetcher.mockRejectedValue(new TypeError('network'));
		expect(await resendEmailChange('http://app.test/profile')).toBe('failed');
	});
});

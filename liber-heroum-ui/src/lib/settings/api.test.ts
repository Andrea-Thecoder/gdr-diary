import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from '#lib/api/client.js';
import { fetchAccount, saveSettings } from '#lib/settings/api.js';

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	});
}

function clientWith(handler: (request: Request) => Response): {
	api: ReturnType<typeof createApiClient>;
	fetchMock: ReturnType<typeof vi.fn<typeof globalThis.fetch>>;
} {
	const fetchMock = vi.fn<typeof globalThis.fetch>((input) =>
		Promise.resolve(handler(input as Request))
	);
	const api = createApiClient({
		baseUrl: 'http://api.test/base',
		getToken: () => Promise.resolve('token'),
		fetch: fetchMock
	});
	return { api, fetchMock };
}

describe('fetchAccount', () => {
	it('returns the stored settings', async () => {
		const { api, fetchMock } = clientWith(() =>
			json({ email: 'a@b.it', premium: false, settings: { theme: 'viola' } })
		);

		expect((await fetchAccount(api)).settings).toEqual({ theme: 'viola' });
		expect(new URL((fetchMock.mock.calls[0]?.[0] as Request).url).pathname).toBe('/base/me');
	});

	it('returns an empty object when none are stored', async () => {
		const { api } = clientWith(() => json({ email: 'a@b.it', premium: false, settings: null }));

		expect((await fetchAccount(api)).settings).toEqual({});
	});

	it('returns the temporary URL of the profile image', async () => {
		const { api } = clientWith(() =>
			json({ email: 'a@b.it', premium: false, settings: {}, avatarUrl: 'http://img.test/a.png' })
		);

		expect((await fetchAccount(api)).avatarUrl).toBe('http://img.test/a.png');
	});

	it('has no image when the user has none', async () => {
		const { api } = clientWith(() => json({ email: 'a@b.it', premium: false, settings: {} }));

		expect((await fetchAccount(api)).avatarUrl).toBeNull();
	});

	it('throws when the user cannot be read', async () => {
		const { api } = clientWith(() => json({ title: 'Not found', status: 404 }, 404));

		await expect(fetchAccount(api)).rejects.toThrow('Not found');
	});
});

describe('saveSettings', () => {
	it('patches only the changed keys and returns the merged settings', async () => {
		const { api, fetchMock } = clientWith(() =>
			json({ payload: { theme: 'viola', fontSize: 16 } })
		);

		const result = await saveSettings(api, { theme: 'viola' });

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('PATCH');
		expect(new URL(request.url).pathname).toBe('/base/me/settings');
		expect(await request.json()).toEqual({ settings: { theme: 'viola' } });
		expect(result).toEqual({ theme: 'viola', fontSize: 16 });
	});

	it('throws with the server message on failure', async () => {
		const { api } = clientWith(() => json({ title: 'Settings too large', status: 400 }, 400));

		await expect(saveSettings(api, { theme: 'viola' })).rejects.toThrow('Settings too large');
	});
});

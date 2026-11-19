import { describe, expect, it, vi } from 'vitest';
import { createApiClient, type ApiClient } from '#lib/api/client.js';
import {
	changeAvatar,
	deleteAccount,
	fetchAvatarUrl,
	fetchProfile,
	RecentLoginRequiredError
} from './api.js';

function apiReturning(result: unknown): ApiClient {
	return { GET: vi.fn(() => Promise.resolve(result)) } as unknown as ApiClient;
}

describe('fetchProfile', () => {
	it('returns the email and the premium flag', async () => {
		const profile = await fetchProfile(
			apiReturning({ data: { email: 'a@b.it', premium: true, settings: null } })
		);

		expect(profile).toEqual({ email: 'a@b.it', premium: true });
	});

	it('falls back to an empty email and no premium', async () => {
		const profile = await fetchProfile(apiReturning({ data: {} }));

		expect(profile).toEqual({ email: '', premium: false });
	});

	it('throws a readable error when the call fails', async () => {
		await expect(fetchProfile(apiReturning({ error: { status: 500 } }))).rejects.toThrow();
	});
});

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	});
}

function clientWith(handler: (request: Request) => Response): {
	api: ApiClient;
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

describe('fetchAvatarUrl', () => {
	it('returns the temporary URL or null', async () => {
		expect(
			await fetchAvatarUrl(apiReturning({ data: { avatarUrl: 'http://img.test/a.png' } }))
		).toBe('http://img.test/a.png');
		expect(await fetchAvatarUrl(apiReturning({ data: { avatarUrl: null } }))).toBeNull();
	});
});

describe('changeAvatar', () => {
	const file = new File(['x'], 'io.png', { type: 'image/png' });

	it('uploads the file to /me/avatar and returns the new URL', async () => {
		const { api, fetchMock } = clientWith(() => json({ payload: 'http://img.test/new.png' }));

		const url = await changeAvatar(api, { type: 'replace', file });

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('PUT');
		expect(new URL(request.url).pathname).toBe('/base/me/avatar');
		expect(((await request.formData()).get('file') as File).name).toBe('io.png');
		expect(url).toBe('http://img.test/new.png');
	});

	it('deletes the image and returns null', async () => {
		const { api, fetchMock } = clientWith(() => json({}));

		expect(await changeAvatar(api, { type: 'remove' })).toBeNull();

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('DELETE');
		expect(new URL(request.url).pathname).toBe('/base/me/avatar');
	});

	it('does nothing when the image is kept', async () => {
		const { api, fetchMock } = clientWith(() => json({}));

		expect(await changeAvatar(api, { type: 'keep' })).toBeNull();
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it('throws with the server message when the upload fails', async () => {
		const { api } = clientWith(() => json({ title: 'File too large', status: 400 }, 400));

		await expect(changeAvatar(api, { type: 'replace', file })).rejects.toThrow('File too large');
	});
});

describe('deleteAccount', () => {
	it('calls DELETE /me', async () => {
		const { api, fetchMock } = clientWith(() => json({}));

		await deleteAccount(api);

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('DELETE');
		expect(new URL(request.url).pathname).toBe('/base/me');
	});

	it('throws a readable error when the server refuses', async () => {
		const { api } = clientWith(() => json({ message: 'boom' }, 500));

		await expect(deleteAccount(api)).rejects.toThrow();
	});

	it('asks for a more recent login when the server answers 401', async () => {
		const { api } = clientWith(() => json({ title: 'UNAUTHORIZED' }, 401));

		await expect(deleteAccount(api)).rejects.toBeInstanceOf(RecentLoginRequiredError);
	});
});

import { describe, expect, it, vi } from 'vitest';
import {
	NETWORK_MESSAGE,
	NetworkError,
	createApiClient,
	describeApiError
} from '#lib/api/client.js';

describe('createApiClient', () => {
	it('sends the bearer token on every request', async () => {
		const fetchMock = vi.fn<typeof globalThis.fetch>(() =>
			Promise.resolve(
				new Response(JSON.stringify({ payload: 'user-id' }), {
					status: 200,
					headers: { 'Content-Type': 'application/json' }
				})
			)
		);
		const api = createApiClient({
			baseUrl: 'http://api.test/base',
			getToken: () => Promise.resolve('abc.def.ghi'),
			fetch: fetchMock
		});

		const { data } = await api.POST('/me');

		expect(data?.payload).toBe('user-id');
		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.url).toBe('http://api.test/base/me');
		expect(request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
	});
});

describe('createApiClient when the network fails', () => {
	function clientFailingWith(cause: Error): ReturnType<typeof createApiClient> {
		return createApiClient({
			baseUrl: 'http://api.test/base',
			getToken: () => Promise.resolve('abc'),
			fetch: vi.fn<typeof globalThis.fetch>(() => Promise.reject(cause))
		});
	}

	it('rejects with a NetworkError when fetch fails with a TypeError', async () => {
		const api = clientFailingWith(new TypeError('Failed to fetch'));

		await expect(api.POST('/me')).rejects.toBeInstanceOf(NetworkError);
	});

	it('rejects with an error that is still a TypeError', async () => {
		const api = clientFailingWith(new TypeError('Failed to fetch'));

		await expect(api.POST('/me')).rejects.toBeInstanceOf(TypeError);
	});

	it('rejects with the message in Italian', async () => {
		const api = clientFailingWith(new TypeError('Failed to fetch'));

		await expect(api.POST('/me')).rejects.toThrow(NETWORK_MESSAGE);
	});

	it('keeps the original error as the cause', async () => {
		const original = new TypeError('Failed to fetch');
		const api = clientFailingWith(original);

		await expect(api.POST('/me')).rejects.toHaveProperty('cause', original);
	});

	it('lets an error that is not a TypeError through unchanged', async () => {
		const original = new Error('boom');
		const api = clientFailingWith(original);

		await expect(api.POST('/me')).rejects.toBe(original);
	});
});

describe('createApiClient without a token', () => {
	it('does not send the request when the token cannot be obtained', async () => {
		const fetchMock = vi.fn<typeof globalThis.fetch>();
		const api = createApiClient({
			baseUrl: 'http://api.test/base',
			getToken: () => Promise.reject(new Error('Session expired')),
			fetch: fetchMock
		});

		await api.POST('/me').catch(() => undefined);

		expect(fetchMock).not.toHaveBeenCalled();
	});

	it('fails with the reason the token could not be obtained', async () => {
		const api = createApiClient({
			baseUrl: 'http://api.test/base',
			getToken: () => Promise.reject(new Error('Session expired')),
			fetch: vi.fn<typeof globalThis.fetch>()
		});

		await expect(api.POST('/me')).rejects.toThrow('Session expired');
	});
});

describe('describeApiError', () => {
	it('joins the violation messages', () => {
		expect(
			describeApiError(
				{
					title: 'Bad request',
					status: 400,
					violations: [{ field: 'name', message: 'must not be blank' }]
				},
				'fallback'
			)
		).toBe('Nome: non può essere vuoto');
	});

	it('ignores empty and null violation messages', () => {
		const error = {
			title: 'BAD_REQUEST',
			status: 400,
			violations: [{ message: null }, { message: '' }, {}]
		} as unknown as Parameters<typeof describeApiError>[0];

		expect(describeApiError(error, 'fallback')).toBe('Richiesta non valida');
	});

	it('translates every violation and joins them', () => {
		const error = {
			status: 400,
			violations: [{ field: 'title', message: 'must not be blank' }, { message: 'File too large' }]
		};

		expect(describeApiError(error, 'fallback')).toBe(
			'Titolo: non può essere vuoto. Il file è troppo grande'
		);
	});

	it('falls back to the title and then to the default', () => {
		expect(describeApiError({ title: 'Not found', status: 404 }, 'fallback')).toBe('Not found');
		expect(describeApiError({ title: 'NOT_FOUND', status: 404 }, 'fallback')).toBe(
			'Elemento non trovato'
		);
		expect(describeApiError(undefined, 'fallback')).toBe('fallback');
	});
});

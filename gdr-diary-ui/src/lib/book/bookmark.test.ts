import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from '#lib/api/client.js';
import { fetchBookmark, removeBookmark, setBookmark } from './bookmark.js';

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	});
}

function clientWith(handler: (request: Request) => Response): {
	api: ReturnType<typeof createApiClient>;
	requests: Request[];
} {
	const requests: Request[] = [];
	const fetchMock = vi.fn<typeof globalThis.fetch>((input) => {
		const request = input as Request;
		requests.push(request.clone());
		return Promise.resolve(handler(request));
	});
	const api = createApiClient({
		baseUrl: 'http://api.test/base',
		getToken: () => Promise.resolve('token'),
		fetch: fetchMock
	});
	return { api, requests };
}

describe('fetchBookmark', () => {
	it('reads the bookmark of the diary', async () => {
		const { api, requests } = clientWith(() => json({ pageId: 'p7', pageNumber: 7 }));

		const result = await fetchBookmark(api, 'd1');

		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/diaries/d1/bookmark');
		expect(requests[0]?.method).toBe('GET');
		expect(result).toEqual({ pageId: 'p7', pageNumber: 7 });
	});

	it('returns null when the diary has no bookmark', async () => {
		const { api } = clientWith(() => json({}));

		const result = await fetchBookmark(api, 'd1');

		expect(result).toBeNull();
	});

	it('throws the message of the API when the diary is not found', async () => {
		const { api } = clientWith(() => json({ title: 'Diary not found' }, 404));

		await expect(fetchBookmark(api, 'd1')).rejects.toThrow('Diario non trovato');
	});
});

describe('setBookmark', () => {
	it('puts the page identifier and returns the new bookmark', async () => {
		const { api, requests } = clientWith(() => json({ pageId: 'p3', pageNumber: 3 }));

		const result = await setBookmark(api, 'd1', 'p3');

		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/diaries/d1/bookmark');
		expect(requests[0]?.method).toBe('PUT');
		expect(await requests[0]?.json()).toEqual({ pageId: 'p3' });
		expect(result).toEqual({ pageId: 'p3', pageNumber: 3 });
	});

	it('throws the message of the API when the page is not found', async () => {
		const { api } = clientWith(() => json({ title: 'Page not found' }, 404));

		await expect(setBookmark(api, 'd1', 'p3')).rejects.toThrow('Pagina non trovata');
	});

	it('throws a default message when the answer has no page', async () => {
		const { api } = clientWith(() => json({}));

		await expect(setBookmark(api, 'd1', 'p3')).rejects.toThrow(
			'Non riesco a mettere il segnalibro.'
		);
	});
});

describe('removeBookmark', () => {
	it('deletes the bookmark of the diary', async () => {
		const { api, requests } = clientWith(() => json({ message: 'Bookmark removed' }));

		await removeBookmark(api, 'd1');

		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/diaries/d1/bookmark');
		expect(requests[0]?.method).toBe('DELETE');
	});

	it('accepts a diary that is already gone', async () => {
		const { api } = clientWith(() => json({ title: 'Diary not found' }, 404));

		await expect(removeBookmark(api, 'd1')).resolves.toBeUndefined();
	});

	it('throws when the server fails', async () => {
		const { api } = clientWith(() => json({ title: 'boom' }, 500));

		await expect(removeBookmark(api, 'd1')).rejects.toThrow('boom');
	});
});

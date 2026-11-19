import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from '#lib/api/client.js';
import { fetchPayments } from './api.js';

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

describe('fetchPayments', () => {
	it('asks the payments of the account, most recent first, ten at a time', async () => {
		const { api, requests } = clientWith(() =>
			json({ list: [], totalRows: 0, totalPages: 0, page: 2 })
		);

		await fetchPayments(api, 2);

		const url = new URL(requests[0]?.url ?? '');
		expect([
			url.pathname,
			url.searchParams.get('page'),
			url.searchParams.get('size'),
			url.searchParams.get('sort'),
			url.searchParams.get('descending')
		]).toEqual(['/base/me/payments', '2', '10', 'paidAt', 'true']);
	});

	it('maps the page of the API', async () => {
		const { api } = clientWith(() =>
			json({ list: [{ id: 'p1' }], totalRows: 11, totalPages: 2, page: 1 })
		);

		const result = await fetchPayments(api, 1);

		expect(result).toEqual({ items: [{ id: 'p1' }], totalRows: 11, totalPages: 2, page: 1 });
	});

	it('fails with a message in Italian when the API does not answer', async () => {
		const { api } = clientWith(() => json({ title: 'INTERNAL_ERROR', status: 500 }, 500));

		await expect(fetchPayments(api, 1)).rejects.toThrow(
			'Si è verificato un errore imprevisto: riprova più tardi'
		);
	});
});

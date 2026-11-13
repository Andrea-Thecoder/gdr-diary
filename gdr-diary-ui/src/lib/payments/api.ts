import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import type { components } from '#lib/api/schema.js';
import type { PageResult } from '#lib/list/paged-list.svelte.js';

export type Payment = components['schemas']['BasePaymentDTO'];

export const PAGE_SIZE = 10;

export async function fetchPayments(api: ApiClient, page: number): Promise<PageResult<Payment>> {
	const { data, error } = await api.GET('/me/payments', {
		params: { query: { page, size: PAGE_SIZE, sort: 'paidAt', descending: true } }
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare i pagamenti.'));
	}
	return {
		items: data.list ?? [],
		totalRows: data.totalRows ?? 0,
		totalPages: data.totalPages ?? 0,
		page: data.page ?? page
	};
}

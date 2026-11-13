import { getApi } from '#lib/api/index.js';
import { PagedList, type PageResult } from '#lib/list/paged-list.svelte.js';
import { fetchPayments, type Payment } from './api.js';

export class PaymentList extends PagedList<Payment> {
	protected readonly failure = 'Non riesco a caricare i pagamenti.';

	protected load(page: number): Promise<PageResult<Payment>> {
		return fetchPayments(getApi(), page);
	}
}

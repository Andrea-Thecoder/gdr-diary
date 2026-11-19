import { describe, expect, it } from 'vitest';
import { PagedList, type PageResult } from './paged-list.svelte.js';

interface Deferred {
	page: number;
	resolve: (result: PageResult<string>) => void;
	reject: (cause: unknown) => void;
}

class TestList extends PagedList<string> {
	readonly requests: Deferred[] = [];

	protected readonly failure = 'Non riesco a caricare.';

	protected load(page: number): Promise<PageResult<string>> {
		return new Promise((resolve, reject) => {
			this.requests.push({ page, resolve, reject });
		});
	}
}

function result(items: string[], page: number, totalPages: number): PageResult<string> {
	return { items, totalRows: items.length, totalPages, page };
}

async function readyList(totalPages = 2): Promise<TestList> {
	const list = new TestList();
	const loading = list.reload();
	list.requests[0]?.resolve(result(['a', 'b'], 1, totalPages));
	await loading;
	return list;
}

describe('PagedList reload', () => {
	it('ignores a slow reload when a newer one has started', async () => {
		const list = new TestList();
		const slow = list.reload();
		const fast = list.reload();
		list.requests[1]?.resolve(result(['new'], 1, 1));
		await fast;

		list.requests[0]?.resolve(result(['old'], 1, 1));
		await slow;

		expect(list.items).toEqual(['new']);
	});

	it('ignores the failure of a slow reload when a newer one has started', async () => {
		const list = new TestList();
		const slow = list.reload();
		const fast = list.reload();
		list.requests[1]?.resolve(result(['new'], 1, 1));
		await fast;

		list.requests[0]?.reject(new Error('Vecchio errore'));
		await slow;

		expect(list.status).toBe('ready');
	});
});

describe('PagedList loadMore', () => {
	it('appends the next page to the loaded items', async () => {
		const list = await readyList();
		const more = list.loadMore();

		list.requests[1]?.resolve(result(['c'], 2, 2));
		await more;

		expect(list.items).toEqual(['a', 'b', 'c']);
	});

	it('ignores its result when a reload started meanwhile', async () => {
		const list = await readyList();
		const more = list.loadMore();
		const reloading = list.reload();

		list.requests[2]?.resolve(result(['fresh'], 1, 1));
		await reloading;
		list.requests[1]?.resolve(result(['late'], 2, 2));
		await more;

		expect(list.items).toEqual(['fresh']);
	});

	it('does nothing when there is no next page', async () => {
		const list = await readyList(1);

		await list.loadMore();

		expect(list.requests).toHaveLength(1);
	});

	it('does nothing while another page is loading', async () => {
		const list = await readyList();
		const first = list.loadMore();

		await list.loadMore();

		expect(list.requests).toHaveLength(2);
		list.requests[1]?.resolve(result(['c'], 2, 2));
		await first;
	});

	it('keeps the loaded items and sets the error when it fails', async () => {
		const list = await readyList();
		const more = list.loadMore();

		list.requests[1]?.reject(new Error('Rete assente'));
		await more;

		expect(list.items).toEqual(['a', 'b']);
		expect(list.error).toBe('Rete assente');
	});

	it('uses the fallback text when the failure is not an error', async () => {
		const list = await readyList();
		const more = list.loadMore();

		list.requests[1]?.reject('boom');
		await more;

		expect(list.error).toBe('Non riesco a caricare.');
	});
});

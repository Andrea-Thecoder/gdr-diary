import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAllDrafts, createDraftStore } from './draft.js';
import { doc, draftFixture } from './test-support.js';

const draft = draftFixture;

describe('IndexedDB draft store', () => {
	beforeEach(async () => {
		await clearAllDrafts();
	});

	it('keeps a copy and gives it back as it was written', async () => {
		const store = createDraftStore();
		await store.put(draft());
		expect(await store.list('d1')).toEqual([draft()]);
	});

	it('lists only the copies of the diary asked for', async () => {
		const store = createDraftStore();
		await store.put(draft({ page: 1 }));
		await store.put(draft({ page: 2 }));
		await store.put(draft({ diaryId: 'd2' }));
		const found = await store.list('d1');
		expect(found.map((item) => item.page).sort()).toEqual([1, 2]);
	});

	it('replaces the copy of the same page and removes it on request', async () => {
		const store = createDraftStore();
		await store.put(draft({ content: doc('prima') }));
		await store.put(draft({ content: doc('dopo') }));
		expect((await store.list('d1')).map((item) => item.content)).toEqual([doc('dopo')]);
		await store.remove('d1', 1);
		expect(await store.list('d1')).toEqual([]);
	});

	it('keeps the copy of a page that was never created', async () => {
		const store = createDraftStore();
		await store.put(draft({ pageId: null }));
		expect((await store.list('d1')).map((item) => item.pageId)).toEqual([null]);
	});

	it('forgets every copy of every diary on logout', async () => {
		const store = createDraftStore();
		await store.put(draft());
		await store.put(draft({ diaryId: 'd2' }));
		await clearAllDrafts();
		expect(await store.list('d1')).toEqual([]);
		expect(await store.list('d2')).toEqual([]);
	});
	afterEach(() => {
		vi.restoreAllMocks();
	});

	function deleteDatabase(): Promise<void> {
		return new Promise<void>((resolve, reject) => {
			const request = indexedDB.deleteDatabase('gdr-diary-drafts');
			request.onsuccess = () => {
				resolve();
			};
			request.onerror = () => {
				reject(new Error('delete failed'));
			};
		});
	}

	function writeRaw(row: Record<string, unknown>): Promise<void> {
		return new Promise<void>((resolve, reject) => {
			const request = indexedDB.open('gdr-diary-drafts');
			request.onsuccess = () => {
				const database = request.result;
				const transaction = database.transaction('drafts', 'readwrite');
				transaction.objectStore('drafts').put(row);
				transaction.oncomplete = () => {
					database.close();
					resolve();
				};
				transaction.onerror = () => {
					reject(new Error('write failed'));
				};
			};
			request.onerror = () => {
				reject(new Error('open failed'));
			};
		});
	}

	it('ignores rows that are not valid copies when listing', async () => {
		const store = createDraftStore();
		await store.put(draft({ page: 1 }));
		await writeRaw({ key: 'd1:7', diaryId: 'd1', page: '7' });
		await writeRaw({ key: 'd1:8', diaryId: 'd1', page: 8, pageId: 5, baseVersion: 1 });

		const found = await store.list('d1');

		expect(found.map((item) => item.page)).toEqual([1]);
	});

	it('resolves when the database cannot be opened while clearing', async () => {
		await deleteDatabase();
		vi.spyOn(indexedDB, 'open').mockImplementation(() => {
			const request = {} as IDBOpenDBRequest;
			queueMicrotask(() => {
				request.onerror?.call(request, new Event('error'));
			});
			return request;
		});

		await expect(clearAllDrafts()).resolves.toBeUndefined();
	});

	it('writes again after the database was deleted under an open connection', async () => {
		const store = createDraftStore();
		await store.put(draft({ page: 1 }));
		await deleteDatabase();

		await store.put(draft({ page: 2 }));

		expect((await store.list('d1')).map((item) => item.page)).toEqual([2]);
	});
});

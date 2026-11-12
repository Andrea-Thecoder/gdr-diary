import { describe, expect, it } from 'vitest';
import {
	clearAllDrafts,
	createDraftStore,
	createMemoryDraftStore,
	judgeDraft,
	loadDrafts,
	type Draft,
	type DraftStore
} from './draft.js';
import { emptyDoc } from './pages.js';
import { doc, draftFixture } from './test-support.js';

function draft(overrides: Partial<Draft> = {}): Draft {
	return draftFixture({ page: 3, pageId: 'p3', ...overrides });
}

describe('judgeDraft', () => {
	it('drops a copy that differs from the same page on the server', () => {
		expect(judgeDraft(draft(), { id: 'p3', content: doc('salvato') }, 10)).toBe('drop');
	});

	it('drops a copy that equals the page on the server', () => {
		expect(judgeDraft(draft(), { id: 'p3', content: doc('bozza') }, 10)).toBe('drop');
	});

	it('searches for a copy whose page id is another page now', () => {
		expect(judgeDraft(draft(), { id: 'altra', content: doc('salvato') }, 10)).toBe('search');
	});

	it('drops the copy of a new page whose number was taken', () => {
		expect(judgeDraft(draft({ pageId: null }), { id: 'altra', content: doc('salvato') }, 10)).toBe(
			'drop'
		);
	});

	it('waits for a page of the diary that is not loaded yet', () => {
		expect(judgeDraft(draft(), undefined, 10)).toBe('wait');
	});

	it('searches for a copy of a page beyond the end of the diary', () => {
		expect(judgeDraft(draft({ page: 12 }), undefined, 10)).toBe('search');
	});

	it('applies the text of a page that was never created only for the next page', () => {
		const created = draft({ page: 11, pageId: null });
		expect(judgeDraft(created, undefined, 10)).toBe('apply');
		expect(judgeDraft({ ...created, page: 12 }, undefined, 10)).toBe('drop');
		expect(judgeDraft({ ...created, page: 10 }, undefined, 10)).toBe('drop');
	});

	it('drops a copy of a new page that has no text', () => {
		expect(judgeDraft(draft({ page: 11, pageId: null, content: emptyDoc() }), undefined, 10)).toBe(
			'drop'
		);
	});
});

describe('memory draft store', () => {
	it('lists only the copies of the diary asked for', async () => {
		const store = createMemoryDraftStore();
		await store.put(draft({ page: 1 }));
		await store.put(draft({ page: 2 }));
		await store.put(draft({ diaryId: 'd2', page: 1 }));
		const found = await store.list('d1');
		expect(found.map((item) => item.page)).toEqual([1, 2]);
	});

	it('replaces the copy of the same page', async () => {
		const store = createMemoryDraftStore();
		await store.put(draft({ content: doc('prima') }));
		await store.put(draft({ content: doc('dopo') }));
		const found = await store.list('d1');
		expect(found).toHaveLength(1);
		expect(found[0]?.content).toEqual(doc('dopo'));
	});

	it('removes a copy', async () => {
		const store = createMemoryDraftStore();
		await store.put(draft());
		await store.remove('d1', 3);
		expect(await store.list('d1')).toEqual([]);
	});
});

describe('loadDrafts', () => {
	it('gives back the copies of the diary', async () => {
		const store = createMemoryDraftStore();
		await store.put(draft({ page: 1 }));
		await store.put(draft({ diaryId: 'd2' }));
		expect((await loadDrafts('d1', store)).map((item) => item.page)).toEqual([1]);
	});

	it('gives back nothing instead of failing when the store does not work', async () => {
		const broken: DraftStore = {
			list: () => Promise.reject(new Error('Archivio non disponibile')),
			put: () => Promise.reject(new Error('Archivio non disponibile')),
			remove: () => Promise.reject(new Error('Archivio non disponibile'))
		};
		expect(await loadDrafts('d1', broken)).toEqual([]);
	});
});

describe('draft store without IndexedDB', () => {
	it('falls back to memory when the browser has no IndexedDB', async () => {
		const store = createDraftStore();

		await store.put(draft({ page: 1 }));

		expect((await store.list('d1')).map((item) => item.page)).toEqual([1]);
	});

	it('removes a copy from the memory fallback', async () => {
		const store = createDraftStore();
		await store.put(draft({ page: 1 }));

		await store.remove('d1', 1);

		expect(await store.list('d1')).toEqual([]);
	});

	it('clears nothing and resolves when the browser has no IndexedDB', async () => {
		await expect(clearAllDrafts()).resolves.toBeUndefined();
	});
});

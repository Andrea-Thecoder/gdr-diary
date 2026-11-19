import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '#lib/api/client.js';
import { MISSING_PAGE_MESSAGE } from './pages.js';
import { SUGGEST_DELAY, DiaryTagList, PageTagStore } from './tags.svelte.js';
import { EMPTY_TAG_MESSAGE, TAG_MAX_LENGTH, TAG_TOO_LONG_MESSAGE } from './tags.js';

type Call = (...args: unknown[]) => Promise<unknown>;

interface Setup {
	store: PageTagStore;
	GET: ReturnType<typeof vi.fn<Call>>;
	PUT: ReturnType<typeof vi.fn<Call>>;
}

function echo(_path: unknown, init: unknown): Promise<unknown> {
	const tag = (init as { body: { tag: string | null } }).body.tag;
	return Promise.resolve({ data: { tag } });
}

function setup(tag: string | null = null): Setup {
	const GET = vi.fn<Call>().mockResolvedValue({ data: { list: [] } });
	const PUT = vi.fn<Call>(echo);
	const api = { GET, PUT } as unknown as ApiClient;
	const store = new PageTagStore(api, 'd1');
	store.seed('p1', tag);
	return { store, GET, PUT };
}

function sentTag(PUT: Setup['PUT'], call = 0): unknown {
	return (PUT.mock.calls[call]?.[1] as { body: { tag: string | null } }).body.tag;
}

beforeEach(() => {
	vi.useFakeTimers();
});

afterEach(() => {
	vi.useRealTimers();
});

describe('seed', () => {
	it('keeps the tag of a page and has none for an unknown one', () => {
		const { store } = setup('combat');
		expect(store.tagOf('p1')).toBe('combat');
		expect(store.tagOf('other')).toBeNull();
		expect(store.unsaved).toBe(false);
	});

	it('does not overwrite a tag that is waiting to be saved', () => {
		const { store } = setup();
		store.set('p1', 'Erebor');
		store.seed('p1', 'altro');
		expect(store.tagOf('p1')).toBe('Erebor');
	});

	it('replaces the tag of a page that has nothing to save', () => {
		const { store } = setup('combat');
		store.seed('p1', 'altro');
		expect(store.tagOf('p1')).toBe('altro');
	});
});

describe('set and clear', () => {
	it('shows the tag at once and marks the page as not saved', () => {
		const { store, PUT } = setup();
		expect(store.set('p1', ' Erebor ')).toBe(true);
		expect(store.tagOf('p1')).toBe('Erebor');
		expect(store.pendingOf('p1')).toBe(true);
		expect(store.unsaved).toBe(true);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('replaces the tag the page had', () => {
		const { store } = setup('combat');
		expect(store.set('p1', 'Erebor')).toBe(true);
		expect(store.tagOf('p1')).toBe('Erebor');
	});

	it('takes the same tag again without changing anything', () => {
		const { store } = setup('combat');
		expect(store.set('p1', ' combat ')).toBe(true);
		expect(store.pendingOf('p1')).toBe(false);
	});

	it('refuses an empty text and says why', () => {
		const { store } = setup('combat');
		expect(store.set('p1', '   ')).toBe(false);
		expect(store.errorOf('p1')).toBe(EMPTY_TAG_MESSAGE);
		expect(store.tagOf('p1')).toBe('combat');
	});

	it('refuses a tag that is too long and says why', () => {
		const { store } = setup();
		expect(store.set('p1', 'x'.repeat(TAG_MAX_LENGTH + 1))).toBe(false);
		expect(store.errorOf('p1')).toBe(TAG_TOO_LONG_MESSAGE);
		expect(store.tagOf('p1')).toBeNull();
	});

	it('does nothing for a page it does not know', () => {
		const { store } = setup();
		expect(store.set('other', 'x')).toBe(false);
		store.clear('other');
		expect(store.tagOf('other')).toBeNull();
	});

	it('takes the tag away and marks the page as not saved', () => {
		const { store } = setup('combat');
		store.clear('p1');
		expect(store.tagOf('p1')).toBeNull();
		expect(store.pendingOf('p1')).toBe(true);
	});

	it('has nothing to save when a page without a tag is cleared', () => {
		const { store } = setup();
		store.clear('p1');
		expect(store.pendingOf('p1')).toBe(false);
	});
});

describe('saving', () => {
	it('saves at once, with the tag and no version', async () => {
		const { store, PUT } = setup();
		store.set('p1', 'Erebor');
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(PUT.mock.calls[0]?.[0]).toBe('/pages/{id}/tag');
		expect(PUT.mock.calls[0]?.[1]).toEqual({
			params: { path: { id: 'p1' } },
			body: { tag: 'Erebor' }
		});
		expect(store.pendingOf('p1')).toBe(false);
		expect(store.revision).toBe(1);
	});

	it('sends only the last tag when it changes more than once at the same time', async () => {
		const { store, PUT } = setup();
		store.set('p1', 'uno');
		store.set('p1', 'due');
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(sentTag(PUT)).toBe('due');
		expect(store.tagOf('p1')).toBe('due');
		expect(store.pendingOf('p1')).toBe(false);
	});

	it('saves the removal of the tag at once', async () => {
		const { store, PUT } = setup('combat');
		store.clear('p1');
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(sentTag(PUT)).toBeNull();
	});

	it('does not call the server when the tag stays the same', async () => {
		const { store, PUT } = setup('combat');
		store.set('p1', 'combat');
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('shows the spelling the server answers with', async () => {
		const { store, PUT } = setup();
		PUT.mockResolvedValue({ data: { tag: 'Erebor' } });
		store.set('p1', 'erebor');
		await store.flush();
		expect(store.tagOf('p1')).toBe('Erebor');
	});

	it('sends null to take the tag away', async () => {
		const { store, PUT } = setup('combat');
		store.clear('p1');
		await store.flush();
		expect(sentTag(PUT)).toBeNull();
		expect(store.tagOf('p1')).toBeNull();
	});

	it('keeps a change made while saving and saves it next', async () => {
		const { store, PUT } = setup();
		let release: () => void = () => undefined;
		PUT.mockImplementationOnce(
			(_path: unknown, init: unknown) =>
				new Promise((resolve) => {
					release = () => {
						resolve({ data: { tag: (init as { body: { tag: string | null } }).body.tag } });
					};
				})
		);
		store.set('p1', 'uno');
		await vi.advanceTimersByTimeAsync(0);
		expect(store.savingOf('p1')).toBe(true);
		store.set('p1', 'due');
		expect(store.pendingOf('p1')).toBe(true);
		release();
		await store.flush();
		expect(store.tagOf('p1')).toBe('due');
		expect(PUT).toHaveBeenCalledTimes(2);
		expect(sentTag(PUT, 1)).toBe('due');
		expect(store.pendingOf('p1')).toBe(false);
	});

	it('does not save a page that was forgotten', async () => {
		const { store, PUT } = setup();
		store.set('p1', 'Erebor');
		store.forget('p1');
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).not.toHaveBeenCalled();
		expect(store.tagOf('p1')).toBeNull();
	});

	it('does not save anything after discard', async () => {
		const { store, PUT } = setup();
		store.set('p1', 'Erebor');
		store.discard();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).not.toHaveBeenCalled();
		expect(store.unsaved).toBe(false);
	});
});

describe('errors', () => {
	it('shows the message of a 400 and does not retry by itself', async () => {
		const { store, PUT } = setup();
		PUT.mockResolvedValue({
			error: {
				title: 'BAD_REQUEST',
				status: 400,
				violations: [{ message: 'A diary can have at most 10 different tags' }]
			},
			response: { status: 400 }
		});
		store.set('p1', 'Erebor');
		await vi.advanceTimersByTimeAsync(0);
		expect(store.errorOf('p1')).toBe(
			'Un diario può avere al massimo 10 tag diversi: usa uno dei tag già usati.'
		);
		expect(store.pendingOf('p1')).toBe(true);
		expect(store.tagOf('p1')).toBe('Erebor');
		await vi.advanceTimersByTimeAsync(10);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('clears the error on the next change', async () => {
		const { store, PUT } = setup();
		PUT.mockResolvedValueOnce({ error: {}, response: { status: 400 } });
		store.set('p1', 'Erebor');
		await store.flush();
		store.clear('p1');
		expect(store.errorOf('p1')).toBeNull();
	});

	it('says the page is gone on a 404 and stops editing it', async () => {
		const { store, PUT } = setup();
		PUT.mockResolvedValue({ error: {}, response: { status: 404 } });
		store.set('p1', 'Erebor');
		await vi.advanceTimersByTimeAsync(0);
		expect(store.goneOf('p1')).toBe(true);
		expect(store.errorOf('p1')).toBe(MISSING_PAGE_MESSAGE);
		expect(store.unsaved).toBe(false);
		expect(store.set('p1', 'altro')).toBe(false);
		store.clear('p1');
		expect(store.tagOf('p1')).toBe('Erebor');
	});

	it('says the server cannot be reached when the network fails', async () => {
		const { store, PUT } = setup();
		PUT.mockRejectedValue(new TypeError('Failed to fetch'));
		store.set('p1', 'Erebor');
		expect(await store.flush()).toBe(false);
		expect(store.errorOf('p1')).toBe('Non riesco a raggiungere il server.');
		expect(store.pendingOf('p1')).toBe(true);
	});

	it('tries again on an explicit flush', async () => {
		const { store, PUT } = setup();
		PUT.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		store.set('p1', 'Erebor');
		await store.flush();
		expect(await store.flush()).toBe(true);
		expect(PUT).toHaveBeenCalledTimes(2);
		expect(store.errorOf('p1')).toBeNull();
	});
});

describe('suggestions', () => {
	it('asks the diary for the tags that contain the text, without the ones already on the page', async () => {
		const { store, GET } = setup('combat');
		GET.mockResolvedValue({
			data: { list: [{ name: 'combat' }, { name: 'Combattimento' }, { name: 'combo' }] }
		});
		store.suggest('com', ['COMBAT']);
		await vi.advanceTimersByTimeAsync(SUGGEST_DELAY);
		expect(GET).toHaveBeenCalledWith('/diaries/{diaryId}/tags', {
			params: { path: { diaryId: 'd1' }, query: { page: 1, size: 8, name: 'com' } }
		});
		expect(store.suggestions).toEqual(['Combattimento', 'combo']);
	});

	it('waits for a pause in the typing and asks once', async () => {
		const { store, GET } = setup();
		store.suggest('c', []);
		await vi.advanceTimersByTimeAsync(SUGGEST_DELAY - 1);
		store.suggest('co', []);
		await vi.advanceTimersByTimeAsync(SUGGEST_DELAY);
		expect(GET).toHaveBeenCalledTimes(1);
	});

	it('empties the list at once for an empty text', async () => {
		const { store, GET } = setup('combat');
		GET.mockResolvedValue({ data: { list: [{ name: 'combat' }] } });
		await store.lookup('com', []);
		expect(store.suggestions).toEqual(['combat']);
		store.suggest('  ', []);
		expect(store.suggestions).toEqual([]);
		await vi.advanceTimersByTimeAsync(SUGGEST_DELAY * 2);
		expect(GET).toHaveBeenCalledTimes(1);
	});

	it('shows nothing when the call fails', async () => {
		const { store, GET } = setup();
		GET.mockRejectedValue(new TypeError('Failed to fetch'));
		await store.lookup('com', []);
		expect(store.suggestions).toEqual([]);
	});

	it('forgets an answer that arrives after the text changed', async () => {
		const { store, GET } = setup('combat');
		let answer: (value: unknown) => void = () => undefined;
		GET.mockReturnValueOnce(
			new Promise((resolve) => {
				answer = resolve;
			})
		);
		const late = store.lookup('com', []);
		store.clearSuggestions();
		answer({ data: { list: [{ name: 'combat' }] } });
		await late;
		expect(store.suggestions).toEqual([]);
	});
});

describe('DiaryTagList', () => {
	it('loads the tags of the diary page by page', async () => {
		const GET = vi
			.fn<Call>()
			.mockResolvedValueOnce({
				data: {
					list: [{ name: 'combat', pageNumbers: [3] }],
					totalRows: 2,
					totalPages: 2,
					page: 1
				}
			})
			.mockResolvedValueOnce({
				data: { list: [{ name: 'pnj', pageNumbers: [1, 2] }], totalRows: 2, totalPages: 2, page: 2 }
			});
		const list = new DiaryTagList({ GET } as unknown as ApiClient, 'd1');
		await list.reload();
		expect(GET).toHaveBeenLastCalledWith('/diaries/{diaryId}/tags', {
			params: { path: { diaryId: 'd1' }, query: { page: 1, size: 50 } }
		});
		expect(list.hasMore).toBe(true);
		await list.loadMore();
		expect(list.items).toEqual([
			{ name: 'combat', pageNumbers: [3] },
			{ name: 'pnj', pageNumbers: [1, 2] }
		]);
		expect(list.hasMore).toBe(false);
	});

	it('keeps the message of a failure', async () => {
		const GET = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 500 } });
		const list = new DiaryTagList({ GET } as unknown as ApiClient, 'd1');
		await list.reload();
		expect(list.status).toBe('error');
		expect(list.error).not.toBeNull();
	});
});

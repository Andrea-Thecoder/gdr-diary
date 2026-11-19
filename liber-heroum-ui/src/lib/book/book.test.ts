import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { JSONContent } from '@tiptap/core';
import type { ApiClient } from '#lib/api/client.js';
import {
	DRAFT_DELAY,
	DiaryBook,
	RETRY_DELAY,
	RETRY_MAX_DELAY,
	SAVE_DELAY,
	SAVE_MAX_WAIT,
	type BookOptions
} from './book.svelte.js';
import { createMemoryDraftStore, type DraftStore } from './draft.js';
import { MAX_PAGES, emptyDoc, hasText, type SpreadData } from './pages.js';
import { doc, draftFixture, spreadData } from './test-support.js';

type Call = (...args: unknown[]) => Promise<unknown>;

interface Setup {
	book: DiaryBook;
	GET: ReturnType<typeof vi.fn<Call>>;
	POST: ReturnType<typeof vi.fn<Call>>;
	PUT: ReturnType<typeof vi.fn<Call>>;
	DELETE: ReturnType<typeof vi.fn<Call>>;
	onview: ReturnType<typeof vi.fn<(spread: number) => void>>;
	drafts: DraftStore;
}

function setup(total: number, options: Partial<BookOptions> = {}, spread = 1): Setup {
	const GET = vi.fn<Call>(async (_path: unknown, init: unknown) => {
		const view = Number((init as { params: { query: { page: string } } }).params.query.page);
		const data = spreadData(total, view);
		return Promise.resolve({
			data: { list: data.pages, totalRows: total, totalPages: Math.ceil(total / 2), page: view }
		});
	});
	const POST = vi.fn<Call>();
	const PUT = vi.fn<Call>();
	const DELETE = vi.fn<Call>();
	const api = { GET, POST, PUT, DELETE } as unknown as ApiClient;
	const onview = vi.fn<(spread: number) => void>();
	const drafts = createMemoryDraftStore();
	const book = new DiaryBook(api, 'd1', spreadData(total, spread), spread, {
		autoSave: () => true,
		drafts,
		animate: () => false,
		onview,
		...options
	});
	return { book, GET, POST, PUT, DELETE, onview, drafts };
}

beforeEach(() => {
	vi.useFakeTimers();
});

afterEach(() => {
	vi.useRealTimers();
});

describe('initial state', () => {
	it('shows the pages of the first spread', () => {
		const { book } = setup(23);
		expect(book.visible).toEqual([1, 2]);
		expect(book.label).toBe('Pagine 1 e 2 di 23');
		expect(book.state).toBe('saved');
		expect(book.previous).toBe(false);
		expect(book.next).toBe(true);
	});

	it('starts from the left page of the requested spread', () => {
		const { book } = setup(23, {}, 4);
		expect(book.visible).toEqual([7, 8]);
	});

	it('treats an empty diary as one editable page and one placeholder', () => {
		const { book } = setup(0);
		expect(book.label).toBe('Nessuna pagina ancora');
		expect(book.editable(1)).toBe(true);
		expect(book.editable(2)).toBe(false);
		expect(book.next).toBe(false);
	});

	it('keeps the blank sheet after an odd last page closed to writing', () => {
		const { book } = setup(23, {}, 12);
		expect(book.visible).toEqual([23, 24]);
		expect(book.editable(23)).toBe(true);
		expect(book.editable(24)).toBe(false);
		expect(book.label).toBe('Pagina 23 di 23');
		expect(book.next).toBe(false);
	});

	it('is full at 100 pages', () => {
		const { book } = setup(100, {}, 50);
		expect(book.full).toBe(true);
		expect(book.editable(100)).toBe(true);
		expect(book.editable(101)).toBe(false);
	});
});

describe('saving', () => {
	it('puts the page after a pause in writing and not before', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: doc('nuovo') } });
		book.update(1, doc('nuovo'));
		expect(book.state).toBe('pending');
		await vi.advanceTimersByTimeAsync(SAVE_DELAY - 10);
		expect(book.state).toBe('pending');
		expect(PUT).not.toHaveBeenCalled();
		await vi.advanceTimersByTimeAsync(20);
		expect(PUT).toHaveBeenCalledWith('/pages/{id}', {
			params: { path: { id: 'p1' } },
			body: { content: doc('nuovo'), version: 3 }
		});
		expect(book.state).toBe('saved');
	});

	it('waits for a longer pause when the writer keeps typing', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('a'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY * 0.8);
		book.update(1, doc('ab'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY * 0.8);
		expect(PUT).not.toHaveBeenCalled();
		await vi.advanceTimersByTimeAsync(SAVE_DELAY * 0.3);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('does not save by itself when automatic saving is off but saves on demand', async () => {
		const { book, PUT } = setup(4, { autoSave: () => false });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('nuovo'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY * 3);
		expect(PUT).not.toHaveBeenCalled();
		expect(book.state).toBe('unsaved');
		await book.flush();
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(book.state).toBe('saved');
	});

	it('creates a page only when it has text', async () => {
		const { book, POST } = setup(0);
		book.update(1, emptyDoc());
		await book.flush();
		expect(POST).not.toHaveBeenCalled();
		POST.mockResolvedValue({ data: { id: 'n1', pageNumber: 1, content: doc('inizio') } });
		book.update(1, doc('inizio'));
		await book.flush();
		expect(POST).toHaveBeenCalledWith('/diaries/{diaryId}/pages', {
			params: { path: { diaryId: 'd1' } },
			body: { content: doc('inizio') }
		});
		expect(book.total).toBe(1);
		expect(book.editable(2)).toBe(false);
		expect(book.label).toBe('Pagina 1 di 1');
	});

	it('uses put for the following writes of a created page', async () => {
		const { book, POST, PUT } = setup(0);
		POST.mockResolvedValue({ data: { id: 'n1', pageNumber: 1, content: {} } });
		PUT.mockResolvedValue({ data: { id: 'n1', pageNumber: 1, content: {} } });
		book.update(1, doc('inizio'));
		await book.flush();
		book.update(1, doc('inizio e seguito'));
		await book.flush();
		expect(POST).toHaveBeenCalledTimes(1);
		expect(PUT).toHaveBeenCalledWith(
			'/pages/{id}',
			expect.objectContaining({ params: { path: { id: 'n1' } } })
		);
	});

	it('keeps the text and reports the failure, then saves on retry', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValueOnce({
			error: { title: 'Server non raggiungibile' },
			response: { status: 500 }
		});
		book.update(1, doc('importante'));
		await book.flush();
		expect(book.failure).toEqual({ page: 1, message: 'Server non raggiungibile' });
		expect(book.state).toBe('unsaved');
		expect(book.content(1)).toEqual(doc('importante'));
		expect(book.unsaved).toBe(true);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.retry();
		await vi.advanceTimersByTimeAsync(0);
		expect(book.failure).toBeNull();
		expect(book.state).toBe('saved');
	});

	it('retries by itself after a pause', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValueOnce({ error: { title: 'Rete assente' }, response: { status: 500 } });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('importante'));
		await book.flush();
		expect(book.failure).not.toBeNull();
		await vi.advanceTimersByTimeAsync(RETRY_DELAY + 50);
		expect(book.failure).toBeNull();
		expect(PUT).toHaveBeenCalledTimes(2);
	});

	it('stops retrying once disposed', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		book.update(1, doc('importante'));
		await book.flush();
		book.dispose();
		await vi.advanceTimersByTimeAsync(RETRY_DELAY * 3);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('saves again when the text changed while the request was running', async () => {
		const { book, PUT } = setup(4);
		let release: (value: unknown) => void = () => undefined;
		PUT.mockImplementationOnce(
			() =>
				new Promise((resolve) => {
					release = resolve;
				})
		);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('uno'));
		const first = book.flush();
		await vi.advanceTimersByTimeAsync(0);
		book.update(1, doc('uno due'));
		release({ data: { id: 'p1', pageNumber: 1, content: {} } });
		await first;
		expect(PUT).toHaveBeenCalledTimes(2);
		expect(book.state).toBe('saved');
	});

	it('creates pages in ascending order', async () => {
		const { book, POST } = setup(2);
		const order: string[] = [];
		POST.mockImplementation((_path: unknown, init: unknown) => {
			const body = (init as { body: { content: JSONContent } }).body;
			order.push(JSON.stringify(body.content).includes('terza') ? 'terza' : 'quarta');
			return Promise.resolve({
				data: { id: `n${String(order.length)}`, pageNumber: 2 + order.length, content: {} }
			});
		});
		book.update(4, doc('quarta'));
		book.update(3, doc('terza'));
		await book.flush();
		expect(order).toEqual(['terza', 'quarta']);
	});
});

describe('turning', () => {
	it('saves, loads the next spread and moves the view', async () => {
		const { book, GET, PUT, onview } = setup(23);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('modificata'));
		const turned = await book.turn('forward');
		expect(turned).toBe(true);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(GET).toHaveBeenCalledWith('/diaries/{diaryId}/pages', {
			params: { path: { diaryId: 'd1' }, query: { page: '2' } }
		});
		expect(book.visible).toEqual([3, 4]);
		expect(book.label).toBe('Pagine 3 e 4 di 23');
		expect(onview).toHaveBeenCalledWith(2);
		expect(book.previous).toBe(true);
	});

	it('does not turn when the save fails', async () => {
		const { book, PUT, GET } = setup(23);
		PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		book.update(1, doc('modificata'));
		expect(await book.turn('forward')).toBe(false);
		expect(book.visible).toEqual([1, 2]);
		expect(GET).not.toHaveBeenCalled();
		expect(book.failure?.message).toBe('Rete assente');
	});

	it('does not reload pages it already has when going back', async () => {
		const { book, GET } = setup(23);
		await book.turn('forward');
		await book.turn('backward');
		expect(GET).toHaveBeenCalledTimes(1);
		expect(book.visible).toEqual([1, 2]);
	});

	it('does not go before the first or after the last page', async () => {
		const { book } = setup(4);
		expect(await book.turn('backward')).toBe(false);
		await book.turn('forward');
		expect(await book.turn('forward')).toBe(false);
	});

	it('goes to a page of another spread and focuses a visible one', async () => {
		const { book } = setup(23);
		await book.goTo(14);
		expect(book.visible).toEqual([13, 14]);
		await book.goTo(500);
		expect(book.visible).toEqual([23, 24]);
		await book.goTo(1);
		expect(book.visible).toEqual([1, 2]);
	});

	it('shows a single page in the narrow layout and moves one page at a time', async () => {
		const { book } = setup(23);
		book.setSingle(true);
		expect(book.visible).toEqual([1]);
		await book.turn('forward');
		expect(book.visible).toEqual([2]);
		expect(book.label).toBe('Pagina 2 di 23');
		await book.turn('forward');
		expect(book.visible).toEqual([3]);
		expect(book.spread).toBe(2);
	});

	it('loads the missing partner page when the layout turns wide', async () => {
		const { book, GET } = setup(23, {}, 1);
		book.setSingle(true);
		await book.turn('forward', 8);
		GET.mockClear();
		book.setSingle(false);
		await vi.advanceTimersByTimeAsync(0);
		expect(book.visible).toEqual([7, 8]);
		expect(book.editable(7)).toBe(true);
	});

	it('takes the view from the address without announcing it again', async () => {
		const { book, onview } = setup(23);
		await book.show(5);
		expect(book.visible).toEqual([9, 10]);
		expect(onview).not.toHaveBeenCalled();
	});
});

describe('animation', () => {
	it('waits for the end of the turn before moving the view', async () => {
		const { book } = setup(23, { animate: () => true });
		const turned = book.turn('forward');
		await vi.advanceTimersByTimeAsync(0);
		expect(book.turning?.direction).toBe('forward');
		expect(book.turning?.to.map((slot) => slot.page)).toEqual([3, 4]);
		expect(book.visible).toEqual([1, 2]);
		book.completeTurn();
		await turned;
		expect(book.turning).toBeNull();
		expect(book.visible).toEqual([3, 4]);
	});

	it('moves on by itself if the animation never reports its end', async () => {
		const { book } = setup(23, { animate: () => true });
		const turned = book.turn('forward');
		await vi.advanceTimersByTimeAsync(1500);
		await turned;
		expect(book.visible).toEqual([3, 4]);
	});
});

describe('overflow into the next page', () => {
	const rest = doc('avanzo');

	it('creates the next page when the diary ends and follows it with the view', async () => {
		const { book, POST } = setup(4, {}, 2);
		POST.mockResolvedValue({ data: { id: 'n5', pageNumber: 5, content: rest } });
		await book.spill(4, rest, 3);
		expect(book.visible).toEqual([5, 6]);
		expect(book.content(5)).toEqual(rest);
		expect(book.pending.get(5)).toEqual({ cursor: 3 });
		expect(POST).not.toHaveBeenCalled();
		await book.flush();
		expect(POST).toHaveBeenCalledTimes(1);
		expect(book.notice.message).toBeNull();
		expect(book.total).toBe(5);
	});

	it('writes into the visible right page when the left one overflows', async () => {
		const { book } = setup(3, {}, 2);
		const prepend = vi.fn<(content: JSONContent, cursor: number | null) => void>();
		book.register(4, {
			editor: {} as never,
			prepend,
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		});
		await book.spill(3, rest, null);
		expect(prepend).toHaveBeenCalledWith(rest, null);
	});

	it('merges the text before the existing content of a page that is not visible', async () => {
		const { book, PUT } = setup(23);
		PUT.mockResolvedValue({ data: { id: 'p3', pageNumber: 3, content: {} } });
		await book.spill(2, rest, 5);
		expect(book.visible).toEqual([3, 4]);
		const merged = book.content(3);
		expect(merged.content?.[0]).toEqual(rest.content?.[0]);
		expect(merged.content?.length).toBe(2);
		expect(book.notice.message).toBeNull();
	});

	it('keeps the text aside when the next page cannot be loaded', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));

		await book.spill(2, rest, 5);

		expect(book.held).toEqual([3]);
		expect(book.visible).toEqual([1, 2]);
		expect(book.state).toBe('unsaved');
		expect(book.unsaved).toBe(true);
	});

	it('adds the kept text before the page once it can be loaded', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await book.spill(2, rest, 5);

		await book.loadHeld();

		expect(book.held).toEqual([]);
		expect(book.content(3)).toEqual({
			type: 'doc',
			content: [...(rest.content ?? []), ...(doc('pagina 3').content ?? [])]
		});
		expect(book.dirty.has(3)).toBe(true);
	});

	it('adds the kept text when the connection comes back', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await book.spill(2, rest, 5);

		book.reconnect();
		await vi.advanceTimersByTimeAsync(0);

		expect(book.held).toEqual([]);
		expect(book.content(3).content?.[0]).toEqual(rest.content?.[0]);
	});

	it('adds the kept text when the user turns to that page', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await book.spill(2, rest, 5);

		await book.turn('forward');

		expect(book.held).toEqual([]);
		expect(book.content(3).content?.[0]).toEqual(rest.content?.[0]);
	});

	it('keeps two overflows in reading order', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await book.spill(2, doc('prima'), null);
		await book.spill(2, doc('dopo'), null);

		await book.loadHeld();

		expect(book.content(3).content?.map((block) => block.content?.[0]?.text)).toEqual([
			'dopo',
			'prima',
			'pagina 3'
		]);
	});

	it('keeps the text aside and tells why when it still cannot be loaded', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		GET.mockResolvedValueOnce({
			error: { title: 'Diario non trovato' },
			response: { status: 404 }
		});
		await book.spill(2, rest, 5);

		await book.loadHeld();

		expect(book.held).toEqual([3]);
		expect(book.notice.message).toBe('Diario non trovato');
	});

	it('never goes past the hundredth page', async () => {
		const { book, POST } = setup(100, {}, 50);
		await book.spill(100, rest, null);
		expect(POST).not.toHaveBeenCalled();
		expect(book.visible).toEqual([99, 100]);
	});

	it('makes the placeholder page editable when the first page overflows', async () => {
		const { book, POST } = setup(0);
		POST.mockResolvedValue({ data: { id: 'n', pageNumber: 1, content: {} } });
		book.update(1, doc('inizio'));
		await book.flush();
		POST.mockResolvedValue({ data: { id: 'n2', pageNumber: 2, content: rest } });
		await book.spill(1, rest, null);
		expect(book.editable(2)).toBe(true);
		expect(book.content(2)).toEqual(rest);
	});
});

describe('typing while the page turns', () => {
	it('keeps what is typed until the new page is ready and then hands it over once', async () => {
		const { book, POST } = setup(2);
		POST.mockResolvedValue({ data: { id: 'n3', pageNumber: 3, content: {} } });
		const rest = doc('avanzo');

		const moving = book.spill(2, rest, null);
		await vi.advanceTimersByTimeAsync(0);
		expect(book.capture('a')).toBe(true);
		expect(book.capture('\n')).toBe(true);
		await moving;

		expect(book.visible).toEqual([3, 4]);
		expect(book.takeTyped()).toEqual(['a', '\n']);
		expect(book.capture('b')).toBe(false);
		expect(book.takeTyped()).toEqual([]);
	});

	it('never starts the new page with line breaks typed while it was being prepared', async () => {
		const { book, POST } = setup(2);
		POST.mockResolvedValue({ data: { id: 'n3', pageNumber: 3, content: {} } });
		const moving = book.spill(2, doc('avanzo'), null);
		await vi.advanceTimersByTimeAsync(0);
		book.capture('\n');
		book.capture('\n');
		book.capture('a');
		book.capture('\n');
		await moving;
		expect(book.takeTyped()).toEqual(['a', '\n']);

		const other = setup(2);
		other.POST.mockResolvedValue({ data: { id: 'n3', pageNumber: 3, content: {} } });
		const again = other.book.spill(2, doc('avanzo'), null);
		await vi.advanceTimersByTimeAsync(0);
		other.book.capture('\n');
		await again;
		expect(other.book.takeTyped()).toEqual([]);
	});

	it('stops keeping keys after a few seconds if nobody collects them', async () => {
		const { book } = setup(2);
		const moving = book.spill(2, doc('avanzo'), null);
		await vi.advanceTimersByTimeAsync(0);
		await moving;
		await vi.advanceTimersByTimeAsync(3500);
		expect(book.capture('a')).toBe(false);
	});
});

describe('going back to the previous page', () => {
	it('puts the cursor at the end of the page before when it is on screen', async () => {
		const { book } = setup(4);
		const first = {
			editor: {} as never,
			prepend: vi.fn<() => void>(),
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		};
		book.register(1, first);

		expect(await book.previousPage(2)).toBe(true);
		expect(first.focusEnd).toHaveBeenCalledTimes(1);
		expect(book.focus).toBe(1);
	});

	it('turns back first when the page before is on another spread', async () => {
		const { book } = setup(4, {}, 2);
		const earlier = {
			editor: {} as never,
			prepend: vi.fn<() => void>(),
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		};
		book.register(2, earlier);

		expect(book.visible).toEqual([3, 4]);
		expect(await book.previousPage(3)).toBe(true);
		expect(book.visible).toEqual([1, 2]);
		expect(earlier.focusEnd).toHaveBeenCalledTimes(1);
	});

	it('does nothing from the first page', async () => {
		const { book } = setup(4);
		expect(await book.previousPage(1)).toBe(false);
	});

	it('hands the keys typed while it turns back to the page before, line breaks included', async () => {
		const { book } = setup(4, {}, 2);
		const earlier = {
			editor: {} as never,
			prepend: vi.fn<() => void>(),
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		};
		book.register(2, earlier);

		const moving = book.previousPage(3);
		expect(book.capture('a')).toBe(true);
		expect(book.capture('\n')).toBe(true);
		expect(book.capture('b')).toBe(true);
		await moving;

		expect(earlier.insert).toHaveBeenCalledWith(['a', '\n', 'b']);
		expect(book.capture('c')).toBe(false);
	});

	it('does not keep keys when the page before is already on screen', async () => {
		const { book } = setup(4);
		const first = {
			editor: {} as never,
			prepend: vi.fn<() => void>(),
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		};
		book.register(1, first);
		await book.previousPage(2);
		expect(first.insert).not.toHaveBeenCalled();
		expect(book.capture('a')).toBe(false);
	});
});

describe('toolbar editor', () => {
	it('follows the page that took the focus and falls back to the first writable page', () => {
		const { book } = setup(4);
		const first = {
			editor: { id: 1 } as never,
			prepend: vi.fn<() => void>(),
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		};
		const second = {
			editor: { id: 2 } as never,
			prepend: vi.fn<() => void>(),
			focus: vi.fn<() => void>(),
			focusEnd: vi.fn<() => void>(),
			insert: vi.fn<(pieces: string[]) => void>()
		};
		book.register(1, first);
		book.register(2, second);
		expect(book.toolbarEditor).toBe(first.editor);
		book.activate(2);
		expect(book.toolbarEditor).toBe(second.editor);
		book.unregister(2, second);
		expect(book.toolbarEditor).toBe(first.editor);
	});
});

async function keepTyping(book: DiaryBook, from: number, to: number): Promise<void> {
	for (let step = from; step < to; step += 1) {
		book.update(1, doc(`testo ${String(step)}`));
		await vi.advanceTimersByTimeAsync(800);
	}
}

async function retriesPerWait(PUT: Setup['PUT'], rounds: number): Promise<number[]> {
	const counts: number[] = [];
	for (let round = 0; round < rounds; round += 1) {
		const calls = PUT.mock.calls.length;
		await vi.advanceTimersByTimeAsync(RETRY_MAX_DELAY + 50);
		counts.push(PUT.mock.calls.length - calls);
	}
	return counts;
}

describe('waiting limits', () => {
	it('saves after the maximum wait even if the writer never pauses', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		await keepTyping(book, 0, 18);
		expect(PUT).not.toHaveBeenCalled();
		await keepTyping(book, 18, 26);
		expect(PUT).toHaveBeenCalled();
		expect(SAVE_MAX_WAIT).toBeGreaterThan(SAVE_DELAY);
	});

	it('waits for the pause chosen by the writer and not for the default one', async () => {
		const { book, PUT } = setup(4, { saveDelay: () => 1000 });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('nuovo'));
		await vi.advanceTimersByTimeAsync(990);
		expect(PUT).not.toHaveBeenCalled();
		await vi.advanceTimersByTimeAsync(20);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('waits longer when the writer chose a longer pause', async () => {
		const { book, PUT } = setup(4, { saveDelay: () => 8000 });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('nuovo'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).not.toHaveBeenCalled();
		await vi.advanceTimersByTimeAsync(8000 - SAVE_DELAY);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('saves at the end of the chosen pause even when it is longer than the maximum wait', async () => {
		const { book, PUT } = setup(4, { saveDelay: () => 30000 });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('a'));
		await vi.advanceTimersByTimeAsync(SAVE_MAX_WAIT + 5000);
		expect(PUT).not.toHaveBeenCalled();
		book.update(1, doc('ab'));
		await vi.advanceTimersByTimeAsync(5100);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('reads the chosen pause again at every change', async () => {
		let seconds = 1;
		const { book, PUT } = setup(4, { saveDelay: () => seconds * 1000 });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('uno'));
		await vi.advanceTimersByTimeAsync(1010);
		expect(PUT).toHaveBeenCalledTimes(1);
		seconds = 5;
		book.update(1, doc('uno due'));
		await vi.advanceTimersByTimeAsync(4990);
		expect(PUT).toHaveBeenCalledTimes(1);
		await vi.advanceTimersByTimeAsync(20);
		expect(PUT).toHaveBeenCalledTimes(2);
	});

	it('goes back to the short pause once the text is saved', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('uno'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).toHaveBeenCalledTimes(1);
		await vi.advanceTimersByTimeAsync(SAVE_MAX_WAIT);
		book.update(1, doc('uno due'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).toHaveBeenCalledTimes(2);
	});

	it('does not put when the text went back to what was saved', async () => {
		const { book, PUT } = setup(4);
		book.update(1, doc('pagina 1 e altro'));
		book.update(1, doc('pagina 1'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).not.toHaveBeenCalled();
		expect(book.state).toBe('saved');
	});
});

describe('connection problems', () => {
	const offline = (): TypeError => new TypeError('Failed to fetch');

	it('reports being offline when the request cannot reach the server', async () => {
		const { book, PUT } = setup(4);
		PUT.mockRejectedValue(offline());
		book.update(1, doc('importante'));
		await book.flush();
		expect(book.offline).toBe(true);
		expect(book.state).toBe('offline');
		expect(book.failure?.message).toBe('Non riesco a raggiungere il server.');
		expect(book.content(1)).toEqual(doc('importante'));
	});

	it('does not call a server error being offline', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({
			error: { title: 'Contenuto troppo grande' },
			response: { status: 500 }
		});
		book.update(1, doc('importante'));
		await book.flush();
		expect(book.offline).toBe(false);
		expect(book.state).toBe('unsaved');
	});

	it('waits longer after every failed attempt, up to a limit', async () => {
		const { book, PUT } = setup(4);
		PUT.mockRejectedValue(offline());
		book.update(1, doc('importante'));
		await book.flush();
		expect(PUT).toHaveBeenCalledTimes(1);
		await vi.advanceTimersByTimeAsync(RETRY_DELAY - 10);
		expect(PUT).toHaveBeenCalledTimes(1);
		await vi.advanceTimersByTimeAsync(20);
		expect(PUT).toHaveBeenCalledTimes(2);
		await vi.advanceTimersByTimeAsync(RETRY_DELAY * 2 - 100);
		expect(PUT).toHaveBeenCalledTimes(2);
		await vi.advanceTimersByTimeAsync(200);
		expect(PUT).toHaveBeenCalledTimes(3);
		expect(await retriesPerWait(PUT, 3)).toEqual([1, 1, 1]);
	});

	it('goes back to the short wait after a manual retry', async () => {
		const { book, PUT } = setup(4);
		PUT.mockRejectedValue(offline());
		book.update(1, doc('importante'));
		await book.flush();
		await vi.advanceTimersByTimeAsync(RETRY_DELAY + 10);
		await vi.advanceTimersByTimeAsync(RETRY_DELAY * 2 + 10);
		expect(PUT).toHaveBeenCalledTimes(3);
		book.retry();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(4);
		await vi.advanceTimersByTimeAsync(RETRY_DELAY + 10);
		expect(PUT).toHaveBeenCalledTimes(5);
	});

	it('retries at once when the connection comes back', async () => {
		const { book, PUT } = setup(4);
		PUT.mockRejectedValueOnce(offline());
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('importante'));
		await book.flush();
		book.reconnect();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(2);
		expect(book.offline).toBe(false);
		expect(book.state).toBe('saved');
	});

	it('ignores the connection coming back when the failure was not the network', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({
			error: { title: 'Contenuto troppo grande' },
			response: { status: 500 }
		});
		book.update(1, doc('importante'));
		await book.flush();
		book.reconnect();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(1);
	});
});

describe('a page deleted elsewhere', () => {
	const message =
		'Questa pagina è stata eliminata altrove: copia il testo se ti serve e ricarica il diario.';
	const missing = { error: { title: 'Pagina non trovata' }, response: { status: 404 } };

	async function gone(): Promise<Setup> {
		const made = setup(4);
		made.PUT.mockResolvedValue(missing);
		made.book.update(1, doc('importante'));
		await made.book.flush();
		return made;
	}

	it('reports the failure with the message and flags the page as gone', async () => {
		const { book } = await gone();

		expect(book.failure).toEqual({ page: 1, message });
		expect(book.gone).toBe(true);
		expect(book.state).toBe('unsaved');
	});

	it('does not call it being offline', async () => {
		const { book } = await gone();

		expect(book.offline).toBe(false);
	});

	it('keeps the text of the page', async () => {
		const { book } = await gone();

		expect(book.content(1)).toEqual(doc('importante'));
		expect(book.unsaved).toBe(true);
	});

	it('does not retry by itself', async () => {
		const { PUT } = await gone();

		await vi.advanceTimersByTimeAsync(RETRY_MAX_DELAY * 2);

		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('tries once more on a manual retry', async () => {
		const { book, PUT } = await gone();

		book.retry();
		await vi.advanceTimersByTimeAsync(0);

		expect(PUT).toHaveBeenCalledTimes(2);
	});

	it('is gone again when the retry meets the same answer', async () => {
		const { book } = await gone();

		book.retry();
		await vi.advanceTimersByTimeAsync(0);

		expect(book.gone).toBe(true);
	});

	it('clears the flag when a retry saves', async () => {
		const { book, PUT } = await gone();
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {}, version: 4 } });

		book.retry();
		await vi.advanceTimersByTimeAsync(0);

		expect(book.gone).toBe(false);
		expect(book.failure).toBeNull();
	});
});

describe('a page the API refuses', () => {
	const refusal = {
		error: { status: 400, title: 'Contenuto non valido' },
		response: { status: 400 }
	};

	async function refused(): Promise<Setup> {
		const made = setup(4);
		made.PUT.mockResolvedValue(refusal);
		made.book.update(1, doc('importante'));
		await made.book.flush();
		return made;
	}

	it('reports the failure with the message of the API', async () => {
		const { book } = await refused();

		expect(book.failure).toEqual({ page: 1, message: 'Contenuto non valido' });
	});

	it('flags the failure as rejected', async () => {
		const { book } = await refused();

		expect(book.rejected).toBe(true);
	});

	it('does not call it being offline or gone', async () => {
		const { book } = await refused();

		expect([book.offline, book.gone]).toEqual([false, false]);
	});

	it('keeps the text of the page', async () => {
		const { book } = await refused();

		expect(book.content(1)).toEqual(doc('importante'));
	});

	it('does not retry by itself', async () => {
		const { PUT } = await refused();

		await vi.advanceTimersByTimeAsync(RETRY_MAX_DELAY * 2);

		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('tries once more on a manual retry', async () => {
		const { book, PUT } = await refused();

		book.retry();
		await vi.advanceTimersByTimeAsync(0);

		expect(PUT).toHaveBeenCalledTimes(2);
	});

	it('clears the flag when a retry saves', async () => {
		const { book, PUT } = await refused();
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {}, version: 4 } });

		book.retry();
		await vi.advanceTimersByTimeAsync(0);

		expect(book.rejected).toBe(false);
	});

	it('stays rejected when the retry meets the same answer', async () => {
		const { book } = await refused();

		book.retry();
		await vi.advanceTimersByTimeAsync(0);

		expect(book.rejected).toBe(true);
	});

	it('retries by itself on a server error and is not rejected', async () => {
		const made = setup(4);
		made.PUT.mockResolvedValue({ error: { title: 'Errore' }, response: { status: 500 } });
		made.book.update(1, doc('importante'));
		await made.book.flush();

		await vi.advanceTimersByTimeAsync(RETRY_DELAY + 50);

		expect(made.PUT).toHaveBeenCalledTimes(2);
	});

	it.each([408, 429])('retries by itself on a %i answer', async (status) => {
		const made = setup(4);
		made.PUT.mockResolvedValue({ error: {}, response: { status } });
		made.book.update(1, doc('importante'));
		await made.book.flush();

		await vi.advanceTimersByTimeAsync(RETRY_DELAY + 50);

		expect(made.PUT).toHaveBeenCalledTimes(2);
	});

	it('is not rejected after a server error', async () => {
		const made = setup(4);
		made.PUT.mockResolvedValue({ error: { title: 'Errore' }, response: { status: 500 } });
		made.book.update(1, doc('importante'));
		await made.book.flush();

		expect(made.book.rejected).toBe(false);
	});
});

describe('dispose', () => {
	it('never announces a view after dispose during a turn', async () => {
		const { book, onview } = setup(23, { animate: () => true });
		const turned = book.turn('forward');
		await vi.advanceTimersByTimeAsync(0);

		book.dispose();
		await turned;
		await vi.advanceTimersByTimeAsync(2000);

		expect(onview).not.toHaveBeenCalled();
	});

	it('ends a turn in progress when disposed', async () => {
		const { book } = setup(23, { animate: () => true });
		const turned = book.turn('forward');
		await vi.advanceTimersByTimeAsync(0);

		book.dispose();

		expect(await turned).toBe(false);
	});

	it('never announces a view after dispose while pages are loading', async () => {
		const { book, onview, GET } = setup(23);
		let release: () => void = () => undefined;
		GET.mockImplementation(
			() =>
				new Promise((resolve) => {
					release = () => {
						resolve({ data: { list: [], totalRows: 23 } });
					};
				})
		);
		const turned = book.turn('forward');
		await vi.advanceTimersByTimeAsync(0);

		book.dispose();
		release();
		await turned;

		expect(onview).not.toHaveBeenCalled();
	});

	it('stops the pending save', async () => {
		const { book, PUT } = setup(4);
		book.update(1, doc('nuovo'));

		book.dispose();
		await vi.advanceTimersByTimeAsync(SAVE_DELAY * 2);

		expect(PUT).not.toHaveBeenCalled();
	});

	it('stops the draft write', async () => {
		const { book, drafts } = setup(4);
		book.update(1, doc('nuovo'));

		book.dispose();
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY * 2);

		expect(await drafts.list('d1')).toEqual([]);
	});

	it('leaves no timer running, the key capture included', async () => {
		const { book } = setup(2);
		const moving = book.spill(2, doc('avanzo'), null);
		await vi.advanceTimersByTimeAsync(0);
		await moving;

		book.dispose();

		expect(vi.getTimerCount()).toBe(0);
	});
});

describe('going to a page', () => {
	it('answers false for a number that is not finite', async () => {
		const { book } = setup(23);

		expect(await book.goTo(Number.NaN)).toBe(false);
		expect(await book.goTo(Number.POSITIVE_INFINITY)).toBe(false);
	});

	it('loads nothing for a number that is not finite', async () => {
		const { book, GET } = setup(23);

		await book.goTo(Number.NaN);
		await book.goTo(Number.POSITIVE_INFINITY);

		expect(GET).not.toHaveBeenCalled();
	});
});

describe('local drafts', () => {
	const page1 = { diaryId: 'd1', page: 1, pageId: 'p1', baseVersion: 3, savedAt: 1 };

	it('keeps a copy of the unsaved text after a short pause', async () => {
		const { book, drafts } = setup(4, { autoSave: () => false });
		book.update(1, doc('nuovo'));
		expect(await drafts.list('d1')).toEqual([]);
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY + 10);
		expect(await drafts.list('d1')).toEqual([
			expect.objectContaining({ page: 1, pageId: 'p1', content: doc('nuovo') })
		]);
	});

	it('writes the copy at once when the book is flushed and the save fails', async () => {
		const { book, drafts, PUT } = setup(4);
		PUT.mockRejectedValue(new TypeError('Failed to fetch'));
		book.update(1, doc('importante'));
		await book.flush();
		expect(await drafts.list('d1')).toEqual([
			expect.objectContaining({ page: 1, content: doc('importante') })
		]);
	});

	it('removes the copy once the text is saved', async () => {
		const { book, drafts, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('nuovo'));
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY + 10);
		expect(await drafts.list('d1')).toHaveLength(1);
		await vi.advanceTimersByTimeAsync(SAVE_DELAY);
		expect(book.state).toBe('saved');
		expect(await drafts.list('d1')).toEqual([]);
	});

	it('shows the page of the server and drops a copy of a page that exists there', async () => {
		const { book, drafts, PUT } = setup(4);
		await drafts.put({ ...page1, content: doc('scritto prima') });
		book.restore(await drafts.list('d1'));
		expect(book.content(1)).toEqual(doc('pagina 1'));
		expect(book.notice.message).toBeNull();
		expect(book.dirty.has(1)).toBe(false);
		expect(await drafts.list('d1')).toEqual([]);
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('does not put a copy written on an older version over the page of the server', async () => {
		const { book, drafts, PUT } = setup(4);
		await drafts.put({ ...page1, baseVersion: 2, content: doc('scritto prima') });
		book.restore(await drafts.list('d1'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(book.content(1)).toEqual(doc('pagina 1'));
		expect(book.state).toBe('saved');
		expect(PUT).not.toHaveBeenCalled();
	});

	it('drops a copy equal to the page on the server', async () => {
		const { book, drafts, PUT } = setup(4);
		await drafts.put({ ...page1, content: doc('pagina 1') });
		book.restore(await drafts.list('d1'));
		expect(book.notice.message).toBeNull();
		expect(await drafts.list('d1')).toEqual([]);
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('drops a draft whose page moved to another number and shows the server page', async () => {
		const { book, drafts, PUT } = setup(4);
		await drafts.put({ ...page1, page: 4, pageId: 'p2', content: doc('scritto prima') });
		book.restore(await drafts.list('d1'));
		expect(book.content(2)).toEqual(doc('pagina 2'));
		expect(book.dirty.has(2)).toBe(false);
		expect(await drafts.list('d1')).toEqual([]);
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('finds the new number of a draft page in an earlier spread', async () => {
		const { book, drafts, GET } = setup(6, { autoSave: () => false }, 3);
		await drafts.put({ ...page1, page: 9, pageId: 'p2', content: doc('scritto prima') });
		book.restore(await drafts.list('d1'));
		await vi.advanceTimersByTimeAsync(10);
		expect(GET).toHaveBeenCalled();
		expect(book.content(2)).toEqual(doc('pagina 2'));
		expect(book.dirty.has(2)).toBe(false);
	});

	it('moves the text of a page that no longer exists into a new last page', async () => {
		const { book, drafts } = setup(4, { autoSave: () => false });
		await drafts.put({ ...page1, pageId: 'altra', content: doc('scritto prima') });
		book.restore(await drafts.list('d1'));
		await vi.advanceTimersByTimeAsync(10);
		expect(book.content(5)).toEqual(doc('scritto prima'));
		expect(book.dirty.has(5)).toBe(true);
		expect(book.notice.failed).toBe(true);
		expect(book.notice.message).toContain('non esiste più');
	});

	it('does not touch a page the writer already changed', async () => {
		const { book, drafts } = setup(4, { autoSave: () => false });
		book.update(1, doc('adesso'));
		await drafts.put({ ...page1, content: doc('scritto prima') });
		book.restore(await drafts.list('d1'));
		expect(book.content(1)).toEqual(doc('adesso'));
		expect(book.notice.message).toBeNull();
	});

	it('waits for the spread of a page that is not loaded yet', async () => {
		const { book, drafts } = setup(23, { autoSave: () => false });
		await drafts.put({
			diaryId: 'd1',
			page: 5,
			pageId: 'p5',
			baseVersion: 3,
			content: doc('bozza'),
			savedAt: 1
		});
		book.restore(await drafts.list('d1'));
		expect(book.notice.message).toBeNull();
		expect(await drafts.list('d1')).toHaveLength(1);
		await book.goTo(5);
		expect(book.content(5)).toEqual(doc('pagina 5'));
		expect(book.notice.message).toBeNull();
		expect(await drafts.list('d1')).toEqual([]);
	});

	it('brings back the first words of a page that was never created', async () => {
		const { book, drafts, POST } = setup(4);
		POST.mockResolvedValue({ data: { id: 'n5', pageNumber: 5, content: {} } });
		await drafts.put({
			diaryId: 'd1',
			page: 5,
			pageId: null,
			baseVersion: 0,
			content: doc('inizio'),
			savedAt: 1
		});
		book.restore(await drafts.list('d1'));
		expect(book.content(5)).toEqual(doc('inizio'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(POST).toHaveBeenCalledTimes(1);
	});

	it('drops a copy of a new page that cannot be the next one', async () => {
		const { book, drafts } = setup(4);
		await drafts.put({
			diaryId: 'd1',
			page: 9,
			pageId: null,
			baseVersion: 0,
			content: doc('inizio'),
			savedAt: 1
		});
		book.restore(await drafts.list('d1'));
		expect(book.content(9)).toEqual(emptyDoc());
		expect(await drafts.list('d1')).toEqual([]);
	});

	it('keeps saving when the draft store fails', async () => {
		const broken: DraftStore = {
			list: () => Promise.reject(new Error('Archivio non disponibile')),
			put: () => Promise.reject(new Error('Archivio non disponibile')),
			remove: () => Promise.reject(new Error('Archivio non disponibile'))
		};
		const { book, PUT } = setup(4, { drafts: broken });
		PUT.mockResolvedValue({ data: { id: 'p1', pageNumber: 1, content: {} } });
		book.update(1, doc('nuovo'));
		await book.flush();
		expect(book.state).toBe('saved');
	});
});

describe('versions and conflicts', () => {
	const conflict = { error: { title: 'CONFLICT', status: 409 }, response: { status: 409 } };

	function fresh(version: number, text: string): unknown {
		return {
			data: { list: [{ id: 'p1', pageNumber: 1, content: doc(text), version }], totalRows: 4 }
		};
	}

	function versionsSent(put: ReturnType<typeof vi.fn<Call>>): number[] {
		return put.mock.calls.map((call) => (call[1] as { body: { version: number } }).body.version);
	}

	it('sends the version of the page and, the next time, the one that came back', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValueOnce({ data: { id: 'p1', pageNumber: 1, content: {}, version: 4 } });
		PUT.mockResolvedValueOnce({ data: { id: 'p1', pageNumber: 1, content: {}, version: 5 } });
		book.update(1, doc('uno'));
		await book.flush();
		book.update(1, doc('uno due'));
		await book.flush();
		expect(versionsSent(PUT)).toEqual([3, 4]);
	});

	it('stops saving on a conflict, keeps the text and does not retry by itself', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue(conflict);
		book.update(1, doc('mio'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY + 10);
		expect(book.state).toBe('conflict');
		expect(book.conflict).toEqual({ page: 1 });
		expect(book.unsaved).toBe(true);
		expect(book.content(1)).toEqual(doc('mio'));
		await vi.advanceTimersByTimeAsync(RETRY_MAX_DELAY * 2);
		book.update(1, doc('mio ancora'));
		await vi.advanceTimersByTimeAsync(SAVE_DELAY * 2);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(await book.flush()).toBe(false);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('keeps my version by saving it over the version now on the server', async () => {
		const { book, PUT, GET } = setup(4);
		PUT.mockResolvedValueOnce(conflict);
		book.update(1, doc('mio'));
		await book.flush();
		GET.mockResolvedValueOnce(fresh(7, 'altrove'));
		PUT.mockResolvedValueOnce({ data: { id: 'p1', pageNumber: 1, content: {}, version: 8 } });
		expect(await book.resolveConflict('mine')).toBe(true);
		expect(PUT).toHaveBeenLastCalledWith('/pages/{id}', {
			params: { path: { id: 'p1' } },
			body: { content: doc('mio'), version: 7 }
		});
		expect(book.conflict).toBeNull();
		expect(book.state).toBe('saved');
		expect(book.content(1)).toEqual(doc('mio'));
	});

	it('takes the saved version and drops my text, the copy included', async () => {
		const { book, PUT, GET, drafts } = setup(4);
		const setContent = vi.fn();
		book.register(1, {
			editor: { commands: { setContent } } as never,
			prepend: vi.fn(),
			focus: vi.fn(),
			focusEnd: vi.fn(),
			insert: vi.fn()
		});
		PUT.mockResolvedValueOnce(conflict);
		book.update(1, doc('mio'));
		await book.flush();
		GET.mockResolvedValueOnce(fresh(7, 'altrove'));
		expect(await book.resolveConflict('theirs')).toBe(true);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(book.content(1)).toEqual(doc('altrove'));
		expect(setContent).toHaveBeenCalledWith(doc('altrove'), { emitUpdate: false });
		expect(book.conflict).toBeNull();
		expect(book.state).toBe('saved');
		expect(await drafts.list('d1')).toEqual([]);
	});

	it('turns a conflict into a missing page when the page is gone, keeping the text', async () => {
		const { book, PUT, GET } = setup(4);
		PUT.mockResolvedValueOnce(conflict);
		book.update(1, doc('mio'));
		await book.flush();
		GET.mockResolvedValueOnce({ data: { list: [], totalRows: 3 } });
		expect(await book.resolveConflict('mine')).toBe(false);
		expect(book.conflict).toBeNull();
		expect(book.gone).toBe(true);
		expect(book.failure?.message).toContain('eliminata altrove');
		expect(book.content(1)).toEqual(doc('mio'));
	});

	it('resolves a conflict when the page was renumbered elsewhere', async () => {
		const { book, PUT, GET } = setup(4);
		PUT.mockResolvedValueOnce(conflict);
		book.update(2, doc('mio'));
		await book.flush();
		GET.mockResolvedValue({
			data: {
				list: [
					{ id: 'p2', pageNumber: 1, content: doc('altrove'), version: 7 },
					{ id: 'p3', pageNumber: 2, content: doc('pagina 3'), version: 4 }
				],
				totalRows: 3
			}
		});
		PUT.mockResolvedValueOnce({ data: { id: 'p2', pageNumber: 1, content: {}, version: 8 } });
		expect(await book.resolveConflict('mine')).toBe(true);
		expect(PUT).toHaveBeenLastCalledWith('/pages/{id}', {
			params: { path: { id: 'p2' } },
			body: { content: doc('mio'), version: 7 }
		});
		expect(book.content(1)).toEqual(doc('mio'));
		expect(book.state).toBe('saved');
	});

	it('takes the saved version of a renumbered page', async () => {
		const { book, PUT, GET } = setup(4);
		PUT.mockResolvedValueOnce(conflict);
		book.update(2, doc('mio'));
		await book.flush();
		GET.mockResolvedValue({
			data: {
				list: [
					{ id: 'p2', pageNumber: 1, content: doc('altrove'), version: 7 },
					{ id: 'p3', pageNumber: 2, content: doc('pagina 3'), version: 4 }
				],
				totalRows: 3
			}
		});
		expect(await book.resolveConflict('theirs')).toBe(true);
		expect(book.content(1)).toEqual(doc('altrove'));
		expect(book.state).toBe('saved');
	});

	it('keeps the conflict when keeping my version ends in a second conflict', async () => {
		const { book, PUT, GET } = setup(4);
		PUT.mockResolvedValue(conflict);
		book.update(1, doc('mio'));
		await book.flush();
		GET.mockResolvedValueOnce(fresh(7, 'altrove'));

		const resolved = await book.resolveConflict('mine');

		expect(resolved).toBe(false);
		expect(book.conflict).toEqual({ page: 1 });
		expect(book.content(1)).toEqual(doc('mio'));
	});

	it('keeps the conflict open when the saved version cannot be read', async () => {
		const { book, PUT, GET } = setup(4);
		PUT.mockResolvedValueOnce(conflict);
		book.update(1, doc('mio'));
		await book.flush();
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		expect(await book.resolveConflict('theirs')).toBe(false);
		expect(book.conflict).toEqual({ page: 1 });
		expect(book.content(1)).toEqual(doc('mio'));
	});

	it('writes the version it started from in the local copy', async () => {
		const { book, drafts } = setup(4);
		book.update(1, doc('mio'));
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY + 10);
		expect(await drafts.list('d1')).toEqual([expect.objectContaining({ page: 1, baseVersion: 3 })]);
	});
});

describe('writing after a page that is not full', () => {
	function withBlankSecond(): DiaryBook {
		const { book } = setup(2);
		book.pages.set(2, { id: 'p2', content: emptyDoc(), version: 3 });
		return book;
	}

	it('keeps a blank page closed until the page before is full', () => {
		const book = withBlankSecond();
		book.markFull(1, false);
		expect(book.editable(1)).toBe(true);
		expect(book.editable(2)).toBe(false);

		book.markFull(1, true);
		expect(book.editable(2)).toBe(true);

		book.markFull(1, false);
		expect(book.editable(2)).toBe(false);
	});

	it('keeps a page that already has text open, and does not close what was never measured', () => {
		const { book } = setup(4);
		book.markFull(1, false);
		expect(book.editable(2)).toBe(true);
		const other = withBlankSecond();
		expect(other.editable(2)).toBe(true);
	});
});

describe('the next page when the last one is full', () => {
	it('is ready to write on as soon as the last page is full, without being saved', () => {
		const { book, POST } = setup(2);
		expect(book.editable(3)).toBe(false);
		expect(book.next).toBe(false);

		book.markFull(2, true);
		expect(book.ahead).toBe(true);
		expect(book.editable(3)).toBe(true);
		expect(book.editable(4)).toBe(false);
		expect(book.next).toBe(true);
		expect(POST).not.toHaveBeenCalled();

		book.markFull(2, false);
		expect(book.ahead).toBe(false);
		expect(book.editable(3)).toBe(false);
		expect(book.next).toBe(false);
	});

	it('turns to the page that does not exist yet and lets you write there', async () => {
		const { book } = setup(2);
		book.markFull(2, true);
		expect(await book.turn('forward')).toBe(true);
		expect(book.visible).toEqual([3, 4]);
		expect(book.editable(3)).toBe(true);
	});

	it('does not go past the last page of the diary', () => {
		const { book } = setup(MAX_PAGES);
		book.markFull(MAX_PAGES, true);
		expect(book.ahead).toBe(false);
		expect(book.editable(MAX_PAGES + 1)).toBe(false);
	});
});

describe('blank pages', () => {
	interface Server {
		book: DiaryBook;
		DELETE: ReturnType<typeof vi.fn<Call>>;
		PUT: ReturnType<typeof vi.fn<Call>>;
		ids: () => string[];
		whileDeleting: (action: () => void) => void;
	}

	function blankSetup(contents: JSONContent[], failing = false): Server {
		const list = contents.map((content, index) => ({ id: `p${String(index + 1)}`, content }));
		const view = (spread: number): SpreadData => {
			const pages = list
				.map((item, index) => ({
					id: item.id,
					pageNumber: index + 1,
					content: item.content,
					version: 3,
					tag: null
				}))
				.filter((page) => page.pageNumber === 2 * spread - 1 || page.pageNumber === 2 * spread);
			return { pages, totalPages: list.length };
		};
		const GET = vi.fn<Call>(async (_path: unknown, init: unknown) => {
			const page = Number((init as { params: { query: { page: string } } }).params.query.page);
			const data = view(page);
			return Promise.resolve({ data: { list: data.pages, totalRows: data.totalPages } });
		});
		let during: (() => void) | undefined;
		const DELETE = vi.fn<Call>(async (_path: unknown, init: unknown) => {
			during?.();
			during = undefined;
			if (failing) {
				return Promise.resolve({ error: { status: 500 }, response: { ok: false, status: 500 } });
			}
			const id = (init as { params: { path: { id: string } } }).params.path.id;
			list.splice(
				list.findIndex((item) => item.id === id),
				1
			);
			return Promise.resolve({ response: { ok: true } });
		});
		const PUT = vi.fn<Call>();
		const api = { GET, POST: vi.fn<Call>(), PUT, DELETE } as unknown as ApiClient;
		const book = new DiaryBook(api, 'd1', view(1), 1, {
			autoSave: () => true,
			drafts: createMemoryDraftStore(),
			animate: () => false,
			onview: vi.fn<(spread: number) => void>()
		});
		return {
			book,
			DELETE,
			PUT,
			ids: () => list.map((item) => item.id),
			whileDeleting: (action) => {
				during = action;
			}
		};
	}

	const spaces: JSONContent = {
		type: 'doc',
		content: [
			{ type: 'paragraph', content: [{ type: 'text', text: '   ' }] },
			{ type: 'paragraph' }
		]
	};

	it('sees a page with only spaces and empty lines as blank', () => {
		expect(hasText(spaces)).toBe(false);
		expect(hasText(emptyDoc())).toBe(false);
		expect(hasText(doc(' ciao '))).toBe(true);
	});

	it('deletes the blank page when the writer leaves the view and the next pages slide back', async () => {
		const { book, DELETE, ids } = blankSetup([doc('uno'), spaces, doc('tre'), doc('quattro')]);

		expect(await book.turn('forward')).toBe(true);

		expect(DELETE).toHaveBeenCalledTimes(1);
		expect(DELETE).toHaveBeenCalledWith('/pages/{id}', { params: { path: { id: 'p2' } } });
		expect(ids()).toEqual(['p1', 'p3', 'p4']);
		expect(book.total).toBe(3);
		expect(book.visible).toEqual([1, 2]);
		expect(book.content(1)).toEqual(doc('uno'));
		expect(book.content(2)).toEqual(doc('tre'));
		expect(book.label).toBe('Pagine 1 e 2 di 3');
		expect(book.generation).toBe(1);
		expect(book.notice.message).toBeNull();
	});

	it('keeps landing on the right pages when the blank page is before the destination', async () => {
		const { book, ids } = blankSetup([
			doc('uno'),
			spaces,
			doc('tre'),
			doc('quattro'),
			doc('cinque')
		]);

		expect(await book.turn('forward')).toBe(true);
		expect(book.visible).toEqual([1, 2]);
		expect(ids()).toEqual(['p1', 'p3', 'p4', 'p5']);

		expect(await book.turn('forward')).toBe(true);
		expect(book.visible).toEqual([3, 4]);
		expect(book.content(3)).toEqual(doc('quattro'));
		expect(book.content(4)).toEqual(doc('cinque'));
	});

	it('does not delete anything when every page has text', async () => {
		const { book, DELETE } = blankSetup([doc('uno'), doc('due'), doc('tre')]);
		await book.turn('forward');
		expect(DELETE).not.toHaveBeenCalled();
		expect(book.generation).toBe(0);
	});

	it('deletes blank pages when leaving the screen', async () => {
		const { book, ids } = blankSetup([doc('uno'), spaces]);
		await book.leave();
		expect(ids()).toEqual(['p1']);
	});

	it('keeps the pages and says so when the server refuses the deletion', async () => {
		const { book, ids } = blankSetup([doc('uno'), spaces, doc('tre'), doc('quattro')], true);

		await book.turn('forward');

		expect(ids()).toEqual(['p1', 'p2', 'p3', 'p4']);
		expect(book.total).toBe(4);
		expect(book.notice.message).toBe('Non riesco a eliminare la pagina.');
	});

	it('deletes one page on request and the next pages slide back', async () => {
		const { book, DELETE, ids } = blankSetup([doc('uno'), doc('due'), doc('tre'), doc('quattro')]);

		expect(book.deletable(2)).toBe(true);
		await book.removePage(2);

		expect(DELETE).toHaveBeenCalledWith('/pages/{id}', { params: { path: { id: 'p2' } } });
		expect(ids()).toEqual(['p1', 'p3', 'p4']);
		expect(book.total).toBe(3);
		expect(book.visible).toEqual([1, 2]);
		expect(book.content(1)).toEqual(doc('uno'));
		expect(book.content(2)).toEqual(doc('tre'));
		expect(book.generation).toBe(1);
		expect(book.notice.message).toBeNull();
	});

	it('lands on the last pages when the deleted one was the last of the diary', async () => {
		const { book, ids } = blankSetup([doc('uno'), doc('due'), doc('tre')]);
		await book.turn('forward');
		expect(book.visible).toEqual([3, 4]);

		await book.removePage(3);

		expect(ids()).toEqual(['p1', 'p2']);
		expect(book.total).toBe(2);
		expect(book.visible).toEqual([1, 2]);
		expect(book.content(2)).toEqual(doc('due'));
	});

	it('empties the diary when its only page is deleted', async () => {
		const { book, ids } = blankSetup([doc('uno')]);

		await book.removePage(1);

		expect(ids()).toEqual([]);
		expect(book.total).toBe(0);
		expect(book.last).toBe(0);
		expect(book.visible).toEqual([1, 2]);
		expect(book.editable(1)).toBe(true);
	});

	it('keeps every page and says so when the server refuses a requested deletion', async () => {
		const { book, ids } = blankSetup([doc('uno'), doc('due')], true);

		await expect(book.removePage(2)).rejects.toThrow('Non riesco a eliminare la pagina.');

		expect(ids()).toEqual(['p1', 'p2']);
		expect(book.total).toBe(2);
		expect(book.content(2)).toEqual(doc('due'));
		expect(book.notice.message).toBeNull();
		expect(book.generation).toBe(0);
	});

	it('does not offer deletion for a page that does not exist or is empty and unsaved', () => {
		const { book } = blankSetup([doc('uno')]);

		expect(book.deletable(1)).toBe(true);
		expect(book.deletable(2)).toBe(false);
		expect(book.deletable(5)).toBe(false);
	});

	it('keeps the text typed on a later page while a deletion is running', async () => {
		const { book, whileDeleting } = blankSetup([doc('uno'), doc('due'), doc('tre')]);
		whileDeleting(() => {
			book.update(2, doc('nuovo'));
		});

		await book.removePage(1);

		expect(book.content(1)).toEqual(doc('nuovo'));
		expect(book.dirty.has(1)).toBe(true);
	});

	it('saves a page moved by a deletion with the version the server gave it', async () => {
		const { book, PUT, whileDeleting } = blankSetup([doc('uno'), doc('due'), doc('tre')]);
		PUT.mockResolvedValue({ data: { id: 'p2', pageNumber: 1, content: doc('nuovo'), version: 5 } });
		whileDeleting(() => {
			book.update(2, doc('nuovo'));
		});
		await book.removePage(1);

		await book.flush();

		expect(PUT).toHaveBeenCalledWith('/pages/{id}', {
			params: { path: { id: 'p2' } },
			body: { content: doc('nuovo'), version: 4 }
		});
	});

	it('drops the text typed on the page that is being deleted', async () => {
		const { book, whileDeleting } = blankSetup([doc('uno'), doc('due'), doc('tre')]);
		whileDeleting(() => {
			book.update(1, doc('ultimo ritocco'));
		});

		await book.removePage(1);

		expect(book.content(1)).toEqual(doc('due'));
		expect(book.dirty.size).toBe(0);
	});

	it('says it is working while a deletion runs and not after', async () => {
		const { book, whileDeleting } = blankSetup([doc('uno'), doc('due')]);
		let during = false;
		whileDeleting(() => {
			during = book.working;
		});

		await book.removePage(2);

		expect(during).toBe(true);
		expect(book.working).toBe(false);
	});

	it('drops a page from the view when it was already deleted elsewhere', async () => {
		const { book, DELETE } = blankSetup([doc('uno'), doc('due')]);
		DELETE.mockResolvedValueOnce({
			error: { status: 404, title: 'Pagina non trovata' },
			response: { ok: false, status: 404 }
		});

		await book.removePage(2);

		expect(book.total).toBe(1);
		expect(book.notice.message).toBeNull();
	});

	it('deletes every blank page of the spread in one go and renumbers the rest', async () => {
		const { book, DELETE, ids } = blankSetup([
			spaces,
			spaces,
			doc('tre'),
			doc('quattro'),
			doc('cinque')
		]);

		await book.turn('forward');

		expect(DELETE).toHaveBeenCalledTimes(2);
		expect(ids()).toEqual(['p3', 'p4', 'p5']);
		expect(book.total).toBe(3);
		expect(book.generation).toBe(1);
	});

	it('stays on the first spread when the blank pages were the ones turned away from', async () => {
		const { book } = blankSetup([spaces, spaces, doc('tre'), doc('quattro'), doc('cinque')]);

		await book.turn('forward');

		expect(book.visible).toEqual([1, 2]);
		expect(book.content(1)).toEqual(doc('tre'));
		expect(book.content(2)).toEqual(doc('quattro'));
	});

	it('lands on the right spread after pruning several blank pages and turning again', async () => {
		const { book } = blankSetup([
			spaces,
			spaces,
			doc('tre'),
			doc('quattro'),
			doc('cinque'),
			doc('sei'),
			doc('sette')
		]);
		await book.turn('forward');

		await book.turn('forward');

		expect(book.visible).toEqual([3, 4]);
		expect(book.content(3)).toEqual(doc('cinque'));
		expect(book.content(4)).toEqual(doc('sei'));
	});

	it('refuses a deletion while another operation is running', async () => {
		const { book } = blankSetup([doc('uno'), doc('due'), doc('tre'), doc('quattro')]);
		const running = book.removePage(2);

		await expect(book.removePage(1)).rejects.toThrow('Aspetta un momento e riprova.');

		await running;
	});

	it('does not delete when the pending text cannot be saved first', async () => {
		const { book, PUT, DELETE } = setup(4);
		PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		book.update(1, doc('importante'));

		await expect(book.removePage(2)).rejects.toThrow(
			'Prima devo salvare le modifiche, ma non ci riesco. Riprova tra poco.'
		);

		expect(DELETE).not.toHaveBeenCalled();
	});

	it('keeps the pending text when it cannot be saved before a deletion', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		book.update(1, doc('importante'));

		await book.removePage(2).catch(() => undefined);

		expect(book.content(1)).toEqual(doc('importante'));
	});

	it('is usable again after a failed deletion', async () => {
		const { book } = blankSetup([doc('uno'), doc('due'), doc('tre'), doc('quattro')], true);
		await book.removePage(2).catch(() => undefined);

		const turned = await book.turn('forward');

		expect(turned).toBe(true);
		expect(book.visible).toEqual([3, 4]);
	});

	it('moves the text kept for a page one page back when an earlier page is deleted', async () => {
		const { book, GET, DELETE } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await book.spill(2, doc('avanzo'), null);
		DELETE.mockResolvedValue({ response: { ok: true } });

		await book.removePage(1);

		expect(book.content(2).content?.[0]).toEqual(doc('avanzo').content?.[0]);
		expect(book.dirty.has(2)).toBe(true);
		expect(book.dirty.has(3)).toBe(false);
	});

	it('keeps two held overflows in reading order when an earlier page is deleted', async () => {
		const { book, GET, DELETE } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await book.spill(2, doc('prima'), null);
		await book.spill(2, doc('dopo'), null);
		DELETE.mockResolvedValue({ response: { ok: true } });

		await book.removePage(1);

		expect(book.content(2).content?.map((block) => block.content?.[0]?.text)).toEqual([
			'dopo',
			'prima',
			'pagina 2'
		]);
	});
});

describe('spread loading problems', () => {
	const refused = { error: { title: 'Diario non trovato' }, response: { status: 404 } };

	it('shows the message of the API and stays on the same pages when the next spread cannot be loaded', async () => {
		const { book, GET } = setup(23);
		GET.mockResolvedValueOnce(refused);

		const turned = await book.turn('forward');

		expect(turned).toBe(false);
		expect(book.notice.message).toBe('Diario non trovato');
		expect(book.visible).toEqual([1, 2]);
	});

	it('does not call a failed load unsaved text', async () => {
		const { book, GET } = setup(23);
		GET.mockResolvedValueOnce(refused);

		await book.turn('forward');

		expect(book.failure).toBeNull();
		expect(book.unsaved).toBe(false);
		expect(book.state).toBe('saved');
	});

	it('does not announce a new view when the next spread cannot be loaded', async () => {
		const { book, GET, onview } = setup(23);
		GET.mockResolvedValueOnce(refused);

		await book.turn('forward');

		expect(onview).not.toHaveBeenCalled();
	});

	it('shows the message of the API and stays on the same pages when a spread chosen from the address cannot be loaded', async () => {
		const { book, GET } = setup(23);
		GET.mockResolvedValueOnce(refused);

		await book.show(5);

		expect(book.notice.message).toBe('Diario non trovato');
		expect(book.visible).toEqual([1, 2]);
	});

	it('says the server cannot be reached when the spread load fails for the network', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));

		await book.turn('forward');

		expect(book.notice.message).toBe(
			'Non riesco a raggiungere il server: controlla la connessione e riprova.'
		);
		expect(book.notice.failed).toBe(true);
	});

	it('stays on the same pages when the spread load fails for the network', async () => {
		const { book, GET } = setup(23);
		GET.mockRejectedValueOnce(new TypeError('Failed to fetch'));

		const turned = await book.turn('forward');

		expect(turned).toBe(false);
		expect(book.visible).toEqual([1, 2]);
	});

	it('turns once the spread can be loaded again', async () => {
		const { book, GET } = setup(23);
		GET.mockResolvedValueOnce(refused);
		await book.turn('forward');

		const turned = await book.turn('forward');

		expect(turned).toBe(true);
		expect(book.visible).toEqual([3, 4]);
	});
});

describe('connection problems when creating a page', () => {
	const offline = (): TypeError => new TypeError('Failed to fetch');
	const created = { data: { id: 'n1', pageNumber: 1, content: {} } };

	it('keeps the text of a new page and reports being offline when it cannot be created', async () => {
		const { book, POST } = setup(0);
		POST.mockRejectedValue(offline());
		book.update(1, doc('inizio'));

		await book.flush();

		expect(book.state).toBe('offline');
		expect(book.content(1)).toEqual(doc('inizio'));
	});

	it('creates the page when the connection comes back', async () => {
		const { book, POST } = setup(0);
		POST.mockRejectedValueOnce(offline());
		POST.mockResolvedValue(created);
		book.update(1, doc('inizio'));
		await book.flush();

		book.reconnect();
		await vi.advanceTimersByTimeAsync(0);

		expect(book.state).toBe('saved');
		expect(book.total).toBe(1);
	});

	it('does not send the page a second time once it has been created', async () => {
		const { book, POST, PUT } = setup(0);
		POST.mockRejectedValueOnce(offline());
		POST.mockResolvedValue(created);
		book.update(1, doc('inizio'));
		await book.flush();
		book.reconnect();
		await vi.advanceTimersByTimeAsync(0);

		await vi.advanceTimersByTimeAsync(RETRY_MAX_DELAY * 2);

		expect(POST).toHaveBeenCalledTimes(2);
		expect(PUT).not.toHaveBeenCalled();
	});
});

describe('discarding the book', () => {
	it('drops the unsaved text, its local draft and the save that was waiting', async () => {
		const { book, PUT, drafts } = setup(4);
		book.update(1, doc('nuovo'));
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY + 10);
		expect(await drafts.list('d1')).toHaveLength(1);

		book.discard();
		await vi.advanceTimersByTimeAsync(SAVE_MAX_WAIT);

		expect(PUT).not.toHaveBeenCalled();
		expect(await drafts.list('d1')).toEqual([]);
		expect(book.state).toBe('saved');
		expect(book.unsaved).toBe(false);
	});

	it('forgets the drafts found when the diary was opened', async () => {
		const { book, drafts } = setup(4);
		await drafts.put(draftFixture({ page: 5, pageId: null, baseVersion: 0 }));
		book.restore(await drafts.list('d1'));
		expect(book.dirty.has(5)).toBe(true);

		book.discard();

		expect(await drafts.list('d1')).toEqual([]);
	});

	it('clears a save failure so that nothing blocks leaving the screen', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		book.update(1, doc('importante'));
		await book.flush();
		expect(book.failure).not.toBeNull();

		book.discard();
		await vi.advanceTimersByTimeAsync(RETRY_MAX_DELAY * 2);

		expect(book.failure).toBeNull();
		expect(book.unsaved).toBe(false);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('does not show a failure when a save that was already running ends badly', async () => {
		const { book, PUT } = setup(4);
		let refuse: () => void = () => undefined;
		PUT.mockImplementation(
			() =>
				new Promise((resolve) => {
					refuse = () => {
						resolve({ error: { title: 'Non trovato' }, response: { status: 404 } });
					};
				})
		);
		book.update(1, doc('importante'));
		const saving = book.flush();
		await vi.advanceTimersByTimeAsync(0);

		book.discard();
		refuse();
		await saving;

		expect(book.failure).toBeNull();
		expect(book.state).toBe('saved');
	});

	it('does nothing when asked to save or to leave afterwards', async () => {
		const { book, PUT, DELETE } = setup(4);
		book.update(1, doc('nuovo'));
		book.discard();

		expect(await book.flush()).toBe(true);
		await book.leave();
		book.update(1, doc('ancora'));
		await vi.advanceTimersByTimeAsync(SAVE_MAX_WAIT);

		expect(PUT).not.toHaveBeenCalled();
		expect(DELETE).not.toHaveBeenCalled();
	});
});

describe('bookmark', () => {
	const mark = { pageId: 'p3', pageNumber: 3 };

	function answer(pageId: string, pageNumber: number): { data: unknown } {
		return { data: { pageId, pageNumber } };
	}

	it('shows the ribbon on the bookmarked page the diary opens on', () => {
		const { book } = setup(6, { bookmark: mark }, 2);

		expect(book.bookmark).toEqual(mark);
		expect(book.marked(3)).toBe(true);
		expect(book.marked(4)).toBe(false);
	});

	it('has no bookmark when the diary has none', () => {
		const { book } = setup(6);

		expect(book.bookmark).toBeNull();
		expect(book.marked(1)).toBe(false);
	});

	it('marks a page with the bookmark and remembers it', async () => {
		const { book, PUT } = setup(6);
		PUT.mockResolvedValue(answer('p2', 2));

		await book.toggleBookmark(2);

		expect(PUT).toHaveBeenCalledWith('/diaries/{id}/bookmark', {
			params: { path: { id: 'd1' } },
			body: { pageId: 'p2' }
		});
		expect(book.bookmark).toEqual({ pageId: 'p2', pageNumber: 2 });
		expect(book.marked(2)).toBe(true);
		expect(book.marking).toBe(false);
	});

	it('moves the bookmark to another page', async () => {
		const { book, PUT } = setup(6, { bookmark: mark }, 2);
		PUT.mockResolvedValue(answer('p4', 4));

		await book.toggleBookmark(4);

		expect(book.marked(3)).toBe(false);
		expect(book.marked(4)).toBe(true);
	});

	it('takes the bookmark off a page that has it', async () => {
		const { book, DELETE } = setup(6, { bookmark: mark }, 2);
		DELETE.mockResolvedValue({ response: { ok: true } });

		await book.toggleBookmark(3);

		expect(DELETE).toHaveBeenCalledWith('/diaries/{id}/bookmark', {
			params: { path: { id: 'd1' } }
		});
		expect(book.bookmark).toBeNull();
		expect(book.marked(3)).toBe(false);
	});

	it('keeps the bookmark and says why when the server refuses it', async () => {
		const { book, PUT } = setup(6, { bookmark: mark }, 2);
		PUT.mockResolvedValue({
			error: { status: 404, title: 'NOT_FOUND', violations: [{ message: 'Page not found' }] },
			response: { ok: false }
		});

		await book.toggleBookmark(4);

		expect(book.bookmark).toEqual(mark);
		expect(book.notice.message).toBe('Pagina non trovata');
		expect(book.notice.failed).toBe(true);
		expect(book.marking).toBe(false);
	});

	it('says the server is unreachable when the network fails', async () => {
		const { book, PUT } = setup(6);
		PUT.mockRejectedValue(new TypeError('Failed to fetch'));

		await book.toggleBookmark(1);

		expect(book.notice.message).toBe('Non riesco a raggiungere il server.');
		expect(book.bookmark).toBeNull();
	});

	it('keeps the bookmark when taking it off fails', async () => {
		const { book, DELETE } = setup(6, { bookmark: mark }, 2);
		DELETE.mockResolvedValue({ error: { title: 'boom' }, response: { ok: false, status: 500 } });

		await book.toggleBookmark(3);

		expect(book.bookmark).toEqual(mark);
		expect(book.notice.message).toBe('boom');
	});

	it('does nothing on a page that has no text and does not exist yet', async () => {
		const { book, PUT, POST } = setup(0);

		await book.toggleBookmark(1);

		expect(book.markable(1)).toBe(false);
		expect(PUT).not.toHaveBeenCalled();
		expect(POST).not.toHaveBeenCalled();
	});

	it('saves a page that was just written before marking it', async () => {
		const { book, POST, PUT } = setup(0);
		POST.mockResolvedValue({ data: { id: 'n1', pageNumber: 1, content: doc('inizio') } });
		PUT.mockResolvedValue(answer('n1', 1));
		book.update(1, doc('inizio'));

		await book.toggleBookmark(1);

		expect(POST).toHaveBeenCalledTimes(1);
		expect(PUT).toHaveBeenCalledWith('/diaries/{id}/bookmark', {
			params: { path: { id: 'd1' } },
			body: { pageId: 'n1' }
		});
		expect(book.marked(1)).toBe(true);
	});

	it('says the page must be saved first when saving fails', async () => {
		const { book, POST, PUT } = setup(0);
		POST.mockRejectedValue(new TypeError('Failed to fetch'));
		book.update(1, doc('inizio'));

		await book.toggleBookmark(1);

		expect(PUT).not.toHaveBeenCalled();
		expect(book.notice.message).toBe(
			'Prima devo salvare la pagina, ma non ci riesco. Riprova tra poco.'
		);
	});

	it('ignores a second tap while the first is still running', async () => {
		const { book, PUT } = setup(6);
		PUT.mockResolvedValue(answer('p1', 1));

		await Promise.all([book.toggleBookmark(1), book.toggleBookmark(1)]);

		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('drops the bookmark when its page is deleted', async () => {
		const { book, DELETE } = setup(6, { bookmark: mark }, 2);
		DELETE.mockResolvedValue({ response: { ok: true } });

		await book.removePage(3);

		expect(book.bookmark).toBeNull();
	});

	it('keeps the bookmark on its page when an earlier page is deleted', async () => {
		const { book, DELETE } = setup(6, { bookmark: { pageId: 'p2', pageNumber: 2 } });
		DELETE.mockResolvedValue({ response: { ok: true } });

		await book.removePage(1);

		expect(book.bookmark).toEqual({ pageId: 'p2', pageNumber: 1 });
	});

	it('keeps the bookmark when a later page is deleted', async () => {
		const { book, DELETE } = setup(6, { bookmark: { pageId: 'p1', pageNumber: 1 } });
		DELETE.mockResolvedValue({ response: { ok: true } });

		await book.removePage(2);

		expect(book.bookmark).toEqual({ pageId: 'p1', pageNumber: 1 });
	});
});

describe('tags of the pages', () => {
	it('knows the tag of the pages it was opened with', () => {
		const spread = spreadData(4, 1);
		const tagged = {
			...spread,
			pages: spread.pages.map((page) => ({ ...page, tag: `tag${page.id}` }))
		};
		const opened = new DiaryBook({ GET: vi.fn<Call>() } as unknown as ApiClient, 'd1', tagged, 1, {
			autoSave: () => true,
			drafts: createMemoryDraftStore(),
			animate: () => false,
			onview: vi.fn<(spread: number) => void>()
		});
		expect(opened.tags.tagOf('p1')).toBe('tagp1');
		expect(opened.tags.tagOf('p2')).toBe('tagp2');
	});

	it('learns the tag of the pages it loads later', async () => {
		const { book, GET } = setup(6);
		GET.mockResolvedValue({
			data: {
				list: [
					{ id: 'p3', pageNumber: 3, version: 3, content: doc('tre'), tag: 'combat' },
					{ id: 'p4', pageNumber: 4, version: 3, content: doc('quattro') }
				],
				totalRows: 6,
				totalPages: 3,
				page: 2
			}
		});

		await book.turn('forward');

		expect(book.tags.tagOf('p3')).toBe('combat');
		expect(book.tags.tagOf('p4')).toBeNull();
	});

	it('gives the id of a saved page and none for a page that is not there', () => {
		const { book } = setup(4);
		expect(book.pageId(1)).toBe('p1');
		expect(book.pageId(9)).toBeNull();
	});

	it('saves the tag at once, without waiting for the pause of the autosave', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { tag: 'Erebor' } });
		book.tags.set('p1', 'Erebor');
		expect(book.unsaved).toBe(true);
		expect(book.state).toBe('pending');

		await vi.advanceTimersByTimeAsync(0);

		expect(PUT).toHaveBeenCalledTimes(1);
		expect(PUT).toHaveBeenCalledWith('/pages/{id}/tag', {
			params: { path: { id: 'p1' } },
			body: { tag: 'Erebor' }
		});
		expect(book.unsaved).toBe(false);
		expect(book.state).toBe('saved');
	});

	it('saves the tag even when the autosave of the text is off', async () => {
		const { book, PUT } = setup(4, { autoSave: () => false });
		PUT.mockResolvedValue({ data: { tag: 'Erebor' } });
		book.tags.set('p1', 'Erebor');

		await vi.advanceTimersByTimeAsync(0);

		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('does not touch the version of the page when it saves the tag', async () => {
		const { book, PUT } = setup(4);
		PUT.mockResolvedValue({ data: { tag: 'Erebor' } });
		book.tags.set('p1', 'Erebor');

		await vi.advanceTimersByTimeAsync(SAVE_DELAY);

		expect(PUT).toHaveBeenCalledTimes(1);
		expect(PUT.mock.calls[0]?.[0]).toBe('/pages/{id}/tag');
	});

	it('forgets the tag of a page that is deleted', async () => {
		const { book, DELETE } = setup(4);
		DELETE.mockResolvedValue({ response: { ok: true } });
		book.tags.seed('p2', 'combat');

		await book.removePage(2);

		expect(book.tags.tagOf('p2')).toBeNull();
	});
});

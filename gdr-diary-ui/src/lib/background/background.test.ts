import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { JSONContent } from '@tiptap/core';
import type { ApiClient } from '#lib/api/client.js';
import { createMemoryDraftStore, type DraftStore } from '#lib/book/draft.js';
import { emptyDoc } from '#lib/book/pages.js';
import { doc } from '#lib/book/test-support.js';
import { AUTOSAVE_MAX_WAIT, RETRY_DELAY } from '#lib/pdf/autosave.js';
import {
	CharacterBackground,
	DRAFT_DELAY,
	DRAFT_PAGE,
	draftKey,
	type BackgroundOptions
} from './background.svelte.js';

type Call = (...args: unknown[]) => Promise<unknown>;

interface Setup {
	background: CharacterBackground;
	GET: ReturnType<typeof vi.fn<Call>>;
	PUT: ReturnType<typeof vi.fn<Call>>;
	drafts: DraftStore;
}

function saved(content: JSONContent, version: number): Promise<unknown> {
	return Promise.resolve({ data: { content, version }, response: { status: 200 } });
}

function refused(status: number): Promise<unknown> {
	return Promise.resolve({ error: { title: 'BAD' }, response: { status } });
}

function setup(
	options: Partial<BackgroundOptions> = {},
	initial: { content: JSONContent; version: number } = { content: emptyDoc(), version: 0 }
): Setup {
	const GET = vi.fn<Call>();
	const PUT = vi.fn<Call>(async (_path: unknown, init: unknown) => {
		const body = (init as { body: { content: JSONContent; version: number } }).body;
		return saved(body.content, body.version + 1);
	});
	const drafts = createMemoryDraftStore();
	const background = new CharacterBackground({ GET, PUT } as unknown as ApiClient, 'c1', initial, {
		autoSave: () => true,
		saveDelay: () => 5000,
		drafts,
		maxChars: 50,
		...options
	});
	return { background, GET, PUT, drafts };
}

beforeEach(() => {
	vi.useFakeTimers();
});

afterEach(() => {
	vi.useRealTimers();
});

describe('CharacterBackground state', () => {
	it('starts saved', () => {
		expect(setup().background.state).toBe('saved');
	});

	it('is pending after a change when the autosave is on', () => {
		const { background } = setup();
		background.update(doc('ciao'));
		expect(background.state).toBe('pending');
	});

	it('is unsaved after a change when the autosave is off', () => {
		const { background } = setup({ autoSave: () => false });
		background.update(doc('ciao'));
		expect(background.state).toBe('unsaved');
	});

	it('is saved again when the text goes back to what was saved', () => {
		const { background } = setup();
		background.update(doc('ciao'));
		background.update(emptyDoc());
		expect(background.state).toBe('saved');
	});

	it('counts the characters of the text', () => {
		const { background } = setup();
		background.update(doc('ciao'));
		expect(background.length).toBe(4);
	});

	it('is full when the text reaches the limit', () => {
		const { background } = setup({ maxChars: 4 });
		background.update(doc('ciao'));
		expect(background.full).toBe(true);
	});

	it('is not full below the limit', () => {
		const { background } = setup({ maxChars: 5 });
		background.update(doc('ciao'));
		expect(background.full).toBe(false);
	});
});

describe('CharacterBackground saving', () => {
	it('saves after the pause chosen by the user', async () => {
		const { background, PUT } = setup();
		background.update(doc('ciao'));
		await vi.advanceTimersByTimeAsync(5000);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(background.state).toBe('saved');
	});

	it('does not save before the pause is over', async () => {
		const { background, PUT } = setup();
		background.update(doc('ciao'));
		await vi.advanceTimersByTimeAsync(4999);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('does not save by itself when the autosave is off', async () => {
		const { background, PUT } = setup({ autoSave: () => false });
		background.update(doc('ciao'));
		await vi.advanceTimersByTimeAsync(AUTOSAVE_MAX_WAIT * 2);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('saves on demand', async () => {
		const { background, PUT } = setup({ autoSave: () => false });
		background.update(doc('ciao'));
		await background.flush();
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('sends the version it holds', async () => {
		const { background, PUT } = setup({}, { content: doc('uno'), version: 4 });
		background.update(doc('due'));
		await background.flush();
		expect(PUT).toHaveBeenCalledWith('/characters/{characterId}/background', {
			params: { path: { characterId: 'c1' } },
			body: { content: doc('due'), version: 4 }
		});
	});

	it('keeps the new version for the next save', async () => {
		const { background } = setup();
		background.update(doc('uno'));
		await background.flush();
		expect(background.version).toBe(1);
	});

	it('does nothing when there is nothing to save', async () => {
		const { background, PUT } = setup();
		await background.flush();
		expect(PUT).not.toHaveBeenCalled();
	});

	it('saves again what was typed while it was saving', async () => {
		const { background, PUT } = setup();
		PUT.mockImplementationOnce(async (_path: unknown, init: unknown) => {
			background.update(doc('uno due'));
			const body = (init as { body: { content: JSONContent; version: number } }).body;
			return saved(body.content, body.version + 1);
		});
		background.update(doc('uno'));
		await background.flush();
		expect(PUT).toHaveBeenCalledTimes(2);
		expect(background.state).toBe('saved');
	});

	it('saves at the latest after the maximum wait while the user keeps typing', async () => {
		const { background, PUT } = setup();
		await Promise.all(
			Array.from({ length: 6 }, async (_, index) => {
				background.update(doc(`testo ${String(index)}`));
				await vi.advanceTimersByTimeAsync(4000);
			})
		);
		expect(PUT).toHaveBeenCalled();
	});
});

describe('CharacterBackground failures', () => {
	it('reports a network failure as offline and tries again later', async () => {
		const { background, PUT } = setup();
		PUT.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		background.update(doc('ciao'));
		await background.flush();
		expect(background.state).toBe('offline');
		await vi.advanceTimersByTimeAsync(RETRY_DELAY);
		expect(PUT).toHaveBeenCalledTimes(2);
		expect(background.state).toBe('saved');
	});

	it('waits longer after every failed attempt', async () => {
		const { background, PUT } = setup();
		PUT.mockRejectedValue(new TypeError('Failed to fetch'));
		background.update(doc('ciao'));
		await background.flush();
		await vi.advanceTimersByTimeAsync(RETRY_DELAY);
		expect(PUT).toHaveBeenCalledTimes(2);
		await vi.advanceTimersByTimeAsync(RETRY_DELAY);
		expect(PUT).toHaveBeenCalledTimes(2);
		await vi.advanceTimersByTimeAsync(RETRY_DELAY);
		expect(PUT).toHaveBeenCalledTimes(3);
	});

	it('does not try again when the server refuses the text', async () => {
		const { background, PUT } = setup();
		PUT.mockReturnValue(refused(400));
		background.update(doc('ciao'));
		await background.flush();
		await vi.advanceTimersByTimeAsync(60000);
		expect(PUT).toHaveBeenCalledTimes(1);
		expect(background.rejected).toBe(true);
		expect(background.state).toBe('unsaved');
	});

	it('keeps the text when saving fails', async () => {
		const { background, PUT } = setup();
		PUT.mockReturnValue(refused(400));
		background.update(doc('ciao'));
		await background.flush();
		expect(background.content).toEqual(doc('ciao'));
	});

	it('tries again at once when the connection comes back', async () => {
		const { background, PUT } = setup();
		PUT.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		background.update(doc('ciao'));
		await background.flush();
		background.reconnect();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(2);
	});

	it('does not touch anything on reconnect when all is well', async () => {
		const { background, PUT } = setup();
		background.reconnect();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).not.toHaveBeenCalled();
	});

	it('retries by hand', async () => {
		const { background, PUT } = setup();
		PUT.mockReturnValueOnce(refused(400));
		background.update(doc('ciao'));
		await background.flush();
		background.retry();
		await vi.advanceTimersByTimeAsync(0);
		expect(PUT).toHaveBeenCalledTimes(2);
	});
});

describe('CharacterBackground conflict', () => {
	async function inConflict(): Promise<Setup> {
		const made = setup({}, { content: doc('mio'), version: 2 });
		made.PUT.mockReturnValueOnce(refused(409));
		made.background.update(doc('mio nuovo'));
		await made.background.flush();
		return made;
	}

	it('stops saving and says so', async () => {
		const { background } = await inConflict();
		expect(background.state).toBe('conflict');
	});

	it('does not save while the conflict is open', async () => {
		const { background, PUT } = await inConflict();
		background.update(doc('ancora'));
		await vi.advanceTimersByTimeAsync(AUTOSAVE_MAX_WAIT);
		expect(PUT).toHaveBeenCalledTimes(1);
	});

	it('takes the saved text when the user chooses it', async () => {
		const { background, GET } = await inConflict();
		GET.mockReturnValue(Promise.resolve({ data: { content: doc('altrove'), version: 7 } }));
		await background.resolveConflict('theirs');
		expect(background.content).toEqual(doc('altrove'));
		expect(background.version).toBe(7);
		expect(background.state).toBe('saved');
	});

	it('shows the saved text again in the editor when the user chooses it', async () => {
		const { background, GET } = await inConflict();
		GET.mockReturnValue(Promise.resolve({ data: { content: doc('altrove'), version: 7 } }));
		const before = background.generation;
		await background.resolveConflict('theirs');
		expect(background.generation).toBe(before + 1);
	});

	it('saves the text of the user over the saved one when the user keeps it', async () => {
		const { background, GET, PUT } = await inConflict();
		GET.mockReturnValue(Promise.resolve({ data: { content: doc('altrove'), version: 7 } }));
		await background.resolveConflict('mine');
		expect(PUT).toHaveBeenLastCalledWith('/characters/{characterId}/background', {
			params: { path: { characterId: 'c1' } },
			body: { content: doc('mio nuovo'), version: 7 }
		});
		expect(background.state).toBe('saved');
	});

	it('stays in conflict when the saved text cannot be read', async () => {
		const { background, GET } = await inConflict();
		GET.mockReturnValue(Promise.resolve({ error: { title: 'BAD' } }));
		expect(await background.resolveConflict('theirs')).toBe(false);
		expect(background.state).toBe('conflict');
	});

	it('has nothing to resolve without a conflict', async () => {
		expect(await setup().background.resolveConflict('mine')).toBe(true);
	});
});

describe('CharacterBackground drafts', () => {
	it('keeps a draft of the unsaved text', async () => {
		const { background, drafts } = setup();
		background.update(doc('ciao'));
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY);
		const list = await drafts.list(draftKey('c1'));
		expect(list.map((draft) => draft.content)).toEqual([doc('ciao')]);
	});

	it('remembers the version the draft started from', async () => {
		const { background, drafts } = setup({}, { content: doc('uno'), version: 3 });
		background.update(doc('due'));
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY);
		const list = await drafts.list(draftKey('c1'));
		expect(list.map((draft) => draft.baseVersion)).toEqual([3]);
	});

	it('drops the draft once the text is saved', async () => {
		const { background, drafts } = setup();
		background.update(doc('ciao'));
		await vi.advanceTimersByTimeAsync(DRAFT_DELAY);
		await background.flush();
		expect(await drafts.list(draftKey('c1'))).toEqual([]);
	});

	it('brings back an unsaved draft of the same version', () => {
		const { background } = setup({}, { content: doc('uno'), version: 3 });
		background.restore([
			{
				diaryId: draftKey('c1'),
				page: DRAFT_PAGE,
				pageId: null,
				baseVersion: 3,
				content: doc('uno e altro'),
				savedAt: 1
			}
		]);
		expect(background.content).toEqual(doc('uno e altro'));
		expect(background.restored).toBe(true);
		expect(background.state).toBe('pending');
	});

	it('forgets a draft that started from an older version', async () => {
		const { background, drafts } = setup({}, { content: doc('uno'), version: 5 });
		await drafts.put({
			diaryId: draftKey('c1'),
			page: DRAFT_PAGE,
			pageId: null,
			baseVersion: 3,
			content: doc('vecchio'),
			savedAt: 1
		});
		background.restore(await drafts.list(draftKey('c1')));
		await vi.advanceTimersByTimeAsync(0);
		expect(background.content).toEqual(doc('uno'));
		expect(await drafts.list(draftKey('c1'))).toEqual([]);
	});

	it('does nothing without a draft', () => {
		const { background } = setup();
		background.restore([]);
		expect(background.restored).toBe(false);
	});
});

describe('CharacterBackground disposal', () => {
	it('does not save after it is disposed', async () => {
		const { background, PUT } = setup();
		background.update(doc('ciao'));
		background.dispose();
		await vi.advanceTimersByTimeAsync(AUTOSAVE_MAX_WAIT);
		expect(PUT).not.toHaveBeenCalled();
	});
});

import { beforeEach, describe, expect, it, vi } from 'vitest';

const { api, fetchDiary, fetchCharacter, fetchSpread, loadDrafts } = vi.hoisted(() => ({
	api: { name: 'api' },
	fetchDiary: vi.fn<(api: unknown, id: string) => Promise<unknown>>(),
	fetchCharacter: vi.fn<(api: unknown, id: string) => Promise<unknown>>(),
	fetchSpread: vi.fn<(api: unknown, id: string, spread: number) => Promise<unknown>>(),
	loadDrafts: vi.fn<(id: string) => Promise<unknown>>()
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/diaries/api.js', () => ({ fetchDiary }));
vi.mock('#lib/characters/api.js', () => ({ fetchCharacter }));
vi.mock('#lib/book/draft.js', () => ({ loadDrafts }));
vi.mock('#lib/book/pages.js', async (importOriginal) => {
	const original = await importOriginal<Record<string, unknown>>();
	return { ...original, fetchSpread };
});

import { characterFixture, diaryFixture } from '#lib/book/test-support.js';
import { load } from './+page.js';

interface Loaded {
	diary: unknown;
	character: unknown;
	initial: unknown;
	spread: number;
	drafts: unknown;
}

function runLoad(vista: string | null): Promise<Loaded> {
	const url = new URL('http://app.test/diaries/d1');
	if (vista !== null) {
		url.searchParams.set('vista', vista);
	}
	const event = { params: { diaryId: 'd1' }, url };
	return (load as unknown as (event: unknown) => Promise<Loaded>)(event);
}

const diary = diaryFixture;
const character = characterFixture;
const drafts = { p1: { text: 'bozza' } };

function spreadOf(totalPages: number): { pages: never[]; totalPages: number } {
	return { pages: [], totalPages };
}

describe('diary page load', () => {
	beforeEach(() => {
		fetchDiary.mockReset();
		fetchDiary.mockResolvedValue(diary);
		fetchCharacter.mockReset();
		fetchCharacter.mockResolvedValue(character);
		fetchSpread.mockReset();
		fetchSpread.mockResolvedValue(spreadOf(10));
		loadDrafts.mockReset();
		loadDrafts.mockResolvedValue(drafts);
	});

	it('answers 404 with the message of the API when the diary cannot be loaded', async () => {
		fetchDiary.mockRejectedValue(new Error('Diario non trovato'));

		await expect(runLoad(null)).rejects.toMatchObject({
			status: 404,
			body: { message: 'Diario non trovato' }
		});
	});

	it('answers 404 when the character of the diary cannot be loaded', async () => {
		fetchCharacter.mockRejectedValue(new Error('Personaggio non trovato'));

		await expect(runLoad(null)).rejects.toMatchObject({
			status: 404,
			body: { message: 'Personaggio non trovato' }
		});
	});

	it('answers 404 with a generic message when the failure is not an error', async () => {
		fetchDiary.mockRejectedValue('boom');

		await expect(runLoad(null)).rejects.toMatchObject({
			status: 404,
			body: { message: 'Non trovo questo diario.' }
		});
	});

	it('answers 503 when the diary cannot be reached because of the network', async () => {
		fetchDiary.mockRejectedValue(new TypeError('Non riesco a raggiungere il server'));

		await expect(runLoad(null)).rejects.toMatchObject({
			status: 503,
			body: { message: 'Non riesco a raggiungere il server' }
		});
	});

	it('answers 503 when the spread cannot be reached because of the network', async () => {
		fetchSpread.mockRejectedValue(new TypeError('Non riesco a raggiungere il server'));

		await expect(runLoad(null)).rejects.toMatchObject({ status: 503 });
	});

	it('goes to the last spread when the requested one is past the end', async () => {
		fetchSpread.mockResolvedValue(spreadOf(7));

		const loaded = await runLoad('50');

		expect(loaded.spread).toBe(4);
	});

	it('loads the last spread again when the requested one is past the end', async () => {
		fetchSpread.mockResolvedValue(spreadOf(7));

		await runLoad('50');

		expect(fetchSpread.mock.calls.map((call) => call[2])).toEqual([50, 4]);
	});

	it('loads the spread once when the requested one exists', async () => {
		const loaded = await runLoad('3');

		expect(fetchSpread).toHaveBeenCalledOnce();
		expect(loaded.spread).toBe(3);
	});

	it.each([[null], ['abc'], ['0'], ['-4']])(
		'starts from the first spread when the view is %s',
		async (vista) => {
			const loaded = await runLoad(vista);

			expect(loaded.spread).toBe(1);
		}
	);

	it('opens the first spread of an empty diary', async () => {
		fetchSpread.mockResolvedValue(spreadOf(0));

		const loaded = await runLoad('9');

		expect(loaded.spread).toBe(1);
	});

	it('returns the diary, the character, the first spread and the drafts', async () => {
		const first = spreadOf(10);
		fetchSpread.mockResolvedValue(first);

		const loaded = await runLoad('2');

		expect(loaded).toEqual({ diary, character, initial: first, spread: 2, drafts });
		expect(loadDrafts).toHaveBeenCalledWith('d1');
	});
});

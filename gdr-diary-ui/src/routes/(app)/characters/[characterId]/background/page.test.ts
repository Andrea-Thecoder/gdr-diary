import { beforeEach, describe, expect, it, vi } from 'vitest';

const { api, fetchCharacter, fetchBackground, loadDrafts } = vi.hoisted(() => ({
	api: { name: 'api' },
	fetchCharacter: vi.fn<(api: unknown, id: string) => Promise<unknown>>(),
	fetchBackground: vi.fn<(api: unknown, id: string) => Promise<unknown>>(),
	loadDrafts: vi.fn<(id: string) => Promise<unknown>>()
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/characters/api.js', () => ({ fetchCharacter }));
vi.mock('#lib/background/api.js', async (original) => ({
	...(await original<Record<string, unknown>>()),
	fetchBackground
}));
vi.mock('#lib/book/draft.js', () => ({ loadDrafts }));

import { BackgroundLoadError } from '#lib/background/api.js';
import { characterFixture } from '#lib/book/test-support.js';
import { load } from './+page.js';

interface Loaded {
	character: unknown;
	background: unknown;
	drafts: unknown;
}

function runLoad(): Promise<Loaded> {
	return (load as unknown as (event: unknown) => Promise<Loaded>)({
		params: { characterId: 'c1' }
	});
}

const background = { content: { type: 'doc' }, version: 2 };
const drafts = [{ page: 1 }];

describe('background page load', () => {
	beforeEach(() => {
		fetchCharacter.mockReset();
		fetchBackground.mockReset();
		loadDrafts.mockReset();
		fetchCharacter.mockResolvedValue(characterFixture);
		fetchBackground.mockResolvedValue(background);
		loadDrafts.mockResolvedValue(drafts);
	});

	it('loads the character, the story and the saved draft', async () => {
		const loaded = await runLoad();
		expect(loaded).toEqual({ character: characterFixture, background, drafts });
	});

	it('asks for the story of the character in the address', async () => {
		await runLoad();
		expect(fetchBackground).toHaveBeenCalledWith(api, 'c1');
	});

	it('looks for the draft of the story of that character', async () => {
		await runLoad();
		expect(loadDrafts).toHaveBeenCalledWith('background:c1');
	});

	it('answers 404 with the message of the API when the story cannot be read', async () => {
		fetchBackground.mockRejectedValue(new Error('Personaggio non trovato'));
		await expect(runLoad()).rejects.toMatchObject({
			status: 404,
			body: { message: 'Personaggio non trovato' }
		});
	});

	it('keeps a failure of the server as such, not as a missing page', async () => {
		fetchBackground.mockRejectedValue(new BackgroundLoadError('Errore imprevisto', 500));

		await expect(runLoad()).rejects.toMatchObject({ status: 500 });
	});

	it('answers 404 for a game master, who has no story', async () => {
		fetchCharacter.mockResolvedValue({ ...characterFixture, kind: 'GAME_MASTER' });
		await expect(runLoad()).rejects.toMatchObject({
			status: 404,
			body: { message: 'Il master non ha una storia.' }
		});
	});

	it('does not read the story of a game master', async () => {
		fetchCharacter.mockResolvedValue({ ...characterFixture, kind: 'GAME_MASTER' });
		await runLoad().catch(() => null);
		expect(fetchBackground).not.toHaveBeenCalled();
	});

	it('answers 503 when the network is down', async () => {
		fetchCharacter.mockRejectedValue(new TypeError('Failed to fetch'));
		await expect(runLoad()).rejects.toMatchObject({ status: 503 });
	});

	it('answers 404 with a generic message when the failure has no message', async () => {
		fetchCharacter.mockRejectedValue('boom');
		await expect(runLoad()).rejects.toMatchObject({
			status: 404,
			body: { message: 'Non trovo la storia di questo personaggio.' }
		});
	});
});

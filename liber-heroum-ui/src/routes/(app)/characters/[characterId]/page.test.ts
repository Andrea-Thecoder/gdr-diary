import { beforeEach, describe, expect, it, vi } from 'vitest';

const { api, fetchCharacter } = vi.hoisted(() => ({
	api: { name: 'api' },
	fetchCharacter: vi.fn<(api: unknown, id: string) => Promise<unknown>>()
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/characters/api.js', () => ({ fetchCharacter }));

import { load } from './+page.js';

function runLoad(): Promise<unknown> {
	return (load as unknown as (event: unknown) => Promise<unknown>)({
		params: { characterId: 'c1' }
	});
}

describe('character page load', () => {
	beforeEach(() => {
		fetchCharacter.mockReset();
	});

	it('answers 404 with the message of the API when the character is missing', async () => {
		fetchCharacter.mockRejectedValue(new Error('Personaggio non trovato'));

		await expect(runLoad()).rejects.toMatchObject({
			status: 404,
			body: { message: 'Personaggio non trovato' }
		});
	});

	it('answers 404 with a generic message when the failure is not an error', async () => {
		fetchCharacter.mockRejectedValue('boom');

		await expect(runLoad()).rejects.toMatchObject({
			status: 404,
			body: { message: 'Non trovo questo personaggio.' }
		});
	});

	it('answers 503 when the failure is a network error', async () => {
		fetchCharacter.mockRejectedValue(new TypeError('Non riesco a raggiungere il server'));

		await expect(runLoad()).rejects.toMatchObject({
			status: 503,
			body: { message: 'Non riesco a raggiungere il server' }
		});
	});

	it('returns the character', async () => {
		const character = { id: 'c1', name: 'Alira' };
		fetchCharacter.mockResolvedValue(character);

		await expect(runLoad()).resolves.toEqual({ character });
		expect(fetchCharacter).toHaveBeenCalledWith(api, 'c1');
	});
});

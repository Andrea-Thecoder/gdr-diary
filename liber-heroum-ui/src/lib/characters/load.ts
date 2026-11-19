import { error } from '@sveltejs/kit';
import { getApi } from '#lib/api/index.js';
import { fetchCharacter, type CharacterDetail } from '#lib/characters/api.js';

export async function loadCharacter(characterId: string): Promise<{ character: CharacterDetail }> {
	try {
		return { character: await fetchCharacter(getApi(), characterId) };
	} catch (cause) {
		error(
			cause instanceof TypeError ? 503 : 404,
			cause instanceof Error ? cause.message : 'Non trovo questo personaggio.'
		);
	}
}

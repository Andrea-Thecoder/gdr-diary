import { error } from '@sveltejs/kit';
import { getApi } from '#lib/api/index.js';
import { fetchCharacter } from '#lib/characters/api.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params }) => {
	try {
		return { character: await fetchCharacter(getApi(), params.characterId) };
	} catch (cause) {
		error(
			cause instanceof TypeError ? 503 : 404,
			cause instanceof Error ? cause.message : 'Non trovo questo personaggio.'
		);
	}
};

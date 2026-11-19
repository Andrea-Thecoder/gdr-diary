import { error } from '@sveltejs/kit';
import { getApi } from '#lib/api/index.js';
import { draftKey } from '#lib/background/background.svelte.js';
import { BackgroundLoadError, fetchBackground, type BackgroundData } from '#lib/background/api.js';
import { loadDrafts, type Draft } from '#lib/book/draft.js';
import { fetchCharacter, type CharacterDetail } from '#lib/characters/api.js';
import { isMaster } from '#lib/characters/kind.js';
import type { PageLoad } from './$types';

interface Opened {
	character: CharacterDetail;
	background: BackgroundData;
	drafts: Draft[];
}

async function fetchOpened(characterId: string): Promise<Opened | null> {
	try {
		const api = getApi();
		const character = await fetchCharacter(api, characterId);
		if (isMaster(character.kind)) {
			return null;
		}
		const [background, drafts] = await Promise.all([
			fetchBackground(api, characterId),
			loadDrafts(draftKey(characterId))
		]);
		return { character, background, drafts };
	} catch (cause) {
		error(
			statusOf(cause),
			cause instanceof Error ? cause.message : 'Non trovo la storia di questo personaggio.'
		);
	}
}

function statusOf(cause: unknown): number {
	if (cause instanceof TypeError) {
		return 503;
	}
	if (cause instanceof BackgroundLoadError && cause.status >= 500) {
		return cause.status;
	}
	return 404;
}

export const load: PageLoad = async ({ params }): Promise<Opened> => {
	const opened = await fetchOpened(params.characterId);
	if (opened === null) {
		error(404, 'Il master non ha una storia.');
	}
	return opened;
};

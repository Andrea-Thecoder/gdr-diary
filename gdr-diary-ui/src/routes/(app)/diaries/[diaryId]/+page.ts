import { error } from '@sveltejs/kit';
import { getApi } from '#lib/api/index.js';
import { loadDrafts } from '#lib/book/draft.js';
import { fetchSpread } from '#lib/book/pages.js';
import { parseSpread, spreadOf } from '#lib/book/spread.js';
import { fetchCharacter } from '#lib/characters/api.js';
import { fetchDiary } from '#lib/diaries/api.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, url }) => {
	const api = getApi();
	try {
		const diary = await fetchDiary(api, params.diaryId);
		const requested = parseSpread(url.searchParams.get('vista'));
		const [character, first] = await Promise.all([
			fetchCharacter(api, diary.characterId ?? ''),
			fetchSpread(api, params.diaryId, requested)
		]);
		const spread = Math.min(requested, spreadOf(Math.max(first.totalPages, 1)));
		const initial = spread === requested ? first : await fetchSpread(api, params.diaryId, spread);
		const drafts = await loadDrafts(params.diaryId);
		return { diary, character, initial, spread, drafts };
	} catch (cause) {
		error(
			cause instanceof TypeError ? 503 : 404,
			cause instanceof Error ? cause.message : 'Non trovo questo diario.'
		);
	}
};

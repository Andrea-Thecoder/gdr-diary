import { error, redirect } from '@sveltejs/kit';
import { resolve } from '$app/paths';
import type { ApiClient } from '#lib/api/client.js';
import { getApi } from '#lib/api/index.js';
import { fetchBookmark, type Bookmark } from '#lib/book/bookmark.js';
import { loadDrafts, type Draft } from '#lib/book/draft.js';
import { openingSpread } from '#lib/book/opening.js';
import { fetchSpread, type SpreadData } from '#lib/book/pages.js';
import { parseSpread } from '#lib/book/spread.js';
import { fetchCharacter, type CharacterDetail } from '#lib/characters/api.js';
import { fetchDiary, type DiaryDetail } from '#lib/diaries/api.js';
import type { PageLoad } from './$types';

interface Opened {
	diary: DiaryDetail;
	character: CharacterDetail;
	initial: SpreadData;
	spread: number;
	drafts: Draft[];
	bookmark: Bookmark | null;
}

type Outcome = { reopenAt: number } | Opened;

async function open(api: ApiClient, diaryId: string, requested: string | null): Promise<Outcome> {
	const diary = await fetchDiary(api, diaryId);
	const view = parseSpread(requested);
	const [character, first, bookmark] = await Promise.all([
		fetchCharacter(api, diary.characterId ?? ''),
		fetchSpread(api, diaryId, view),
		fetchBookmark(api, diaryId).catch((): null => null)
	]);
	const spread = openingSpread(requested, bookmark, first.totalPages);
	if (requested === null && spread !== view) {
		return { reopenAt: spread };
	}
	const initial = spread === view ? first : await fetchSpread(api, diaryId, spread);
	const drafts = await loadDrafts(diaryId);
	return { diary, character, initial, spread, drafts, bookmark };
}

export const load: PageLoad = async ({ params, url }) => {
	let outcome: Outcome;
	try {
		outcome = await open(getApi(), params.diaryId, url.searchParams.get('vista'));
	} catch (cause) {
		error(
			cause instanceof TypeError ? 503 : 404,
			cause instanceof Error ? cause.message : 'Non trovo questo diario.'
		);
	}
	if ('reopenAt' in outcome) {
		redirect(
			307,
			resolve(`/(app)/diaries/[diaryId]?vista=${String(outcome.reopenAt)}`, {
				diaryId: params.diaryId
			})
		);
	}
	return outcome;
};

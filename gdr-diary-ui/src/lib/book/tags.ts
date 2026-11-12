import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import { MISSING_PAGE_MESSAGE, MissingPageError } from './pages.js';

export const TAG_MAX_LENGTH = 30;
export const TAG_LIST_SIZE = 50;
export const SUGGESTION_SIZE = 8;
export const POSTIT_COLORS = 4;
export const POSTIT_TILTS = 5;

export const EMPTY_TAG_MESSAGE = 'Scrivi il nome del tag.';
export const TAG_TOO_LONG_MESSAGE = `Un tag può avere al massimo ${String(TAG_MAX_LENGTH)} caratteri.`;

const BAD_REQUEST = 400;
const NOT_FOUND = 404;
const CONTROL_CHARACTERS = /\p{Cc}/gu;

export interface DiaryTag {
	name: string;
	pageNumbers: number[];
}

export interface DiaryTagQuery {
	name: string;
	page: number;
	size: number;
}

export interface DiaryTagPage {
	items: DiaryTag[];
	totalRows: number;
	totalPages: number;
	page: number;
}

export class RejectedTagError extends Error {}

export function cleanTag(text: string): string {
	return text.replace(CONTROL_CHARACTERS, ' ').trim();
}

export function sameTag(a: string, b: string): boolean {
	return a.toLowerCase() === b.toLowerCase();
}

export function checkTag(text: string): string | null {
	const name = cleanTag(text);
	if (name === '') {
		return EMPTY_TAG_MESSAGE;
	}
	return name.length > TAG_MAX_LENGTH ? TAG_TOO_LONG_MESSAGE : null;
}

export function pageForTag(
	pageNumbers: readonly number[],
	visible: readonly number[]
): number | null {
	const first = pageNumbers[0];
	if (first === undefined) {
		return null;
	}
	if (!pageNumbers.some((page) => visible.includes(page))) {
		return first;
	}
	const edge = Math.max(...visible);
	return pageNumbers.find((page) => page > edge) ?? first;
}

function hashOf(text: string): number {
	let hash = 5381;
	for (const character of text.toLowerCase()) {
		hash = (Math.imul(hash, 33) + (character.codePointAt(0) ?? 0)) >>> 0;
	}
	return hash;
}

export function postitColor(name: string): number {
	return hashOf(name) % POSTIT_COLORS;
}

export function postitTilt(name: string): number {
	return (hashOf(`${name}~`) % POSTIT_TILTS) - Math.floor(POSTIT_TILTS / 2);
}

export function pagesLabel(pageNumbers: readonly number[]): string {
	const shown = pageNumbers.slice(0, 4).map(String).join(', ');
	if (pageNumbers.length === 1) {
		return `pagina ${shown}`;
	}
	return pageNumbers.length > 4 ? `${String(pageNumbers.length)} pagine` : `pagine ${shown}`;
}

export async function updatePageTag(
	api: ApiClient,
	id: string,
	tag: string | null
): Promise<string | null> {
	const { data, error, response } = await api.PUT('/pages/{id}/tag', {
		params: { path: { id } },
		body: { tag }
	});
	if (data !== undefined) {
		return data.tag ?? null;
	}
	if (response.status === NOT_FOUND) {
		throw new MissingPageError(MISSING_PAGE_MESSAGE);
	}
	const message = describeApiError(error, 'Non riesco a salvare il tag.');
	throw response.status === BAD_REQUEST ? new RejectedTagError(message) : new Error(message);
}

export async function fetchDiaryTags(
	api: ApiClient,
	diaryId: string,
	query: DiaryTagQuery
): Promise<DiaryTagPage> {
	const name = query.name.trim();
	const { data, error } = await api.GET('/diaries/{diaryId}/tags', {
		params: {
			path: { diaryId },
			query: { page: query.page, size: query.size, ...(name === '' ? {} : { name }) }
		}
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare i tag del diario.'));
	}
	const items = (data.list ?? []).flatMap((dto) =>
		dto.name === undefined ? [] : [{ name: dto.name, pageNumbers: dto.pageNumbers ?? [] }]
	);
	return {
		items,
		totalRows: data.totalRows ?? 0,
		totalPages: data.totalPages ?? 0,
		page: data.page ?? query.page
	};
}

import type { JSONContent } from '@tiptap/core';
import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import type { components } from '#lib/api/schema.js';
import { titlesFromHeadings } from './title.js';

export type PageDto = components['schemas']['BaseDiaryPageDTO'];

export const MAX_PAGES = 100;
export const PAGES_PER_SPREAD = 2;
const NOT_FOUND = 404;
const REQUEST_TIMEOUT = 408;
const TOO_MANY_REQUESTS = 429;

export const MISSING_PAGE_MESSAGE =
	'Questa pagina è stata eliminata altrove: copia il testo se ti serve e ricarica il diario.';

export interface DiaryPageData {
	id: string;
	pageNumber: number;
	content: JSONContent;
	version: number;
	tag: string | null;
}

export interface SpreadData {
	pages: DiaryPageData[];
	totalPages: number;
}

export class ConflictError extends Error {}

export class MissingPageError extends Error {}

export class RejectedPageError extends Error {}

function isRejected(status: number): boolean {
	return (
		status >= 400 && status < 500 && status !== REQUEST_TIMEOUT && status !== TOO_MANY_REQUESTS
	);
}

export function emptyDoc(): JSONContent {
	return { type: 'doc', content: [{ type: 'paragraph' }] };
}

export function hasText(node: JSONContent | undefined): boolean {
	if (node === undefined) {
		return false;
	}
	if (node.type === 'text') {
		return (node.text ?? '').trim() !== '';
	}
	return (node.content ?? []).some((child) => hasText(child));
}

export function sameContent(a: JSONContent, b: JSONContent): boolean {
	return JSON.stringify(a) === JSON.stringify(b);
}

function normalize(dto: PageDto): DiaryPageData | null {
	if (dto.id === undefined || dto.pageNumber === undefined) {
		return null;
	}
	const content: JSONContent = dto.content ?? {};
	return {
		id: dto.id,
		pageNumber: dto.pageNumber,
		version: dto.version ?? 0,
		tag: dto.tag ?? null,
		content: content.type === 'doc' ? titlesFromHeadings(content) : emptyDoc()
	};
}

export async function fetchSpread(
	api: ApiClient,
	diaryId: string,
	spread: number
): Promise<SpreadData> {
	const { data, error } = await api.GET('/diaries/{diaryId}/pages', {
		params: { path: { diaryId }, query: { page: String(spread) } }
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare le pagine del diario.'));
	}
	const pages = (data.list ?? [])
		.map((dto) => normalize(dto))
		.filter((page): page is DiaryPageData => page !== null);
	return { pages, totalPages: data.totalRows ?? 0 };
}

export async function createPage(
	api: ApiClient,
	diaryId: string,
	content: JSONContent
): Promise<DiaryPageData> {
	const { data, error, response } = await api.POST('/diaries/{diaryId}/pages', {
		params: { path: { diaryId } },
		body: { content }
	});
	const page = data === undefined ? null : normalize(data);
	if (page === null) {
		const message = describeApiError(error, 'Non riesco a creare la pagina.');
		throw isRejected(response.status) ? new RejectedPageError(message) : new Error(message);
	}
	return page;
}

export async function deletePage(api: ApiClient, id: string): Promise<void> {
	const { error, response } = await api.DELETE('/pages/{id}', { params: { path: { id } } });
	if (!response.ok && response.status !== NOT_FOUND) {
		throw new Error(describeApiError(error, 'Non riesco a eliminare la pagina.'));
	}
}

export async function updatePage(
	api: ApiClient,
	id: string,
	content: JSONContent,
	version: number
): Promise<DiaryPageData> {
	const { data, error, response } = await api.PUT('/pages/{id}', {
		params: { path: { id } },
		body: { content, version }
	});
	const page = data === undefined ? null : normalize(data);
	if (page === null) {
		if (response.status === 409) {
			throw new ConflictError(describeApiError(error, 'La pagina è stata modificata altrove.'));
		}
		if (response.status === NOT_FOUND) {
			throw new MissingPageError(MISSING_PAGE_MESSAGE);
		}
		const message = describeApiError(error, 'Non riesco a salvare la pagina.');
		throw isRejected(response.status) ? new RejectedPageError(message) : new Error(message);
	}
	return page;
}

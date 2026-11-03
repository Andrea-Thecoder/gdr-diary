import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import { fileForm, type ImageChange } from '#lib/api/multipart.js';
import type { components } from '#lib/api/schema.js';

export type DiarySummary = components['schemas']['BaseDiaryDTO'];
export type DiaryDetail = components['schemas']['DetailDiaryDTO'];

export const PAGE_SIZE = 12;
const NOT_FOUND = 404;

export interface DiaryQuery {
	title: string;
	descending: boolean;
	page: number;
}

export interface DiaryPage {
	items: DiarySummary[];
	totalRows: number;
	totalPages: number;
	page: number;
}

export interface DiaryFields {
	title: string;
	description: string;
	coverColor: string;
	font: string;
	fontSize: string;
}

export interface SavedDiary {
	diary: DiaryDetail;
	coverError: string | null;
}

function body(fields: DiaryFields): components['schemas']['InsertDiaryDTO'] {
	const description = fields.description.trim();
	const font = fields.font.trim();
	const fontSize = Number.parseInt(fields.fontSize, 10);
	return {
		title: fields.title.trim(),
		coverColor: fields.coverColor,
		...(description === '' ? {} : { description }),
		...(font === '' ? {} : { font }),
		...(Number.isNaN(fontSize) ? {} : { fontSize })
	};
}

export async function fetchDiaries(
	api: ApiClient,
	characterId: string,
	query: DiaryQuery
): Promise<DiaryPage> {
	const title = query.title.trim();
	const { data, error } = await api.GET('/characters/{characterId}/diaries', {
		params: {
			path: { characterId },
			query: {
				page: query.page,
				size: PAGE_SIZE,
				sort: 'title',
				descending: query.descending,
				...(title === '' ? {} : { title })
			}
		}
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare i diari.'));
	}
	return {
		items: data.list ?? [],
		totalRows: data.totalRows ?? 0,
		totalPages: data.totalPages ?? 0,
		page: data.page ?? query.page
	};
}

export async function createDiary(
	api: ApiClient,
	characterId: string,
	fields: DiaryFields,
	cover: File | null
): Promise<SavedDiary> {
	const { data, error } = await api.POST('/characters/{characterId}/diaries', {
		params: { path: { characterId } },
		body: body(fields)
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a creare il diario.'));
	}
	if (cover === null || data.id === undefined) {
		return { diary: data, coverError: null };
	}
	return { diary: data, coverError: await uploadCover(api, data.id, cover) };
}

export async function fetchDiary(api: ApiClient, id: string): Promise<DiaryDetail> {
	const { data, error } = await api.GET('/diaries/{id}', { params: { path: { id } } });
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare il diario.'));
	}
	return data;
}

export async function updateDiary(
	api: ApiClient,
	id: string,
	fields: DiaryFields,
	cover: ImageChange
): Promise<SavedDiary> {
	const { data, error } = await api.PUT('/diaries/{id}', {
		params: { path: { id } },
		body: body(fields)
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a salvare il diario.'));
	}
	if (cover.type === 'replace') {
		return { diary: data, coverError: await uploadCover(api, id, cover.file) };
	}
	if (cover.type === 'remove') {
		return { diary: data, coverError: await removeCover(api, id) };
	}
	return { diary: data, coverError: null };
}

async function removeCover(api: ApiClient, id: string): Promise<string | null> {
	const { error, response } = await api.DELETE('/diaries/{id}/cover', {
		params: { path: { id } }
	});
	if (response.ok) {
		return null;
	}
	return describeApiError(error, 'Non riesco a togliere la copertina.');
}

async function uploadCover(api: ApiClient, id: string, cover: File): Promise<string | null> {
	const { error, response } = await api.PUT('/diaries/{id}/cover', {
		params: { path: { id } },
		body: { file: '' },
		bodySerializer: fileForm(cover)
	});
	if (response.ok) {
		return null;
	}
	return describeApiError(error, 'Non riesco a caricare la copertina.');
}

export async function deleteDiary(api: ApiClient, id: string): Promise<void> {
	const { error, response } = await api.DELETE('/diaries/{id}', { params: { path: { id } } });
	if (!response.ok && response.status !== NOT_FOUND) {
		throw new Error(
			describeApiError(error, 'Non riesco a eliminare il diario. Riprova più tardi.')
		);
	}
}

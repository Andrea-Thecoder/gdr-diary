import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';

const NOT_FOUND = 404;

export interface Bookmark {
	pageId: string;
	pageNumber: number;
}

export async function fetchBookmark(api: ApiClient, diaryId: string): Promise<Bookmark | null> {
	const { data, error } = await api.GET('/diaries/{id}/bookmark', {
		params: { path: { id: diaryId } }
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a leggere il segnalibro.'));
	}
	if (typeof data.pageId !== 'string' || typeof data.pageNumber !== 'number') {
		return null;
	}
	return { pageId: data.pageId, pageNumber: data.pageNumber };
}

export async function setBookmark(
	api: ApiClient,
	diaryId: string,
	pageId: string
): Promise<Bookmark> {
	const { data, error } = await api.PUT('/diaries/{id}/bookmark', {
		params: { path: { id: diaryId } },
		body: { pageId }
	});
	if (
		data === undefined ||
		typeof data.pageId !== 'string' ||
		typeof data.pageNumber !== 'number'
	) {
		throw new Error(describeApiError(error, 'Non riesco a mettere il segnalibro.'));
	}
	return { pageId: data.pageId, pageNumber: data.pageNumber };
}

export async function removeBookmark(api: ApiClient, diaryId: string): Promise<void> {
	const { error, response } = await api.DELETE('/diaries/{id}/bookmark', {
		params: { path: { id: diaryId } }
	});
	if (!response.ok && response.status !== NOT_FOUND) {
		throw new Error(describeApiError(error, 'Non riesco a togliere il segnalibro.'));
	}
}

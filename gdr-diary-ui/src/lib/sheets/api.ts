import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import { fileForm } from '#lib/api/multipart.js';
import { fetchWithProgress, type UploadProgress } from '#lib/api/progress.js';
import type { components } from '#lib/api/schema.js';

export type SheetSummary = components['schemas']['BaseCharacterSheetDTO'];
export type SheetDetail = components['schemas']['DetailCharacterSheetDTO'];

export const PAGE_SIZE = 12;
const NOT_FOUND = 404;

export interface SheetQuery {
	title: string;
	system: string;
	descending: boolean;
	page: number;
}

export interface SheetPage {
	items: SheetSummary[];
	totalRows: number;
	totalPages: number;
	page: number;
}

export interface SheetFields {
	title: string;
	system: string;
}

export interface SavedSheet {
	sheet: SheetDetail;
	fileError: string | null;
}

function withProgress(onProgress: UploadProgress | undefined): {
	fetch?: (request: Request) => Promise<Response>;
} {
	return onProgress === undefined ? {} : { fetch: fetchWithProgress(onProgress) };
}

function sheetForm(file: File, fields: SheetFields): () => FormData {
	return () => {
		const form = new FormData();
		form.append('file', file);
		form.append('title', fields.title.trim());
		const system = fields.system.trim();
		if (system !== '') {
			form.append('system', system);
		}
		return form;
	};
}

export async function fetchSheets(
	api: ApiClient,
	characterId: string,
	query: SheetQuery
): Promise<SheetPage> {
	const title = query.title.trim();
	const system = query.system.trim();
	const { data, error } = await api.GET('/characters/{characterId}/sheets', {
		params: {
			path: { characterId },
			query: {
				page: query.page,
				size: PAGE_SIZE,
				sort: 'createdAt',
				descending: query.descending,
				...(title === '' ? {} : { title }),
				...(system === '' ? {} : { system })
			}
		}
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare le schede.'));
	}
	return {
		items: data.list ?? [],
		totalRows: data.totalRows ?? 0,
		totalPages: data.totalPages ?? 0,
		page: data.page ?? query.page
	};
}

export async function createSheet(
	api: ApiClient,
	characterId: string,
	fields: SheetFields,
	file: File,
	onProgress?: UploadProgress
): Promise<SheetDetail> {
	const { data, error } = await api.POST('/characters/{characterId}/sheets', {
		params: { path: { characterId } },
		body: { file: '', title: fields.title.trim() },
		bodySerializer: sheetForm(file, fields),
		...withProgress(onProgress)
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare la scheda.'));
	}
	return data;
}

export async function updateSheet(
	api: ApiClient,
	id: string,
	fields: SheetFields,
	file: File | null,
	onProgress?: UploadProgress
): Promise<SavedSheet> {
	const system = fields.system.trim();
	const { data, error } = await api.PUT('/sheets/{id}', {
		params: { path: { id } },
		body: { title: fields.title.trim(), system: system === '' ? null : system }
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a salvare la scheda.'));
	}
	if (file === null) {
		return { sheet: data, fileError: null };
	}
	const replaced = await api.PUT('/sheets/{id}/file', {
		params: { path: { id } },
		body: { file: '' },
		bodySerializer: fileForm(file),
		...withProgress(onProgress)
	});
	if (replaced.data === undefined) {
		return {
			sheet: data,
			fileError: describeApiError(replaced.error, 'Non riesco a sostituire il PDF.')
		};
	}
	return { sheet: replaced.data, fileError: null };
}

export async function deleteSheet(api: ApiClient, id: string): Promise<void> {
	const { error, response } = await api.DELETE('/sheets/{id}', { params: { path: { id } } });
	if (!response.ok && response.status !== NOT_FOUND) {
		throw new Error(
			describeApiError(error, 'Non riesco a eliminare la scheda. Riprova più tardi.')
		);
	}
}

export async function fetchSheet(api: ApiClient, id: string): Promise<SheetDetail> {
	const { data, error } = await api.GET('/sheets/{id}', { params: { path: { id } } });
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare la scheda.'));
	}
	return data;
}

export async function freshDownloadUrl(api: ApiClient, id: string): Promise<string> {
	const { data, error } = await api.GET('/sheets/{id}', { params: { path: { id } } });
	if (data?.downloadUrl === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a scaricare la scheda. Riprova.'));
	}
	return data.downloadUrl;
}

export async function replaceSheetFile(
	api: ApiClient,
	id: string,
	file: File
): Promise<SheetDetail> {
	const { data, error } = await api.PUT('/sheets/{id}/file', {
		params: { path: { id } },
		body: { file: '' },
		bodySerializer: fileForm(file)
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a salvare il PDF.'));
	}
	return data;
}

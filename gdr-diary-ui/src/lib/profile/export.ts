import { resolve } from '$app/paths';
import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import { fileNameFromDisposition, type DownloadedFile } from '#lib/api/download.js';
import { RecentLoginRequiredError } from './api.js';

export const EXPORT_PARAM = 'esporta';
export const DEFAULT_EXPORT_NAME = 'diario-gdr.zip';

const UNAUTHORIZED = 401;

export async function downloadUserExport(api: ApiClient): Promise<DownloadedFile> {
	const { data, error, response } = await api.GET('/me/export', { parseAs: 'blob' });
	if (response.status === UNAUTHORIZED) {
		throw new RecentLoginRequiredError();
	}
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a preparare la copia dei tuoi dati.'));
	}
	return {
		blob: data,
		fileName: fileNameFromDisposition(
			response.headers.get('Content-Disposition'),
			DEFAULT_EXPORT_NAME
		)
	};
}

export function exportReturnUrl(origin: string): string {
	const back = new URL(resolve('/(app)/profile'), origin);
	back.searchParams.set(EXPORT_PARAM, '1');
	return back.href;
}

export function wantsExport(href: string): boolean {
	return new URL(href).searchParams.get(EXPORT_PARAM) === '1';
}

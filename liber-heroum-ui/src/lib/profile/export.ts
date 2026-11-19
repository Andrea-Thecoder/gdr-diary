import { resolve } from '$app/paths';
import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import { fileNameFromDisposition, type DownloadedFile } from '#lib/api/download.js';
import { RecentLoginRequiredError } from './api.js';

export const DEFAULT_EXPORT_NAME = 'liber-heroum.zip';

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

export function profileUrl(origin: string): string {
	return new URL(resolve('/(app)/profile'), origin).href;
}

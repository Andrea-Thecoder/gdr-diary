import type { JSONContent } from '@tiptap/core';
import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import type { components } from '#lib/api/schema.js';
import { ConflictError, RejectedPageError, emptyDoc } from '#lib/book/pages.js';

export type BackgroundDto = components['schemas']['DetailCharacterBackgroundDTO'];

export class BackgroundLoadError extends Error {
	constructor(
		message: string,
		readonly status: number
	) {
		super(message);
		this.name = 'BackgroundLoadError';
	}
}

export interface BackgroundData {
	content: JSONContent;
	version: number;
}

const REQUEST_TIMEOUT = 408;
const TOO_MANY_REQUESTS = 429;
const CONFLICT = 409;

function isRejected(status: number): boolean {
	return (
		status >= 400 && status < 500 && status !== REQUEST_TIMEOUT && status !== TOO_MANY_REQUESTS
	);
}

export function backgroundFromDto(dto: BackgroundDto): BackgroundData {
	const content: JSONContent = dto.content ?? {};
	return {
		content: content.type === 'doc' ? content : emptyDoc(),
		version: dto.version ?? 0
	};
}

export async function fetchBackground(
	api: ApiClient,
	characterId: string
): Promise<BackgroundData> {
	const { data, error, response } = await api.GET('/characters/{characterId}/background', {
		params: { path: { characterId } }
	});
	if (data === undefined) {
		throw new BackgroundLoadError(
			describeApiError(error, 'Non riesco a caricare la storia del personaggio.'),
			response.status
		);
	}
	return backgroundFromDto(data);
}

export async function saveBackground(
	api: ApiClient,
	characterId: string,
	content: JSONContent,
	version: number
): Promise<BackgroundData> {
	const { data, error, response } = await api.PUT('/characters/{characterId}/background', {
		params: { path: { characterId } },
		body: { content, version }
	});
	if (data === undefined) {
		if (response.status === CONFLICT) {
			throw new ConflictError(describeApiError(error, 'La storia è stata modificata altrove.'));
		}
		const message = describeApiError(error, 'Non riesco a salvare la storia.');
		throw isRejected(response.status) ? new RejectedPageError(message) : new Error(message);
	}
	return backgroundFromDto(data);
}

export interface BackgroundPdfFile {
	blob: Blob;
	fileName: string;
}

export const DEFAULT_PDF_NAME = 'background.pdf';

export function fileNameFrom(disposition: string | null): string {
	const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition ?? '');
	if (encoded?.[1] !== undefined) {
		try {
			return decodeURIComponent(encoded[1]);
		} catch {
			return DEFAULT_PDF_NAME;
		}
	}
	const plain = /filename="([^"]+)"/i.exec(disposition ?? '');
	return plain?.[1] ?? DEFAULT_PDF_NAME;
}

export async function downloadBackgroundPdf(
	api: ApiClient,
	characterId: string
): Promise<BackgroundPdfFile> {
	const { data, error, response } = await api.GET('/characters/{characterId}/background/pdf', {
		params: { path: { characterId } },
		parseAs: 'blob'
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a preparare il PDF della storia.'));
	}
	return { blob: data, fileName: fileNameFrom(response.headers.get('Content-Disposition')) };
}

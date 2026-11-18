import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '#lib/api/client.js';
import { ConflictError, RejectedPageError, emptyDoc } from '#lib/book/pages.js';
import { doc } from '#lib/book/test-support.js';
import {
	DEFAULT_PDF_NAME,
	backgroundFromDto,
	downloadBackgroundPdf,
	fetchBackground,
	fileNameFrom,
	saveBackground
} from './api.js';

type Call = (...args: unknown[]) => Promise<unknown>;

function clientWith(method: 'GET' | 'PUT', answer: unknown): { api: ApiClient; call: Call } {
	const call = vi.fn<Call>().mockResolvedValue(answer);
	const api = { [method]: call } as unknown as ApiClient;
	return { api, call };
}

describe('backgroundFromDto', () => {
	it('keeps the document and the version', () => {
		expect(backgroundFromDto({ content: doc('ciao'), version: 4, textLength: 4 })).toEqual({
			content: doc('ciao'),
			version: 4
		});
	});

	it('falls back to an empty page at version zero', () => {
		expect(backgroundFromDto({})).toEqual({ content: emptyDoc(), version: 0 });
	});

	it('replaces a content that is not a document with an empty page', () => {
		expect(backgroundFromDto({ content: { type: 'paragraph' }, version: 2 }).content).toEqual(
			emptyDoc()
		);
	});
});

describe('fetchBackground', () => {
	it('asks for the background of the character', async () => {
		const { api, call } = clientWith('GET', {
			data: { content: doc('ciao'), version: 1 },
			response: { status: 200 }
		});
		await fetchBackground(api, 'c1');
		expect(call).toHaveBeenCalledWith('/characters/{characterId}/background', {
			params: { path: { characterId: 'c1' } }
		});
	});

	it('returns the background it received', async () => {
		const { api } = clientWith('GET', {
			data: { content: doc('ciao'), version: 1 },
			response: { status: 200 }
		});
		expect(await fetchBackground(api, 'c1')).toEqual({ content: doc('ciao'), version: 1 });
	});

	it('explains a failure in Italian', async () => {
		const { api } = clientWith('GET', {
			error: { title: 'NOT_FOUND', status: 404, violations: [{ message: 'Character not found' }] },
			response: { status: 404 }
		});
		await expect(fetchBackground(api, 'c1')).rejects.toThrow('Personaggio non trovato');
	});

	it('keeps the status of the server in the failure', async () => {
		const { api } = clientWith('GET', {
			error: { title: 'INTERNAL_ERROR', status: 500, violations: [] },
			response: { status: 500 }
		});

		await expect(fetchBackground(api, 'c1')).rejects.toMatchObject({ status: 500 });
	});
});

describe('saveBackground', () => {
	it('sends the content with the version that was read', async () => {
		const { api, call } = clientWith('PUT', {
			data: { content: doc('nuovo'), version: 2 },
			response: { status: 200 }
		});
		await saveBackground(api, 'c1', doc('nuovo'), 1);
		expect(call).toHaveBeenCalledWith('/characters/{characterId}/background', {
			params: { path: { characterId: 'c1' } },
			body: { content: doc('nuovo'), version: 1 }
		});
	});

	it('returns the new version', async () => {
		const { api } = clientWith('PUT', {
			data: { content: doc('nuovo'), version: 2 },
			response: { status: 200 }
		});
		expect((await saveBackground(api, 'c1', doc('nuovo'), 1)).version).toBe(2);
	});

	it('reports a conflict when the story changed elsewhere', async () => {
		const { api } = clientWith('PUT', { error: { title: 'CONFLICT' }, response: { status: 409 } });
		await expect(saveBackground(api, 'c1', doc('x'), 1)).rejects.toBeInstanceOf(ConflictError);
	});

	it.each([400, 404, 413])('reports a refusal when the answer is %s', async (status) => {
		const { api } = clientWith('PUT', { error: { title: 'BAD' }, response: { status } });
		await expect(saveBackground(api, 'c1', doc('x'), 1)).rejects.toBeInstanceOf(RejectedPageError);
	});

	it.each([408, 429, 500, 503])('treats a %s as worth retrying', async (status) => {
		const { api } = clientWith('PUT', { error: { title: 'BAD' }, response: { status } });
		const failure = await saveBackground(api, 'c1', doc('x'), 1).catch((cause: unknown) => cause);
		expect(failure).not.toBeInstanceOf(RejectedPageError);
	});
});

describe('fileNameFrom', () => {
	it.each([
		['attachment; filename="background-alira.pdf"', 'background-alira.pdf'],
		[
			'attachment; filename="background-elodie.pdf"; filename*=UTF-8\'\'background-%C3%A9lodie.pdf',
			'background-élodie.pdf'
		],
		["attachment; filename*=UTF-8''%E0%A4%A", DEFAULT_PDF_NAME],
		['attachment', DEFAULT_PDF_NAME],
		[null, DEFAULT_PDF_NAME]
	])('reads %s as %s', (header, name) => {
		expect(fileNameFrom(header)).toBe(name);
	});
});

describe('downloadBackgroundPdf', () => {
	it('returns the file and its name', async () => {
		const blob = new Blob(['%PDF-']);
		const { api } = clientWith('GET', {
			data: blob,
			response: new Response(null, {
				headers: { 'Content-Disposition': 'attachment; filename="background-alira.pdf"' }
			})
		});

		expect(await downloadBackgroundPdf(api, 'c1')).toEqual({
			blob,
			fileName: 'background-alira.pdf'
		});
	});

	it('explains a failure in Italian', async () => {
		const { api } = clientWith('GET', {
			error: { title: 'NOT_FOUND', status: 404, violations: [{ message: 'Background not found' }] },
			response: new Response(null, { status: 404 })
		});

		await expect(downloadBackgroundPdf(api, 'c1')).rejects.toThrow(
			'Questa storia non è ancora stata scritta'
		);
	});
});

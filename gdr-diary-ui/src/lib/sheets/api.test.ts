import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApiClient } from '#lib/api/client.js';
import { FakeXhr } from '#lib/api/xhr-support.js';
import {
	createSheet,
	deleteSheet,
	fetchSheet,
	fetchSheets,
	replaceSheetFile,
	updateSheet,
	type SheetFields
} from '#lib/sheets/api.js';

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	});
}

function clientWith(handler: (request: Request) => Response): {
	api: ReturnType<typeof createApiClient>;
	requests: Request[];
} {
	const requests: Request[] = [];
	const fetchMock = vi.fn<typeof globalThis.fetch>((input) => {
		const request = input as Request;
		requests.push(request.clone());
		return Promise.resolve(handler(request));
	});
	const api = createApiClient({
		baseUrl: 'http://api.test/base',
		getToken: () => Promise.resolve('token'),
		fetch: fetchMock
	});
	return { api, requests };
}

const fields: SheetFields = { title: '  Scheda livello 5 ', system: ' D&D 5e ' };
const pdf = new File(['%PDF-1.7'], 'scheda.pdf', { type: 'application/pdf' });

describe('fetchSheets', () => {
	it('sends the filters, sort and page for the character and maps the result', async () => {
		const { api, requests } = clientWith(() =>
			json({ list: [{ id: 's1', title: 'Scheda' }], totalRows: 13, totalPages: 2, page: 1 })
		);

		const result = await fetchSheets(api, 'c1', {
			title: ' Sch ',
			system: 'D&D 5e',
			descending: true,
			page: 2
		});

		const url = new URL(requests[0]?.url ?? '');
		expect(url.pathname).toBe('/base/characters/c1/sheets');
		expect(url.searchParams.get('title')).toBe('Sch');
		expect(url.searchParams.get('system')).toBe('D&D 5e');
		expect(url.searchParams.get('sort')).toBe('createdAt');
		expect(url.searchParams.get('descending')).toBe('true');
		expect(url.searchParams.get('page')).toBe('2');
		expect(url.searchParams.get('size')).toBe('12');
		expect(result).toEqual({
			items: [{ id: 's1', title: 'Scheda' }],
			totalRows: 13,
			totalPages: 2,
			page: 1
		});
	});

	it('leaves out an empty title and an empty system', async () => {
		const { api, requests } = clientWith(() => json({ list: [] }));

		await fetchSheets(api, 'c1', { title: '  ', system: '', descending: false, page: 1 });

		const params = new URL(requests[0]?.url ?? '').searchParams;
		expect(params.has('title')).toBe(false);
		expect(params.has('system')).toBe(false);
	});

	it('throws a readable error when the call fails', async () => {
		const { api } = clientWith(() => json({ title: 'Boom', status: 500 }, 500));

		await expect(
			fetchSheets(api, 'c1', { title: '', system: '', descending: false, page: 1 })
		).rejects.toThrow();
	});
});

afterEach(() => {
	vi.unstubAllGlobals();
});

describe('createSheet', () => {
	it('posts the PDF, the trimmed title and the system as multipart form data', async () => {
		const { api, requests } = clientWith(() => json({ id: 's1', title: 'Scheda livello 5' }, 201));

		const created = await createSheet(api, 'c1', fields, pdf);

		expect(created).toEqual({ id: 's1', title: 'Scheda livello 5' });
		expect(requests[0]?.method).toBe('POST');
		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/characters/c1/sheets');
		const form = await requests[0]?.formData();
		expect(form?.get('title')).toBe('Scheda livello 5');
		expect(form?.get('system')).toBe('D&D 5e');
		expect((form?.get('file') as File).name).toBe('scheda.pdf');
	});

	it('does not send the system when it is empty', async () => {
		const { api, requests } = clientWith(() => json({ id: 's1' }, 201));

		await createSheet(api, 'c1', { title: 'A', system: '  ' }, pdf);

		expect((await requests[0]?.formData())?.has('system')).toBe(false);
	});

	it('throws the message of the API when the upload is refused', async () => {
		const { api } = clientWith(() => json({ title: 'File troppo grande', status: 413 }, 413));

		await expect(createSheet(api, 'c1', fields, pdf)).rejects.toThrow('File troppo grande');
	});

	it('reports the progress of the upload when asked to', async () => {
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		const { api, requests } = clientWith(() => json({}));
		const onProgress = vi.fn<(fraction: number) => void>();
		const sent = FakeXhr.nextSent();

		const upload = createSheet(api, 'c1', fields, pdf, onProgress);
		const xhr = await sent;
		xhr.progress(1, 4);
		xhr.finish(201, '{"id":"s1","title":"Scheda livello 5"}');

		expect(await upload).toEqual({ id: 's1', title: 'Scheda livello 5' });
		expect(onProgress).toHaveBeenCalledWith(0.25);
		expect(xhr.headers['authorization']).toBe('Bearer token');
		expect(requests).toHaveLength(0);
	});
});

describe('updateSheet', () => {
	it('saves the fields only when there is no new PDF', async () => {
		const { api, requests } = clientWith(() => json({ id: 's1', title: 'Scheda livello 5' }));

		const result = await updateSheet(api, 's1', fields, null);

		expect(result).toEqual({ sheet: { id: 's1', title: 'Scheda livello 5' }, fileError: null });
		expect(requests).toHaveLength(1);
		expect(requests[0]?.method).toBe('PUT');
		expect(await requests[0]?.json()).toEqual({ title: 'Scheda livello 5', system: 'D&D 5e' });
	});

	it('clears the system when it is empty', async () => {
		const { api, requests } = clientWith(() => json({ id: 's1' }));

		await updateSheet(api, 's1', { title: 'A', system: ' ' }, null);

		expect(await requests[0]?.json()).toEqual({ title: 'A', system: null });
	});

	it('replaces the PDF after saving the fields and returns the updated sheet', async () => {
		const { api, requests } = clientWith((request) =>
			json(
				new URL(request.url).pathname.endsWith('/file') ? { id: 's1', sizeBytes: 5 } : { id: 's1' }
			)
		);

		const result = await updateSheet(api, 's1', fields, pdf);

		expect(result).toEqual({ sheet: { id: 's1', sizeBytes: 5 }, fileError: null });
		expect(new URL(requests[1]?.url ?? '').pathname).toBe('/base/sheets/s1/file');
		expect((await requests[1]?.formData())?.get('file')).toBeInstanceOf(File);
	});

	it('keeps the saved fields and reports the PDF error when only the PDF fails', async () => {
		const { api } = clientWith((request) =>
			new URL(request.url).pathname.endsWith('/file')
				? json({ title: 'File non valido', status: 400 }, 400)
				: json({ id: 's1', title: 'Scheda livello 5' })
		);

		const result = await updateSheet(api, 's1', fields, pdf);

		expect(result.sheet).toEqual({ id: 's1', title: 'Scheda livello 5' });
		expect(result.fileError).toBe('File non valido');
	});

	it('throws when the fields cannot be saved, without touching the PDF', async () => {
		const { api, requests } = clientWith(() => json({ title: 'Boom', status: 500 }, 500));

		await expect(updateSheet(api, 's1', fields, pdf)).rejects.toThrow();
		expect(requests).toHaveLength(1);
	});
});

describe('deleteSheet', () => {
	it('deletes the sheet', async () => {
		const { api, requests } = clientWith(() => new Response(null, { status: 204 }));

		await deleteSheet(api, 's1');

		expect(requests[0]?.method).toBe('DELETE');
		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/sheets/s1');
	});

	it('treats a sheet that is already gone as deleted', async () => {
		const { api } = clientWith(() => json({ title: 'Non trovata', status: 404 }, 404));

		await expect(deleteSheet(api, 's1')).resolves.toBeUndefined();
	});

	it('throws on any other failure', async () => {
		const { api } = clientWith(() => json({ title: 'Boom', status: 500 }, 500));

		await expect(deleteSheet(api, 's1')).rejects.toThrow();
	});
});

describe('fetchSheet', () => {
	it('reads one sheet with its fresh file addresses', async () => {
		const { api, requests } = clientWith(() =>
			json({ id: 's1', title: 'Scheda', url: 'https://files.test/s1.pdf' })
		);

		const sheet = await fetchSheet(api, 's1');

		expect(sheet.url).toBe('https://files.test/s1.pdf');
		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/sheets/s1');
	});

	it('says why the sheet cannot be read', async () => {
		const { api } = clientWith(() => json({ title: 'Scheda non trovata', status: 404 }, 404));

		await expect(fetchSheet(api, 's1')).rejects.toThrow('Scheda non trovata');
	});
});

describe('replaceSheetFile', () => {
	it('sends the new PDF over the old one as a file upload', async () => {
		const { api, requests } = clientWith(() => json({ id: 's1', title: 'Scheda' }));

		await replaceSheetFile(api, 's1', pdf);

		const request = requests[0];
		expect(request?.method).toBe('PUT');
		expect(new URL(request?.url ?? '').pathname).toBe('/base/sheets/s1/file');
		const sent = (await request?.formData())?.get('file') as File;
		expect(sent.name).toBe('scheda.pdf');
		expect(await sent.text()).toBe('%PDF-1.7');
	});

	it('says why the PDF was refused', async () => {
		const { api } = clientWith(() => json({ title: 'File troppo grande', status: 413 }, 413));

		await expect(replaceSheetFile(api, 's1', pdf)).rejects.toThrow('File troppo grande');
	});
});

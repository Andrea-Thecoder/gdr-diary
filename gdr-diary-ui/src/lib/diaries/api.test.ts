import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from '#lib/api/client.js';
import {
	createDiary,
	deleteDiary,
	fetchDiaries,
	fetchDiary,
	updateDiary,
	type DiaryFields
} from '#lib/diaries/api.js';

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

const fields: DiaryFields = {
	title: '  Cronache del Nord ',
	description: '',
	coverColor: '#3B2A6B',
	font: '',
	fontSize: ''
};

describe('fetchDiaries', () => {
	it('sends the title, sort and page for the character and maps the result', async () => {
		const { api, requests } = clientWith(() =>
			json({ list: [{ id: 'd1', title: 'Cronache' }], totalRows: 13, totalPages: 2, page: 1 })
		);

		const result = await fetchDiaries(api, 'c1', {
			title: ' Cron ',
			tag: ' erebo ',
			descending: true,
			page: 2
		});

		const url = new URL(requests[0]?.url ?? '');
		expect(url.pathname).toBe('/base/characters/c1/diaries');
		expect(url.searchParams.get('title')).toBe('Cron');
		expect(url.searchParams.get('tag')).toBe('erebo');
		expect(url.searchParams.get('sort')).toBe('title');
		expect(url.searchParams.get('descending')).toBe('true');
		expect(url.searchParams.get('page')).toBe('2');
		expect(url.searchParams.get('size')).toBe('12');
		expect(result).toEqual({
			items: [{ id: 'd1', title: 'Cronache' }],
			totalRows: 13,
			totalPages: 2,
			page: 1
		});
	});

	it('leaves out an empty title and an empty tag', async () => {
		const { api, requests } = clientWith(() => json({ list: [] }));

		await fetchDiaries(api, 'c1', { title: '  ', tag: '  ', descending: false, page: 1 });

		const url = new URL(requests[0]?.url ?? '');
		expect(url.searchParams.has('title')).toBe(false);
		expect(url.searchParams.has('tag')).toBe(false);
	});

	it('throws a readable error when the call fails', async () => {
		const { api } = clientWith(() => json({ title: 'Boom', status: 500 }, 500));

		await expect(
			fetchDiaries(api, 'c1', { title: '', tag: '', descending: false, page: 1 })
		).rejects.toThrow();
	});
});

describe('createDiary', () => {
	it('sends only the filled fields', async () => {
		const { api, requests } = clientWith(() => json({ id: 'd1', title: 'Cronache del Nord' }, 201));

		const result = await createDiary(api, 'c1', fields, null);

		expect(requests[0]?.method).toBe('POST');
		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/characters/c1/diaries');
		expect(await requests[0]?.json()).toEqual({
			title: 'Cronache del Nord',
			coverColor: '#3B2A6B'
		});
		expect(result).toEqual({ diary: { id: 'd1', title: 'Cronache del Nord' }, coverError: null });
	});

	it('sends font and size as a name and a number', async () => {
		const { api, requests } = clientWith(() => json({ id: 'd1' }, 201));

		await createDiary(
			api,
			'c1',
			{ ...fields, font: 'Caveat', fontSize: '18', description: ' Note ' },
			null
		);

		expect(await requests[0]?.json()).toEqual({
			title: 'Cronache del Nord',
			coverColor: '#3B2A6B',
			description: 'Note',
			font: 'Caveat',
			fontSize: 18
		});
	});

	it('uploads the cover after creating and keeps the diary if the upload fails', async () => {
		const { api, requests } = clientWith((request) =>
			request.method === 'POST'
				? json({ id: 'd1', title: 'Cronache' }, 201)
				: json({ title: 'Troppo grande', status: 413 }, 413)
		);

		const result = await createDiary(
			api,
			'c1',
			fields,
			new File(['x'], 'c.png', { type: 'image/png' })
		);

		expect(requests.map((request) => request.method)).toEqual(['POST', 'PUT']);
		expect(new URL(requests[1]?.url ?? '').pathname).toBe('/base/diaries/d1/cover');
		expect(result.diary.id).toBe('d1');
		expect(result.coverError).not.toBeNull();
	});

	it('throws when the diary cannot be created', async () => {
		const { api } = clientWith(() => json({ title: 'Bad', status: 400 }, 400));

		await expect(createDiary(api, 'c1', fields, null)).rejects.toThrow();
	});
});

describe('fetchDiary', () => {
	it('reads one diary', async () => {
		const { api, requests } = clientWith(() =>
			json({ id: 'd1', title: 'Cronache', description: 'x' })
		);

		const diary = await fetchDiary(api, 'd1');

		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/diaries/d1');
		expect(diary.description).toBe('x');
	});
});

describe('updateDiary', () => {
	it('replaces the fields without touching the cover', async () => {
		const { api, requests } = clientWith(() => json({ id: 'd1', title: 'Cronache del Nord' }));

		const result = await updateDiary(api, 'd1', fields, { type: 'keep' });

		expect(requests.map((request) => request.method)).toEqual(['PUT']);
		expect(result.coverError).toBeNull();
	});

	it('removes the cover when asked', async () => {
		const { api, requests } = clientWith((request) =>
			request.method === 'DELETE' ? json({}) : json({ id: 'd1' })
		);

		const result = await updateDiary(api, 'd1', fields, { type: 'remove' });

		expect(requests.map((request) => request.method)).toEqual(['PUT', 'DELETE']);
		expect(new URL(requests[1]?.url ?? '').pathname).toBe('/base/diaries/d1/cover');
		expect(result.coverError).toBeNull();
	});

	it('uploads a new cover when asked', async () => {
		const { api, requests } = clientWith(() => json({ id: 'd1' }));

		await updateDiary(api, 'd1', fields, {
			type: 'replace',
			file: new File(['x'], 'c.png', { type: 'image/png' })
		});

		expect(requests.map((request) => request.method)).toEqual(['PUT', 'PUT']);
		expect(new URL(requests[1]?.url ?? '').pathname).toBe('/base/diaries/d1/cover');
	});

	it('reports a failed cover change but keeps the saved diary', async () => {
		const { api } = clientWith((request) =>
			request.method === 'DELETE' ? json({ title: 'No', status: 500 }, 500) : json({ id: 'd1' })
		);

		const result = await updateDiary(api, 'd1', fields, { type: 'remove' });

		expect(result.diary.id).toBe('d1');
		expect(result.coverError).not.toBeNull();
	});

	it('throws when the diary cannot be saved', async () => {
		const { api } = clientWith(() => json({ title: 'Bad', status: 400 }, 400));

		await expect(updateDiary(api, 'd1', fields, { type: 'keep' })).rejects.toThrow();
	});
});

describe('deleteDiary', () => {
	it('calls DELETE /diaries/{id}', async () => {
		const { api, requests } = clientWith(() => json({}));

		await deleteDiary(api, 'd1');

		expect(requests[0]?.method).toBe('DELETE');
		expect(new URL(requests[0]?.url ?? '').pathname).toBe('/base/diaries/d1');
	});

	it('throws a readable error when the server refuses', async () => {
		const { api } = clientWith(() => json({ message: 'boom' }, 500));

		await expect(deleteDiary(api, 'd1')).rejects.toThrow();
	});

	it('counts a missing diario as deleted', async () => {
		const { api } = clientWith(() => json({ title: 'Diary not found' }, 404));

		await expect(deleteDiary(api, 'd1')).resolves.toBeUndefined();
	});

	it('translates the conflict of a change during the deletion', async () => {
		const { api } = clientWith(() =>
			json(
				{ violations: [{ message: 'The diary changed while it was being deleted, try again' }] },
				409
			)
		);

		await expect(deleteDiary(api, 'd1')).rejects.toThrow(
			'Il diario è cambiato mentre lo eliminavi: riprova'
		);
	});
});

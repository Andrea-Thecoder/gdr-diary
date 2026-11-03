import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from '#lib/api/client.js';
import {
	createCharacter,
	deleteCharacter,
	fetchCharacter,
	fetchCharacters,
	updateCharacter
} from '#lib/characters/api.js';

function json(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' }
	});
}

function clientWith(handler: (request: Request) => Response): {
	api: ReturnType<typeof createApiClient>;
	fetchMock: ReturnType<typeof vi.fn<typeof globalThis.fetch>>;
} {
	const fetchMock = vi.fn<typeof globalThis.fetch>((input) => {
		const request = input as Request;
		return Promise.resolve(handler(request));
	});
	const api = createApiClient({
		baseUrl: 'http://api.test/base',
		getToken: () => Promise.resolve('token'),
		fetch: fetchMock
	});
	return { api, fetchMock };
}

describe('fetchCharacters', () => {
	it('sends filters, sort and page and maps the result', async () => {
		const { api, fetchMock } = clientWith(() =>
			json({
				list: [{ id: '1', name: 'Alira' }],
				totalRows: 13,
				totalPages: 2,
				page: 1,
				pageSize: 12
			})
		);

		const result = await fetchCharacters(api, {
			name: ' Ali ',
			campaignName: '',
			descending: true,
			page: 1
		});

		const url = new URL((fetchMock.mock.calls[0]?.[0] as Request).url);
		expect(url.pathname).toBe('/base/characters');
		expect(url.searchParams.get('name')).toBe('Ali');
		expect(url.searchParams.has('campaignName')).toBe(false);
		expect(url.searchParams.get('sort')).toBe('name');
		expect(url.searchParams.get('descending')).toBe('true');
		expect(url.searchParams.get('page')).toBe('1');
		expect(url.searchParams.get('size')).toBe('12');
		expect(result).toEqual({
			items: [{ id: '1', name: 'Alira' }],
			totalRows: 13,
			totalPages: 2,
			page: 1
		});
	});

	it('throws the server message on failure', async () => {
		const { api } = clientWith(() =>
			json({ title: 'Bad request', status: 400, violations: [{ message: 'size too big' }] }, 400)
		);

		await expect(
			fetchCharacters(api, { name: '', campaignName: '', descending: false, page: 1 })
		).rejects.toThrow('size too big');
	});
});

describe('createCharacter', () => {
	const input = {
		kind: 'PLAYER' as const,
		name: ' Alira ',
		description: '',
		campaignName: ' Nord ',
		backgroundColor: '#3B2A6B'
	};

	it('creates the character without optional empty fields', async () => {
		const { api, fetchMock } = clientWith(() => json({ id: 'abc', name: 'Alira' }, 201));

		const result = await createCharacter(api, input, null);

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('POST');
		expect(await request.json()).toEqual({
			kind: 'PLAYER',
			name: 'Alira',
			campaignName: 'Nord',
			backgroundColor: '#3B2A6B'
		});
		expect(result.avatarError).toBeNull();
		expect(fetchMock).toHaveBeenCalledTimes(1);
	});

	it('sends the game master kind', async () => {
		const { api, fetchMock } = clientWith(() => json({ id: 'abc', name: 'Alira' }, 201));

		await createCharacter(api, { ...input, kind: 'GAME_MASTER' }, null);

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(((await request.json()) as { kind: string }).kind).toBe('GAME_MASTER');
	});

	it('uploads the avatar as multipart in a second call', async () => {
		const { api, fetchMock } = clientWith((request) =>
			request.method === 'POST' ? json({ id: 'abc', name: 'Alira' }, 201) : json({ id: 'abc' })
		);
		const file = new File(['x'], 'volto.png', { type: 'image/png' });

		const result = await createCharacter(api, input, file);

		const second = fetchMock.mock.calls[1]?.[0] as Request;
		expect(second.method).toBe('PUT');
		expect(new URL(second.url).pathname).toBe('/base/characters/abc/avatar');
		expect(second.headers.get('Content-Type')).toContain('multipart/form-data');
		expect(result.avatarError).toBeNull();
	});

	it('keeps the character and reports an avatar failure', async () => {
		const { api } = clientWith((request) =>
			request.method === 'POST'
				? json({ id: 'abc', name: 'Alira' }, 201)
				: json({ title: 'Unsupported', status: 400 }, 400)
		);
		const file = new File(['x'], 'volto.png', { type: 'image/png' });

		const result = await createCharacter(api, input, file);

		expect(result.character.id).toBe('abc');
		expect(result.avatarError).toBe('Unsupported');
	});

	it('throws when the creation fails', async () => {
		const { api } = clientWith(() =>
			json({ title: 'Bad request', status: 400, violations: [{ message: 'name blank' }] }, 400)
		);

		await expect(createCharacter(api, input, null)).rejects.toThrow('name blank');
	});
});

describe('fetchCharacter', () => {
	it('loads the detail of a character', async () => {
		const { api, fetchMock } = clientWith(() =>
			json({ id: 'abc', name: 'Alira', description: 'Elfa' })
		);

		const result = await fetchCharacter(api, 'abc');

		expect(new URL((fetchMock.mock.calls[0]?.[0] as Request).url).pathname).toBe(
			'/base/characters/abc'
		);
		expect(result.description).toBe('Elfa');
	});

	it('throws when the character is not found', async () => {
		const { api } = clientWith(() => json({ title: 'Not found', status: 404 }, 404));

		await expect(fetchCharacter(api, 'abc')).rejects.toThrow('Not found');
	});
});

describe('updateCharacter', () => {
	const input = {
		name: ' Alira ',
		description: ' Elfa ',
		campaignName: '',
		backgroundColor: '#3B2A6B'
	};

	it('replaces the editable fields and leaves the avatar alone', async () => {
		const { api, fetchMock } = clientWith(() => json({ id: 'abc', name: 'Alira' }));

		const result = await updateCharacter(api, 'abc', input, { type: 'keep' });

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('PUT');
		expect(new URL(request.url).pathname).toBe('/base/characters/abc');
		expect(await request.json()).toEqual({
			name: 'Alira',
			description: 'Elfa',
			campaignName: '',
			backgroundColor: '#3B2A6B'
		});
		expect(result.avatarError).toBeNull();
		expect(fetchMock).toHaveBeenCalledTimes(1);
	});

	it('uploads a new avatar after saving', async () => {
		const { api, fetchMock } = clientWith(() => json({ id: 'abc', name: 'Alira' }));
		const file = new File(['x'], 'volto.png', { type: 'image/png' });

		await updateCharacter(api, 'abc', input, { type: 'replace', file });

		const second = fetchMock.mock.calls[1]?.[0] as Request;
		expect(second.method).toBe('PUT');
		expect(new URL(second.url).pathname).toBe('/base/characters/abc/avatar');
	});

	it('removes the avatar after saving', async () => {
		const { api, fetchMock } = clientWith(() => json({ id: 'abc', name: 'Alira' }));

		const result = await updateCharacter(api, 'abc', input, { type: 'remove' });

		const second = fetchMock.mock.calls[1]?.[0] as Request;
		expect(second.method).toBe('DELETE');
		expect(new URL(second.url).pathname).toBe('/base/characters/abc/avatar');
		expect(result.avatarError).toBeNull();
	});

	it('keeps the saved character and reports an avatar failure', async () => {
		const { api } = clientWith((request) =>
			request.method === 'DELETE'
				? json({ title: 'Storage down', status: 500 }, 500)
				: json({ id: 'abc', name: 'Alira' })
		);

		const result = await updateCharacter(api, 'abc', input, { type: 'remove' });

		expect(result.character.id).toBe('abc');
		expect(result.avatarError).toBe('Storage down');
	});

	it('throws when saving fails', async () => {
		const { api } = clientWith(() =>
			json({ title: 'Bad request', status: 400, violations: [{ message: 'name blank' }] }, 400)
		);

		await expect(updateCharacter(api, 'abc', input, { type: 'keep' })).rejects.toThrow(
			'name blank'
		);
	});
});

describe('deleteCharacter', () => {
	it('calls DELETE /characters/{id}', async () => {
		const { api, fetchMock } = clientWith(() => json({}));

		await deleteCharacter(api, 'abc');

		const request = fetchMock.mock.calls[0]?.[0] as Request;
		expect(request.method).toBe('DELETE');
		expect(new URL(request.url).pathname).toBe('/base/characters/abc');
	});

	it('throws a readable error when the server refuses', async () => {
		const { api } = clientWith(() => json({ message: 'boom' }, 500));

		await expect(deleteCharacter(api, 'abc')).rejects.toThrow();
	});

	it('counts a missing personaggio as deleted', async () => {
		const { api } = clientWith(() => json({ title: 'Character not found' }, 404));

		await expect(deleteCharacter(api, 'abc')).resolves.toBeUndefined();
	});

	it('translates the conflict of a change during the deletion', async () => {
		const { api } = clientWith(() =>
			json(
				{
					violations: [{ message: 'The character changed while it was being deleted, try again' }]
				},
				409
			)
		);

		await expect(deleteCharacter(api, 'abc')).rejects.toThrow(
			'Il personaggio è cambiato mentre lo eliminavi: riprova'
		);
	});
});

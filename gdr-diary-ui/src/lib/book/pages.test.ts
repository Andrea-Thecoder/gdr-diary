import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '#lib/api/client.js';
import {
	ConflictError,
	MissingPageError,
	RejectedPageError,
	createPage,
	deletePage,
	emptyDoc,
	fetchSpread,
	hasText,
	sameContent,
	updatePage
} from './pages.js';

type Call = (...args: unknown[]) => Promise<unknown>;

function fakeApi(handlers: Partial<Record<'GET' | 'POST' | 'PUT' | 'DELETE', Call>>): ApiClient {
	return {
		GET: handlers.GET ?? vi.fn<Call>(),
		POST: handlers.POST ?? vi.fn<Call>(),
		PUT: handlers.PUT ?? vi.fn<Call>(),
		DELETE: handlers.DELETE ?? vi.fn<Call>()
	} as unknown as ApiClient;
}

const text = {
	type: 'doc',
	content: [{ type: 'paragraph', content: [{ type: 'text', text: 'ciao' }] }]
};

describe('hasText', () => {
	it('is false for an empty document and for blank text', () => {
		expect(hasText(emptyDoc())).toBe(false);
		expect(
			hasText({
				type: 'doc',
				content: [{ type: 'paragraph', content: [{ type: 'text', text: '  ' }] }]
			})
		).toBe(false);
		expect(hasText(undefined)).toBe(false);
	});

	it('is true when some node holds text, even nested', () => {
		expect(hasText(text)).toBe(true);
		expect(
			hasText({
				type: 'doc',
				content: [{ type: 'bulletList', content: [{ type: 'listItem', content: text.content }] }]
			})
		).toBe(true);
	});
});

describe('sameContent', () => {
	it('compares documents by value', () => {
		expect(sameContent(text, structuredClone(text))).toBe(true);
		expect(sameContent(text, emptyDoc())).toBe(false);
	});
});

describe('fetchSpread', () => {
	it('asks for the spread and returns the pages with the total', async () => {
		const GET = vi.fn<Call>().mockResolvedValue({
			data: {
				list: [
					{ id: 'a', pageNumber: 7, content: text },
					{ id: 'b', pageNumber: 8, content: { foo: 1 } },
					{ pageNumber: 9, content: text }
				],
				totalRows: 23
			}
		});
		const result = await fetchSpread(fakeApi({ GET }), 'd1', 4);
		expect(GET).toHaveBeenCalledWith('/diaries/{diaryId}/pages', {
			params: { path: { diaryId: 'd1' }, query: { page: '4' } }
		});
		expect(result.totalPages).toBe(23);
		expect(result.pages.map((page) => page.pageNumber)).toEqual([7, 8]);
		expect(result.pages[1]?.content).toEqual(emptyDoc());
	});

	it('throws the message of the API on failure', async () => {
		const GET = vi.fn<Call>().mockResolvedValue({ error: { title: 'Diario non trovato' } });
		await expect(fetchSpread(fakeApi({ GET }), 'd1', 1)).rejects.toThrow('Diario non trovato');
	});
});

describe('createPage and updatePage', () => {
	it('posts the content at the end of the diary', async () => {
		const POST = vi
			.fn<Call>()
			.mockResolvedValue({ data: { id: 'n', pageNumber: 24, content: text } });
		const page = await createPage(fakeApi({ POST }), 'd1', text);
		expect(POST).toHaveBeenCalledWith('/diaries/{diaryId}/pages', {
			params: { path: { diaryId: 'd1' } },
			body: { content: text }
		});
		expect(page.pageNumber).toBe(24);
	});

	it('puts the content and the version on an existing page and gives back the new version', async () => {
		const PUT = vi
			.fn<Call>()
			.mockResolvedValue({ data: { id: 'a', pageNumber: 7, content: text, version: 6 } });
		const page = await updatePage(fakeApi({ PUT }), 'a', text, 5);
		expect(PUT).toHaveBeenCalledWith('/pages/{id}', {
			params: { path: { id: 'a' } },
			body: { content: text, version: 5 }
		});
		expect(page.id).toBe('a');
		expect(page.version).toBe(6);
	});

	it('tells a conflict apart from the other failures', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({
			error: { status: 409, title: 'La pagina è cambiata' },
			response: { status: 409 }
		});
		await expect(updatePage(fakeApi({ PUT }), 'a', text, 5)).rejects.toBeInstanceOf(ConflictError);
		const other = vi.fn<Call>().mockResolvedValue({
			error: { status: 400, title: 'Troppo grande' },
			response: { status: 400 }
		});
		const failure = await updatePage(fakeApi({ PUT: other }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);
		expect(failure).toBeInstanceOf(Error);
		expect(failure).not.toBeInstanceOf(ConflictError);
	});

	it('tells a page deleted elsewhere apart from the other failures', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({
			error: { status: 404, title: 'Pagina non trovata' },
			response: { status: 404 }
		});

		const failure = await updatePage(fakeApi({ PUT }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);

		expect(failure).toBeInstanceOf(MissingPageError);
		expect(failure).toHaveProperty(
			'message',
			'Questa pagina è stata eliminata altrove: copia il testo se ti serve e ricarica il diario.'
		);
	});

	it('tells a conflict apart even when the API sends no body', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ error: undefined, response: { status: 409 } });

		await expect(updatePage(fakeApi({ PUT }), 'a', text, 5)).rejects.toBeInstanceOf(ConflictError);
	});

	it('lets a network failure through as it is', async () => {
		const PUT = vi.fn<Call>().mockRejectedValue(new TypeError('Failed to fetch'));

		await expect(updatePage(fakeApi({ PUT }), 'a', text, 5)).rejects.toBeInstanceOf(TypeError);
	});

	it('does not treat a conflict on creation as a version conflict', async () => {
		const POST = vi.fn<Call>().mockResolvedValue({
			error: { status: 409, title: 'Diario pieno' },
			response: { status: 409 }
		});

		const failure = await createPage(fakeApi({ POST }), 'd1', text).catch(
			(cause: unknown) => cause
		);

		expect(failure).toBeInstanceOf(Error);
		expect(failure).not.toBeInstanceOf(ConflictError);
	});

	it('fails creation with the message of the API', async () => {
		const POST = vi
			.fn<Call>()
			.mockResolvedValue({ error: { title: 'Diario pieno' }, response: { status: 500 } });

		await expect(createPage(fakeApi({ POST }), 'd1', text)).rejects.toThrow('Diario pieno');
	});

	it('fails an update with the default message when the API sends no body', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 500 } });

		await expect(updatePage(fakeApi({ PUT }), 'a', text, 1)).rejects.toThrow(
			'Non riesco a salvare la pagina.'
		);
	});

	it('rejects an update refused with a 4xx as a rejected page with the message of the API', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({
			error: { status: 400, title: 'Troppo grande' },
			response: { status: 400 }
		});

		const failure = await updatePage(fakeApi({ PUT }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);

		expect(failure).toBeInstanceOf(RejectedPageError);
		expect(failure).toHaveProperty('message', 'Troppo grande');
	});

	it('rejects a creation refused with a 4xx as a rejected page', async () => {
		const POST = vi.fn<Call>().mockResolvedValue({
			error: { status: 422, violations: [{ message: 'Contenuto non valido' }] },
			response: { status: 422 }
		});

		const failure = await createPage(fakeApi({ POST }), 'd1', text).catch(
			(cause: unknown) => cause
		);

		expect(failure).toBeInstanceOf(RejectedPageError);
		expect(failure).toHaveProperty('message', 'Contenuto non valido');
	});

	it('keeps a server error on update as a plain error', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({
			error: { status: 500, title: 'Errore interno' },
			response: { status: 500 }
		});

		const failure = await updatePage(fakeApi({ PUT }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);

		expect(failure).not.toBeInstanceOf(RejectedPageError);
	});

	it.each([408, 429])('keeps a %i answer on update as a plain error to retry', async (status) => {
		const PUT = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status } });

		const failure = await updatePage(fakeApi({ PUT }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);

		expect(failure).not.toBeInstanceOf(RejectedPageError);
	});

	it.each([408, 429])('keeps a %i answer on creation as a plain error to retry', async (status) => {
		const POST = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status } });

		const failure = await createPage(fakeApi({ POST }), 'd1', text).catch(
			(cause: unknown) => cause
		);

		expect(failure).not.toBeInstanceOf(RejectedPageError);
	});

	it.each([401, 403])('rejects a %i answer on update', async (status) => {
		const PUT = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status } });

		const failure = await updatePage(fakeApi({ PUT }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);

		expect(failure).toBeInstanceOf(RejectedPageError);
	});

	it('keeps a server error on creation as a plain error', async () => {
		const POST = vi.fn<Call>().mockResolvedValue({
			error: { status: 503, title: 'Non disponibile' },
			response: { status: 503 }
		});

		const failure = await createPage(fakeApi({ POST }), 'd1', text).catch(
			(cause: unknown) => cause
		);

		expect(failure).not.toBeInstanceOf(RejectedPageError);
	});

	it('does not turn a conflict or a missing page on update into a rejected page', async () => {
		const conflict = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 409 } });
		const missing = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 404 } });

		const first = await updatePage(fakeApi({ PUT: conflict }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);
		const second = await updatePage(fakeApi({ PUT: missing }), 'a', text, 5).catch(
			(cause: unknown) => cause
		);

		expect([first, second].some((failure) => failure instanceof RejectedPageError)).toBe(false);
	});
});

describe('titles from old headings', () => {
	it('turns heading blocks saved earlier into paragraphs with inline titles', async () => {
		const get = vi.fn<Call>().mockResolvedValue({
			data: {
				totalRows: 1,
				list: [
					{
						id: 'p1',
						pageNumber: 1,
						content: {
							type: 'doc',
							content: [
								{
									type: 'heading',
									attrs: { level: 2, textAlign: 'center' },
									content: [{ type: 'text', text: 'asdasd', marks: [{ type: 'bold' }] }]
								},
								{ type: 'paragraph', content: [{ type: 'text', text: 'ciao' }] }
							]
						}
					}
				]
			}
		});
		const data = await fetchSpread(fakeApi({ GET: get }), 'd1', 1);

		expect(data.pages[0]?.content.content).toEqual([
			{
				type: 'paragraph',
				attrs: { textAlign: 'center' },
				content: [
					{
						type: 'text',
						text: 'asdasd',
						marks: [{ type: 'bold' }, { type: 'title', attrs: { level: 2 } }]
					}
				]
			},
			{ type: 'paragraph', content: [{ type: 'text', text: 'ciao' }] }
		]);
	});
});

describe('deletePage', () => {
	it('resolves when the API accepts the deletion', async () => {
		const DELETE = vi.fn<Call>().mockResolvedValue({ response: { ok: true, status: 204 } });

		await expect(deletePage(fakeApi({ DELETE }), 'a')).resolves.toBeUndefined();
	});

	it('calls the page endpoint with the id', async () => {
		const DELETE = vi.fn<Call>().mockResolvedValue({ response: { ok: true, status: 204 } });

		await deletePage(fakeApi({ DELETE }), 'a');

		expect(DELETE).toHaveBeenCalledWith('/pages/{id}', { params: { path: { id: 'a' } } });
	});

	it('throws the message of the API when the deletion is refused', async () => {
		const DELETE = vi.fn<Call>().mockResolvedValue({
			error: { title: 'Accesso negato' },
			response: { ok: false, status: 403 }
		});

		await expect(deletePage(fakeApi({ DELETE }), 'a')).rejects.toThrow('Accesso negato');
	});

	it('treats a page that is already gone as deleted', async () => {
		const DELETE = vi.fn<Call>().mockResolvedValue({
			error: { title: 'Pagina non trovata' },
			response: { ok: false, status: 404 }
		});

		await expect(deletePage(fakeApi({ DELETE }), 'a')).resolves.toBeUndefined();
	});
});

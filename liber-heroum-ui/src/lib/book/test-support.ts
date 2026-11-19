import type { JSONContent } from '@tiptap/core';
import type { Mock } from 'vitest';
import type { Draft } from './draft.js';
import type { SpreadData } from './pages.js';

export type ApiCall = (...args: unknown[]) => Promise<unknown>;

export interface BookApiMocks {
	GET: Mock<ApiCall>;
	POST: Mock<ApiCall>;
	PUT: Mock<ApiCall>;
}

export function doc(value: string): JSONContent {
	return {
		type: 'doc',
		content: [{ type: 'paragraph', content: [{ type: 'text', text: value }] }]
	};
}

export function spreadData(
	total: number,
	spread: number,
	text: (page: number) => string = (page) => `pagina ${String(page)}`
): SpreadData {
	const pages = [2 * spread - 1, 2 * spread]
		.filter((page) => page <= total)
		.map((page) => ({
			id: `p${String(page)}`,
			pageNumber: page,
			content: doc(text(page)),
			version: 3,
			tag: null
		}));
	return { pages, totalPages: total };
}

export function draftFixture(overrides: Partial<Draft> = {}): Draft {
	return {
		diaryId: 'd1',
		page: 1,
		pageId: 'p1',
		baseVersion: 3,
		content: doc('bozza'),
		savedAt: 1,
		...overrides
	};
}

export function serveBook(api: BookApiMocks, total: number, text?: (page: number) => string): void {
	api.GET.mockImplementation((path: unknown, init: unknown) => {
		if (path === '/diaries/{diaryId}/tags') {
			return Promise.resolve({ data: { list: [], totalRows: 0, totalPages: 0, page: 1 } });
		}
		const view = Number((init as { params: { query: { page: string } } }).params.query.page);
		const data = spreadData(total, view, text);
		return Promise.resolve({ data: { list: data.pages, totalRows: total } });
	});
	api.PUT.mockImplementation((path: unknown, init: unknown) => {
		if (path === '/pages/{id}/tag') {
			return Promise.resolve({
				data: { tag: (init as { body: { tag: string | null } }).body.tag }
			});
		}
		const id = (init as { params: { path: { id: string } } }).params.path.id;
		return Promise.resolve({ data: { id, pageNumber: Number(id.slice(1)), content: {} } });
	});
	let created = total;
	api.POST.mockImplementation(() => {
		created += 1;
		return Promise.resolve({
			data: { id: `p${String(created)}`, pageNumber: created, content: {} }
		});
	});
}

export const diaryFixture = {
	id: 'd1',
	characterId: 'c1',
	title: 'Cronache del Nord',
	coverColor: '#2F4B43',
	coverUrl: null,
	font: null,
	fontSize: null,
	description: null,
	coverDocumentId: null
};

export const characterFixture = {
	id: 'c1',
	kind: 'PLAYER',
	name: 'Alira dei Salici',
	campaignName: 'Il Nord Spezzato',
	backgroundColor: '#3B2A6B',
	avatarUrl: null
};

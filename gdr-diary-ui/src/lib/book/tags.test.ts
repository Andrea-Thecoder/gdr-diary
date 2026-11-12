import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '#lib/api/client.js';
import { MISSING_PAGE_MESSAGE, MissingPageError } from './pages.js';
import {
	EMPTY_TAG_MESSAGE,
	POSTIT_COLORS,
	POSTIT_TILTS,
	RejectedTagError,
	TAG_MAX_LENGTH,
	TAG_TOO_LONG_MESSAGE,
	checkTag,
	cleanTag,
	fetchDiaryTags,
	pageForTag,
	pagesLabel,
	postitColor,
	postitTilt,
	sameTag,
	updatePageTag
} from './tags.js';

type Call = (...args: unknown[]) => Promise<unknown>;

function fakeApi(handlers: Partial<Record<'GET' | 'PUT', Call>>): ApiClient {
	return {
		GET: handlers.GET ?? vi.fn<Call>(),
		PUT: handlers.PUT ?? vi.fn<Call>()
	} as unknown as ApiClient;
}

describe('cleanTag', () => {
	it.each([
		['  Erebor ', 'Erebor'],
		['due\tparole', 'due parole'],
		['a\nb', 'a b'],
		['   ', '']
	])('turns %j into %j', (text, expected) => {
		expect(cleanTag(text)).toBe(expected);
	});
});

describe('sameTag', () => {
	it('does not look at the case', () => {
		expect(sameTag('Erebor', 'erebor')).toBe(true);
		expect(sameTag('Erebor', 'combat')).toBe(false);
	});
});

describe('checkTag', () => {
	it('accepts a name of up to the limit', () => {
		expect(checkTag('  Erebor ')).toBeNull();
		expect(checkTag('x'.repeat(TAG_MAX_LENGTH))).toBeNull();
	});

	it('asks for a name when there is nothing but spaces', () => {
		expect(checkTag('   ')).toBe(EMPTY_TAG_MESSAGE);
	});

	it('refuses a name that is too long', () => {
		expect(checkTag('x'.repeat(TAG_MAX_LENGTH + 1))).toBe(TAG_TOO_LONG_MESSAGE);
	});
});

describe('post-it look', () => {
	it('gives the same colour and tilt to the same name, whatever the case', () => {
		expect(postitColor('Erebor')).toBe(postitColor('erebor'));
		expect(postitTilt('Erebor')).toBe(postitTilt('erebor'));
	});

	it('picks a colour among the ones in the tokens', () => {
		expect(postitColor('combat')).toBeGreaterThanOrEqual(0);
		expect(postitColor('combat')).toBeLessThan(POSTIT_COLORS);
	});

	it('keeps the tilt within a couple of degrees', () => {
		const limit = Math.floor(POSTIT_TILTS / 2);
		expect(Math.abs(postitTilt('combat'))).toBeLessThanOrEqual(limit);
		expect(Math.abs(postitTilt('pnj'))).toBeLessThanOrEqual(limit);
	});

	it('does not give every name the same colour', () => {
		const colours = new Set(
			['combat', 'pnj', 'Erebor', 'indizi', 'mappa', 'taverna'].map(postitColor)
		);
		expect(colours.size).toBeGreaterThan(1);
	});
});

describe('pageForTag', () => {
	it.each([
		['the first page when the tag is not in view', [3, 9], [1, 2], 3],
		['the next page when one page in view has the tag', [3, 9], [3, 4], 9],
		['the next page after the view, not just after the first page', [1, 3, 9], [1, 2], 3],
		['the first page again after the last one', [3, 9], [9, 10], 3],
		['nothing when the tag has no pages', [], [1, 2], null]
	])('goes to %s', (_name, pages, visible, expected) => {
		expect(pageForTag(pages, visible)).toBe(expected);
	});
});

describe('pagesLabel', () => {
	it.each([
		[[7], 'pagina 7'],
		[[3, 7], 'pagine 3, 7'],
		[[1, 2, 3, 4], 'pagine 1, 2, 3, 4'],
		[[1, 2, 3, 4, 5], '5 pagine']
	])('describes %j as %j', (pages, expected) => {
		expect(pagesLabel(pages)).toBe(expected);
	});
});

describe('updatePageTag', () => {
	it('sends the tag of the page and returns the tag that was saved', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ data: { tag: 'Erebor' } });
		const saved = await updatePageTag(fakeApi({ PUT }), 'p1', 'erebor');
		expect(PUT).toHaveBeenCalledWith('/pages/{id}/tag', {
			params: { path: { id: 'p1' } },
			body: { tag: 'erebor' }
		});
		expect(saved).toBe('Erebor');
	});

	it('removes the tag with null', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ data: { tag: null } });
		expect(await updatePageTag(fakeApi({ PUT }), 'p1', null)).toBeNull();
		expect(PUT.mock.calls[0]?.[1]).toEqual({ params: { path: { id: 'p1' } }, body: { tag: null } });
	});

	it('returns null when the answer has no tag', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ data: {} });
		expect(await updatePageTag(fakeApi({ PUT }), 'p1', null)).toBeNull();
	});

	it('says the page is gone on a 404', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 404 } });
		const failure = updatePageTag(fakeApi({ PUT }), 'p1', 'a');
		await expect(failure).rejects.toBeInstanceOf(MissingPageError);
		await expect(failure).rejects.toThrow(MISSING_PAGE_MESSAGE);
	});

	it('shows the message of a 400 as a rejection', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({
			error: {
				title: 'BAD_REQUEST',
				status: 400,
				violations: [{ message: 'A diary can have at most 10 different tags' }]
			},
			response: { status: 400 }
		});
		const failure = updatePageTag(fakeApi({ PUT }), 'p1', 'a');
		await expect(failure).rejects.toBeInstanceOf(RejectedTagError);
		await expect(failure).rejects.toThrow('Un diario può avere al massimo 10 tag diversi');
	});

	it('throws a plain error for any other failure', async () => {
		const PUT = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 500 } });
		const failure = updatePageTag(fakeApi({ PUT }), 'p1', 'a');
		await expect(failure).rejects.toThrow(Error);
		await expect(failure).rejects.not.toBeInstanceOf(RejectedTagError);
		await expect(failure).rejects.not.toBeInstanceOf(MissingPageError);
	});
});

describe('fetchDiaryTags', () => {
	it('asks for the page with the filter and maps the tags', async () => {
		const GET = vi.fn<Call>().mockResolvedValue({
			data: {
				list: [{ name: 'combat', pageNumbers: [3, 7] }, { pageNumbers: [1] }, { name: 'pnj' }],
				totalRows: 3,
				totalPages: 1,
				page: 1
			}
		});
		const result = await fetchDiaryTags(fakeApi({ GET }), 'd1', {
			name: '  com ',
			page: 1,
			size: 8
		});
		expect(GET).toHaveBeenCalledWith('/diaries/{diaryId}/tags', {
			params: { path: { diaryId: 'd1' }, query: { page: 1, size: 8, name: 'com' } }
		});
		expect(result).toEqual({
			items: [
				{ name: 'combat', pageNumbers: [3, 7] },
				{ name: 'pnj', pageNumbers: [] }
			],
			totalRows: 3,
			totalPages: 1,
			page: 1
		});
	});

	it('leaves out an empty filter', async () => {
		const GET = vi.fn<Call>().mockResolvedValue({ data: { list: [] } });
		await fetchDiaryTags(fakeApi({ GET }), 'd1', { name: ' ', page: 2, size: 50 });
		expect(GET).toHaveBeenCalledWith('/diaries/{diaryId}/tags', {
			params: { path: { diaryId: 'd1' }, query: { page: 2, size: 50 } }
		});
	});

	it('throws a readable error when the call fails', async () => {
		const GET = vi.fn<Call>().mockResolvedValue({ error: {}, response: { status: 500 } });
		await expect(
			fetchDiaryTags(fakeApi({ GET }), 'd1', { name: '', page: 1, size: 50 })
		).rejects.toThrow();
	});
});

import { beforeEach, describe, expect, it, vi } from 'vitest';

type Call = (...args: unknown[]) => Promise<unknown>;

const { api } = vi.hoisted(() => ({ api: { GET: vi.fn<Call>() } }));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));

import { ALL_SYSTEMS, SheetList } from '#lib/sheets/sheets.svelte.js';

function pageOf(items: { id: string; system: string | null }[]): { data: unknown } {
	return { data: { list: items, totalRows: items.length, totalPages: 1, page: 1 } };
}

function lastQuery(): Record<string, unknown> {
	const options = api.GET.mock.calls.at(-1)?.[1] as { params: { query: Record<string, unknown> } };
	return options.params.query;
}

beforeEach(() => {
	api.GET.mockReset();
});

describe('SheetList', () => {
	it('starts with the most recent sheets and no filter', () => {
		const list = new SheetList('c1');

		expect(list.descending).toBe(true);
		expect(list.hasFilters).toBe(false);
		expect(list.hasDraft).toBe(false);
		expect(list.systemOptions).toEqual([{ value: '', label: ALL_SYSTEMS }]);
	});

	it('learns the systems of the sheets it loads, sorted and without repeats', async () => {
		api.GET.mockResolvedValueOnce(
			pageOf([
				{ id: 'a', system: 'Pathfinder 2e' },
				{ id: 'b', system: 'D&D 5e' },
				{ id: 'c', system: null },
				{ id: 'd', system: '  ' },
				{ id: 'e', system: 'D&D 5e' }
			])
		);
		const list = new SheetList('c1');

		await list.reload();

		expect(list.knownSystems).toEqual(['D&D 5e', 'Pathfinder 2e']);
		expect(list.systemOptions.map((option) => option.label)).toEqual([
			ALL_SYSTEMS,
			'D&D 5e',
			'Pathfinder 2e'
		]);
	});

	it('keeps the systems it already knew when a filtered page shows only some', async () => {
		api.GET.mockResolvedValueOnce(
			pageOf([
				{ id: 'a', system: 'D&D 5e' },
				{ id: 'b', system: 'Pathfinder 2e' }
			])
		);
		const list = new SheetList('c1');
		await list.reload();

		api.GET.mockResolvedValueOnce(pageOf([{ id: 'a', system: 'D&D 5e' }]));
		list.draftSystem = 'D&D 5e';
		list.applyFilters();
		await list.reload();

		expect(list.knownSystems).toEqual(['D&D 5e', 'Pathfinder 2e']);
	});

	it('applies the draft filters, trimmed, and sends them to the API', async () => {
		api.GET.mockResolvedValue(pageOf([]));
		const list = new SheetList('c1');
		list.draftTitle = '  Alira ';
		list.draftSystem = 'D&D 5e';

		expect(list.hasDraft).toBe(true);
		expect(list.hasFilters).toBe(false);
		list.applyFilters();
		await list.reload();

		expect(list.hasFilters).toBe(true);
		expect(lastQuery()).toMatchObject({ title: 'Alira', system: 'D&D 5e', descending: true });
	});

	it('changes the query key when a filter or the order changes', () => {
		const list = new SheetList('c1');
		const before = list.queryKey;

		list.draftTitle = 'Alira';
		list.applyFilters();
		const filtered = list.queryKey;
		list.toggleOrder();

		expect(filtered).not.toBe(before);
		expect(list.queryKey).not.toBe(filtered);
	});

	it('clears drafts and filters together', () => {
		const list = new SheetList('c1');
		list.draftTitle = 'Alira';
		list.draftSystem = 'D&D 5e';
		list.applyFilters();

		list.clearFilters();

		expect(list.hasDraft).toBe(false);
		expect(list.hasFilters).toBe(false);
	});

	it('asks for the sheets of its own character', async () => {
		api.GET.mockResolvedValue(pageOf([]));

		await new SheetList('id-alira').reload();

		const options = api.GET.mock.calls[0]?.[1] as { params: { path: { characterId: string } } };
		expect(options.params.path.characterId).toBe('id-alira');
	});
});

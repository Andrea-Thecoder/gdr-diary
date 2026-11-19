import { getApi } from '#lib/api/index.js';
import { PagedList, type PageResult } from '#lib/list/paged-list.svelte.js';
import { fetchSheets, type SheetSummary } from './api.js';

export const ALL_SYSTEMS = 'Tutti i sistemi';

export class SheetList extends PagedList<SheetSummary> {
	title = $state('');
	draftTitle = $state('');
	system = $state('');
	draftSystem = $state('');
	knownSystems = $state<string[]>([]);

	readonly queryKey = $derived(JSON.stringify([this.title.trim(), this.system, this.descending]));
	readonly hasDraft = $derived(this.draftTitle.trim() !== '' || this.draftSystem !== '');
	readonly hasFilters = $derived(this.title.trim() !== '' || this.system !== '');
	readonly systemOptions = $derived([
		{ value: '', label: ALL_SYSTEMS },
		...this.knownSystems.map((system) => ({ value: system, label: system }))
	]);

	protected readonly failure = 'Non riesco a caricare le schede.';

	private readonly characterId: string;

	constructor(characterId: string) {
		super();
		this.characterId = characterId;
		this.descending = true;
	}

	applyFilters(): void {
		this.title = this.draftTitle.trim();
		this.system = this.draftSystem;
	}

	clearFilters(): void {
		this.draftTitle = '';
		this.draftSystem = '';
		this.title = '';
		this.system = '';
	}

	protected async load(page: number): Promise<PageResult<SheetSummary>> {
		const result = await fetchSheets(getApi(), this.characterId, {
			title: this.title,
			system: this.system,
			descending: this.descending,
			page
		});
		this.remember(result.items);
		return result;
	}

	private remember(items: readonly SheetSummary[]): void {
		const seen = items.flatMap((item) => {
			const system = item.system?.trim() ?? '';
			return system === '' ? [] : [system];
		});
		const merged = [...this.knownSystems, ...seen].filter(
			(system, index, all) => all.indexOf(system) === index
		);
		if (merged.length !== this.knownSystems.length) {
			this.knownSystems = merged.sort((a, b) => a.localeCompare(b, 'it'));
		}
	}
}

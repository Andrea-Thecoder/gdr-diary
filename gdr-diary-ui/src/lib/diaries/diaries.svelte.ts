import { getApi } from '#lib/api/index.js';
import { PagedList, type PageResult } from '#lib/list/paged-list.svelte.js';
import { fetchDiaries, type DiarySummary } from './api.js';

export class DiaryList extends PagedList<DiarySummary> {
	title = $state('');
	draftTitle = $state('');

	readonly queryKey = $derived(JSON.stringify([this.title.trim(), this.descending]));
	readonly hasDraft = $derived(this.draftTitle.trim() !== '');
	readonly hasFilters = $derived(this.title.trim() !== '');

	protected readonly failure = 'Non riesco a caricare i diari.';

	private readonly characterId: string;

	constructor(characterId: string) {
		super();
		this.characterId = characterId;
	}

	applyFilters(): void {
		this.title = this.draftTitle.trim();
	}

	clearFilters(): void {
		this.draftTitle = '';
		this.title = '';
	}

	protected load(page: number): Promise<PageResult<DiarySummary>> {
		return fetchDiaries(getApi(), this.characterId, {
			title: this.title,
			descending: this.descending,
			page
		});
	}
}

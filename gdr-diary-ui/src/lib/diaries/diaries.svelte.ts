import { getApi } from '#lib/api/index.js';
import { PagedList, type PageResult } from '#lib/list/paged-list.svelte.js';
import { fetchDiaries, type DiarySummary } from './api.js';

export const TAG_FILTER_DELAY = 400;

export class DiaryList extends PagedList<DiarySummary> {
	title = $state('');
	draftTitle = $state('');
	tag = $state('');
	draftTag = $state('');

	readonly queryKey = $derived(
		JSON.stringify([this.title.trim(), this.tag.trim(), this.descending])
	);
	readonly hasDraft = $derived(this.draftTitle.trim() !== '' || this.draftTag.trim() !== '');
	readonly hasFilters = $derived(this.title.trim() !== '' || this.tag.trim() !== '');

	protected readonly failure = 'Non riesco a caricare i diari.';

	private readonly characterId: string;
	private tagTimer: ReturnType<typeof setTimeout> | undefined;

	constructor(characterId: string) {
		super();
		this.characterId = characterId;
	}

	applyFilters(): void {
		clearTimeout(this.tagTimer);
		this.title = this.draftTitle.trim();
		this.tag = this.draftTag.trim();
	}

	typeTag(): void {
		clearTimeout(this.tagTimer);
		this.tagTimer = setTimeout(() => {
			this.tag = this.draftTag.trim();
		}, TAG_FILTER_DELAY);
	}

	clearFilters(): void {
		clearTimeout(this.tagTimer);
		this.draftTitle = '';
		this.title = '';
		this.draftTag = '';
		this.tag = '';
	}

	dispose(): void {
		clearTimeout(this.tagTimer);
	}

	protected load(page: number): Promise<PageResult<DiarySummary>> {
		return fetchDiaries(getApi(), this.characterId, {
			title: this.title,
			tag: this.tag,
			descending: this.descending,
			page
		});
	}
}

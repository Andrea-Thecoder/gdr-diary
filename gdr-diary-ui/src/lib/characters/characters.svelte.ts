import { getApi } from '#lib/api/index.js';
import { PagedList, type PageResult } from '#lib/list/paged-list.svelte.js';
import { fetchCharacters, type CharacterSummary } from './api.js';

export class CharacterList extends PagedList<CharacterSummary> {
	name = $state('');
	campaignName = $state('');
	draftName = $state('');
	draftCampaignName = $state('');

	readonly queryKey = $derived(
		JSON.stringify([this.name.trim(), this.campaignName.trim(), this.descending])
	);
	readonly hasDraft = $derived(
		this.draftName.trim() !== '' || this.draftCampaignName.trim() !== ''
	);
	readonly hasFilters = $derived(this.name.trim() !== '' || this.campaignName.trim() !== '');

	protected readonly failure = 'Non riesco a caricare i personaggi.';

	applyFilters(): void {
		this.name = this.draftName.trim();
		this.campaignName = this.draftCampaignName.trim();
	}

	clearFilters(): void {
		this.draftName = '';
		this.draftCampaignName = '';
		this.name = '';
		this.campaignName = '';
	}

	protected load(page: number): Promise<PageResult<CharacterSummary>> {
		return fetchCharacters(getApi(), {
			name: this.name,
			campaignName: this.campaignName,
			descending: this.descending,
			page
		});
	}
}

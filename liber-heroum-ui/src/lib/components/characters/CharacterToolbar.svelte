<script lang="ts">
	import type { CharacterList } from '#lib/characters/characters.svelte.js';
	import Icon from '#lib/components/ui/Icon.svelte';
	import ListToolbar from '#lib/components/ui/ListToolbar.svelte';
	import SearchField from '#lib/components/ui/SearchField.svelte';
	import { FILTER_MAX_LENGTH } from '#lib/list/paged-list.svelte.js';

	interface Props {
		list: CharacterList;
	}

	let { list }: Props = $props();

	const count = $derived(
		list.totalRows === 1 ? '1 personaggio' : `${String(list.totalRows)} personaggi`
	);
</script>

<ListToolbar
	sortLabel="Nome"
	descending={list.descending}
	{count}
	canClear={list.hasDraft || list.hasFilters}
	ontoggleorder={() => {
		list.toggleOrder();
	}}
	onapply={() => {
		list.applyFilters();
	}}
	onclear={() => {
		list.clearFilters();
	}}
>
	<SearchField
		label="Cerca per nome"
		bind:value={list.draftName}
		maxlength={FILTER_MAX_LENGTH}
		class="flex-[1_1_240px]"
	/>
	<SearchField
		label="Filtra per campagna"
		bind:value={list.draftCampaignName}
		maxlength={FILTER_MAX_LENGTH}
		class="flex-[1_1_240px]"
	>
		{#snippet icon()}
			<Icon size={20} strokeWidth={1.8} class="flex-none">
				<path d="M6 21V4M6 5h12l-3 4 3 4H6" />
			</Icon>
		{/snippet}
	</SearchField>
</ListToolbar>

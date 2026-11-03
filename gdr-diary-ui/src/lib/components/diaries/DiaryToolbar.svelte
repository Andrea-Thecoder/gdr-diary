<script lang="ts">
	import ListToolbar from '#lib/components/ui/ListToolbar.svelte';
	import SearchField from '#lib/components/ui/SearchField.svelte';
	import { FILTER_MAX_LENGTH } from '#lib/list/paged-list.svelte.js';
	import type { DiaryList } from '#lib/diaries/diaries.svelte.js';

	interface Props {
		list: DiaryList;
	}

	let { list }: Props = $props();

	const count = $derived(list.totalRows === 1 ? '1 diario' : `${String(list.totalRows)} diari`);
</script>

<ListToolbar
	sortLabel="Titolo"
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
		label="Cerca per titolo"
		bind:value={list.draftTitle}
		maxlength={FILTER_MAX_LENGTH}
		class="flex-[1_1_240px]"
	/>
</ListToolbar>

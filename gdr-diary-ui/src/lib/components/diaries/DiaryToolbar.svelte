<script lang="ts">
	import Icon from '#lib/components/ui/Icon.svelte';
	import ListToolbar from '#lib/components/ui/ListToolbar.svelte';
	import SearchField from '#lib/components/ui/SearchField.svelte';
	import { TAG_MAX_LENGTH } from '#lib/book/tags.js';
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
	<SearchField
		label="Cerca per tag"
		bind:value={list.draftTag}
		maxlength={TAG_MAX_LENGTH}
		oninput={() => {
			list.typeTag();
		}}
		class="flex-[1_1_200px]"
	>
		{#snippet icon()}
			<Icon size={20} strokeWidth={1.8} class="flex-none">
				<path d="M3 12V4h8l9 9-8 8-9-9z" />
				<circle cx="7.5" cy="8.5" r="1.2" />
			</Icon>
		{/snippet}
	</SearchField>
</ListToolbar>

<script lang="ts">
	import ListToolbar from '#lib/components/ui/ListToolbar.svelte';
	import SearchField from '#lib/components/ui/SearchField.svelte';
	import SelectField from '#lib/components/ui/SelectField.svelte';
	import { FILTER_MAX_LENGTH } from '#lib/list/paged-list.svelte.js';
	import type { SheetList } from '#lib/sheets/sheets.svelte.js';

	interface Props {
		list: SheetList;
	}

	let { list }: Props = $props();

	const count = $derived(list.totalRows === 1 ? '1 scheda' : `${String(list.totalRows)} schede`);
	const order = $derived({
		label: list.descending ? 'Più recenti' : 'Meno recenti',
		hint: `Ordina per data di caricamento, dalla ${list.descending ? 'più' : 'meno'} recente`,
		up: !list.descending
	});
</script>

<ListToolbar
	sortLabel="Data"
	descending={list.descending}
	{count}
	{order}
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
	{#if list.knownSystems.length > 0}
		<SelectField
			label="Sistema di gioco"
			hideLabel
			bind:value={list.draftSystem}
			options={list.systemOptions}
			class="min-w-0 flex-[0_1_220px]"
		/>
	{/if}
</ListToolbar>

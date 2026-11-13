<script lang="ts">
	import { untrack } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import type { CharacterDetail } from '#lib/characters/api.js';
	import CharacterHeader from '#lib/components/diaries/CharacterHeader.svelte';
	import CardList from '#lib/components/ui/CardList.svelte';
	import ConfirmDeleteDialog from '#lib/components/ui/ConfirmDeleteDialog.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import { getMaxSheets } from '#lib/env.js';
	import {
		deleteSheet,
		freshDownloadUrl,
		type SavedSheet,
		type SheetSummary
	} from '#lib/sheets/api.js';
	import { startDownload } from '#lib/sheets/download.js';
	import { SheetList } from '#lib/sheets/sheets.svelte.js';
	import { Notice } from '#lib/ui/notice.svelte.js';
	import NewSheetCard from './NewSheetCard.svelte';
	import SheetCard from './SheetCard.svelte';
	import SheetDialog from './SheetDialog.svelte';
	import SheetToolbar from './SheetToolbar.svelte';

	interface Props {
		character: CharacterDetail;
	}

	let { character }: Props = $props();

	const characterId = untrack(() => character.id ?? '');
	const list = new SheetList(characterId);
	const notice = new Notice();

	let dialogOpen = $state(false);
	let editing = $state<SheetSummary | undefined>(undefined);
	let deleting = $state<SheetSummary | null>(null);

	const maxSheets = getMaxSheets();

	const name = $derived(character.name ?? 'Il personaggio');
	const firstTime = $derived(list.status === 'ready' && list.totalRows === 0 && !list.hasFilters);
	const full = $derived(list.status === 'ready' && !list.hasFilters && list.totalRows >= maxSheets);
	const showToolbar = $derived(!firstTime && !(list.status === 'error' && list.items.length === 0));
	const subtitle = $derived(
		firstTime
			? `${name} non ha ancora nessuna scheda. Carica il PDF e tienilo qui, accanto ai diari.`
			: `Le schede di ${name} in PDF, a portata di mano mentre scrivi. Carica quella nuova quando sale di livello.`
	);
	const count = $derived(list.status === 'ready' ? list.totalRows : undefined);

	$effect(() => {
		if (list.queryKey) {
			untrack(() => {
				void list.reload();
			});
		}
	});

	function openDialog(): void {
		editing = undefined;
		dialogOpen = true;
	}

	function openEditor(sheet: SheetSummary): void {
		editing = sheet;
		dialogOpen = true;
	}

	function closeDialog(): void {
		dialogOpen = false;
	}

	function askDelete(sheet: SheetSummary): void {
		deleting = sheet;
	}

	async function download(sheet: SheetSummary): Promise<void> {
		if (sheet.id === undefined) {
			return;
		}
		try {
			startDownload(await freshDownloadUrl(getApi(), sheet.id));
		} catch (cause) {
			notice.show(
				cause instanceof Error ? cause.message : 'Non riesco a scaricare la scheda. Riprova.',
				true
			);
		}
	}

	async function removeSheet(): Promise<void> {
		const target = deleting;
		if (target?.id === undefined) {
			return;
		}
		await deleteSheet(getApi(), target.id);
		deleting = null;
		notice.show(`${target.title ?? 'La scheda'} è stata eliminata.`);
		await list.reload();
	}

	function saved(result: SavedSheet): void {
		const wasEditing = editing !== undefined;
		dialogOpen = false;
		const title = result.sheet.title ?? 'La scheda';
		if (result.fileError !== null) {
			notice.show(`${title} è stata salvata, ma il PDF non è cambiato: ${result.fileError}`, true);
		} else {
			notice.show(wasEditing ? `${title} è stata aggiornata.` : `${title} è stata archiviata.`);
		}
		void list.reload();
	}
</script>

<CharacterHeader
	{character}
	tab="sheets"
	{subtitle}
	{count}
	newLabel={firstTime || full ? undefined : 'Nuova scheda'}
	onnew={firstTime || full ? undefined : openDialog}
/>

<Container class="pt-[clamp(32px,4vw,48px)] pb-[clamp(64px,9vw,112px)]">
	{#if showToolbar}
		<SheetToolbar {list} />
	{/if}

	<CardList
		{list}
		itemKey={(sheet: SheetSummary) => sheet.id}
		{firstTime}
		loadingText="Carico le schede…"
		noMatchText="Nessuna scheda corrisponde alla ricerca."
		moreLabel="Mostra altre schede"
		onclearfilters={() => {
			list.clearFilters();
		}}
	>
		{#snippet card(sheet: SheetSummary)}
			<SheetCard
				{sheet}
				onedit={openEditor}
				ondelete={askDelete}
				ondownload={(target: SheetSummary) => {
					void download(target);
				}}
			/>
		{/snippet}
		{#snippet newCard()}
			<NewSheetCard
				title="Nuova scheda"
				limit={full ? maxSheets : undefined}
				onclick={openDialog}
			/>
		{/snippet}
		{#snippet firstTimeCard()}
			<NewSheetCard title="Carica la prima scheda" first onclick={openDialog} />
		{/snippet}
	</CardList>
</Container>

<SheetDialog
	open={dialogOpen}
	{characterId}
	characterName={name}
	sheet={editing}
	onclose={closeDialog}
	onsaved={saved}
/>

<ConfirmDeleteDialog
	open={deleting !== null}
	title={`Eliminare ${deleting?.title ?? 'la scheda'}?`}
	subtitle="Stai per cancellare questa scheda."
	consequences={['la scheda e il suo file PDF.']}
	fallbackError="Non riesco a eliminare la scheda."
	onconfirm={removeSheet}
	onclose={() => {
		deleting = null;
	}}
/>

<NoticePopup {notice} />

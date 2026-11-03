<script lang="ts">
	import { untrack } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import type { CharacterDetail } from '#lib/characters/api.js';
	import CardList from '#lib/components/ui/CardList.svelte';
	import ConfirmDeleteDialog from '#lib/components/ui/ConfirmDeleteDialog.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import {
		deleteDiary,
		fetchDiary,
		type DiaryDetail,
		type DiarySummary,
		type SavedDiary
	} from '#lib/diaries/api.js';
	import { DiaryList } from '#lib/diaries/diaries.svelte.js';
	import { Notice } from '#lib/ui/notice.svelte.js';
	import CharacterHeader from './CharacterHeader.svelte';
	import DiaryCard from './DiaryCard.svelte';
	import DiaryDialog from './DiaryDialog.svelte';
	import DiaryToolbar from './DiaryToolbar.svelte';
	import NewDiaryCard from './NewDiaryCard.svelte';

	interface Props {
		character: CharacterDetail;
	}

	let { character }: Props = $props();

	const characterId = untrack(() => character.id ?? '');
	const list = new DiaryList(characterId);
	const notice = new Notice();

	let dialogOpen = $state(false);
	let editing = $state<DiaryDetail | undefined>(undefined);
	let deleting = $state<DiarySummary | null>(null);

	const name = $derived(character.name ?? 'Il personaggio');
	const firstTime = $derived(list.status === 'ready' && list.totalRows === 0 && !list.hasFilters);
	const showToolbar = $derived(!firstTime && !(list.status === 'error' && list.items.length === 0));
	const subtitle = $derived(
		firstTime
			? `${name} non ha ancora nessun diario. Rilegane uno e comincia dalla prima pagina.`
			: 'È ora di scrivere qualcosa di epico.'
	);

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

	async function openEditor(summary: DiarySummary): Promise<void> {
		if (summary.id === undefined) {
			return;
		}
		try {
			editing = await fetchDiary(getApi(), summary.id);
			dialogOpen = true;
		} catch (cause) {
			notice.show(
				cause instanceof Error ? cause.message : 'Non riesco a caricare il diario.',
				true
			);
		}
	}

	function closeDialog(): void {
		dialogOpen = false;
	}

	async function removeDiary(): Promise<void> {
		const target = deleting;
		if (target?.id === undefined) {
			return;
		}
		await deleteDiary(getApi(), target.id);
		deleting = null;
		notice.show(`${target.title ?? 'Il diario'} è stato eliminato.`);
		await list.reload();
	}

	function saved(result: SavedDiary): void {
		const wasEditing = editing !== undefined;
		dialogOpen = false;
		const title = result.diary.title ?? 'Il diario';
		if (result.coverError !== null) {
			notice.show(
				wasEditing
					? `${title} è stato salvato, ma la copertina non è cambiata: ${result.coverError}`
					: `${title} è stato creato, ma la copertina non è stata caricata: ${result.coverError}`,
				true
			);
		} else {
			notice.show(
				wasEditing ? `${title} è stato aggiornato.` : `${title} è pronto per le sue pagine.`
			);
		}
		void list.reload();
	}
</script>

<CharacterHeader {character} {subtitle} showNew={!firstTime} onnew={openDialog} />

<Container class="pb-[clamp(64px,9vw,112px)]">
	{#if showToolbar}
		<DiaryToolbar {list} />
	{/if}

	<CardList
		{list}
		itemKey={(diary: DiarySummary) => diary.id}
		{firstTime}
		loadingText="Carico i diari…"
		noMatchText="Nessun diario corrisponde alla ricerca."
		moreLabel="Mostra altri diari"
		onclearfilters={() => {
			list.clearFilters();
		}}
	>
		{#snippet card(diary: DiarySummary)}
			<DiaryCard
				{diary}
				onedit={(target: DiarySummary) => {
					void openEditor(target);
				}}
				ondelete={(target: DiarySummary) => {
					deleting = target;
				}}
			/>
		{/snippet}
		{#snippet newCard()}
			<NewDiaryCard title="Nuovo diario" onclick={openDialog} />
		{/snippet}
		{#snippet firstTimeCard()}
			<NewDiaryCard title="Rilega il primo diario" onclick={openDialog} />
		{/snippet}
	</CardList>
</Container>

<DiaryDialog
	open={dialogOpen}
	{characterId}
	characterName={name}
	diary={editing}
	onclose={closeDialog}
	onsaved={saved}
/>

<ConfirmDeleteDialog
	open={deleting !== null}
	title={`Eliminare ${deleting?.title ?? 'il diario'}?`}
	subtitle="Stai per cancellare questo diario."
	consequences={['il diario e la sua copertina;', 'tutte le sue pagine, con il testo scritto.']}
	fallbackError="Non riesco a eliminare il diario."
	onconfirm={removeDiary}
	onclose={() => {
		deleting = null;
	}}
/>

<NoticePopup {notice} />

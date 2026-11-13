<script lang="ts">
	import { untrack } from 'svelte';
	import CampaignsComingSoon from '#lib/components/characters/CampaignsComingSoon.svelte';
	import CharacterCard from '#lib/components/characters/CharacterCard.svelte';
	import CharacterDialog from '#lib/components/characters/CharacterDialog.svelte';
	import NewCharacterCard from '#lib/components/characters/NewCharacterCard.svelte';
	import CharacterToolbar from '#lib/components/characters/CharacterToolbar.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import CardList from '#lib/components/ui/CardList.svelte';
	import ConfirmDeleteDialog from '#lib/components/ui/ConfirmDeleteDialog.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import Tabs from '#lib/components/ui/Tabs.svelte';
	import { getApi } from '#lib/api/index.js';
	import {
		deleteCharacter,
		fetchCharacter,
		type CharacterDetail,
		type CharacterSummary,
		type CreatedCharacter
	} from '#lib/characters/api.js';
	import { areaChoice, type Area } from '#lib/characters/area.svelte.js';
	import { CharacterList } from '#lib/characters/characters.svelte.js';
	import { Notice } from '#lib/ui/notice.svelte.js';

	const AREAS = [
		{ id: 'characters', label: 'I miei personaggi' },
		{ id: 'campaigns', label: 'Le mie campagne' }
	] as const satisfies readonly { id: Area; label: string }[];

	const tabsId = $props.id();

	const list = new CharacterList();

	let dialogOpen = $state(false);
	let editing = $state<CharacterDetail | undefined>(undefined);
	let deleting = $state<CharacterSummary | null>(null);
	const notice = new Notice();

	const area = $derived(areaChoice.value);
	const campaigns = $derived(area === 'campaigns');
	const firstTime = $derived(
		!campaigns && list.status === 'ready' && list.totalRows === 0 && !list.hasFilters
	);
	const showToolbar = $derived(!firstTime && !(list.status === 'error' && list.items.length === 0));

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

	async function openEditor(character: CharacterSummary): Promise<void> {
		if (character.id === undefined) {
			return;
		}
		try {
			editing = await fetchCharacter(getApi(), character.id);
			dialogOpen = true;
		} catch (cause) {
			notice.show(
				cause instanceof Error ? cause.message : 'Non riesco a caricare il personaggio.',
				true
			);
		}
	}

	function closeDialog(): void {
		dialogOpen = false;
	}

	async function removeCharacter(): Promise<void> {
		const target = deleting;
		if (target?.id === undefined) {
			return;
		}
		await deleteCharacter(getApi(), target.id);
		deleting = null;
		notice.show(`${target.name ?? 'Il personaggio'} è stato eliminato.`);
		await list.reload();
	}

	function saved(result: CreatedCharacter): void {
		const wasEditing = editing !== undefined;
		dialogOpen = false;
		const name = result.character.name ?? 'Il personaggio';
		if (result.avatarError !== null) {
			notice.show(
				wasEditing
					? `${name} è stato salvato, ma l’immagine non è cambiata: ${result.avatarError}`
					: `${name} è stato creato, ma l’immagine non è stata caricata: ${result.avatarError}`,
				true
			);
		} else {
			notice.show(
				wasEditing ? `${name} è stato aggiornato.` : `${name} è pronto per i suoi diari.`
			);
		}
		void list.reload();
	}
</script>

<svelte:head>
	<title>I tuoi personaggi · Diario GDR</title>
</svelte:head>

<Container class="pt-[clamp(16px,3vw,40px)]">
	<Tabs
		label="Aree"
		tabs={AREAS}
		value={area}
		idPrefix={tabsId}
		onselect={(id: Area) => {
			areaChoice.select(id);
		}}
	/>
</Container>

<Container
	as="section"
	aria-labelledby="page-title"
	class="flex flex-wrap items-end justify-between gap-x-8 gap-y-7 pt-[18px] pb-[clamp(40px,6vw,72px)]"
>
	<div class="min-w-0 flex-[1_1_420px]">
		<h1
			id="page-title"
			class="m-0 font-display text-[clamp(34px,4.6vw,56px)] leading-[1.04] font-bold text-ink"
		>
			{campaigns
				? 'Le campagne stanno arrivando'
				: firstTime
					? 'Il tuo primo eroe ti aspetta.'
					: 'Chi scrive oggi?'}
		</h1>
		<p class="mx-0 mt-4 mb-0 max-w-[30em] text-[clamp(17px,1.8vw,20px)] text-soft">
			{campaigns
				? 'Presto potrai guidare le sessioni da Master: la tua campagna, i suoi giocatori e un diario del gruppo. Per ora scrivi i diari dei tuoi personaggi.'
				: firstTime
					? 'Dai un nome al personaggio: i diari e le pagine vengono dopo.'
					: 'Ogni personaggio ha i suoi diari. Scegli chi prende la penna, oppure forgia un nuovo eroe.'}
		</p>
	</div>
	{#if !firstTime && !campaigns}
		<Button onclick={openDialog}>
			<Icon size={18} strokeWidth={2.2}>
				<path d="M12 5v14M5 12h14" />
			</Icon>
			Nuovo personaggio
		</Button>
	{/if}
</Container>

<Container class="pb-[clamp(64px,9vw,112px)]">
	{#if campaigns}
		<div
			role="tabpanel"
			id={`${tabsId}-panel-campaigns`}
			aria-labelledby={`${tabsId}-tab-campaigns`}
		>
			<CampaignsComingSoon />
		</div>
	{:else}
		<div
			role="tabpanel"
			id={`${tabsId}-panel-characters`}
			aria-labelledby={`${tabsId}-tab-characters`}
		>
			{#if showToolbar}
				<CharacterToolbar {list} />
			{/if}

			<CardList
				{list}
				itemKey={(character: CharacterSummary) => character.id}
				{firstTime}
				loadingText="Carico i personaggi…"
				noMatchText="Nessun personaggio corrisponde alla ricerca."
				moreLabel="Mostra altri personaggi"
				onclearfilters={() => {
					list.clearFilters();
				}}
			>
				{#snippet card(character: CharacterSummary)}
					<CharacterCard
						{character}
						onedit={(target: CharacterSummary) => {
							void openEditor(target);
						}}
						ondelete={(target: CharacterSummary) => {
							deleting = target;
						}}
					/>
				{/snippet}
				{#snippet newCard()}
					<NewCharacterCard title="Nuovo personaggio" onclick={openDialog} />
				{/snippet}
				{#snippet firstTimeCard()}
					<NewCharacterCard title="Forgia il tuo primo eroe" onclick={openDialog} />
				{/snippet}
			</CardList>
		</div>
	{/if}
</Container>

<CharacterDialog open={dialogOpen} character={editing} onclose={closeDialog} onsaved={saved} />

<ConfirmDeleteDialog
	open={deleting !== null}
	title={`Eliminare ${deleting?.name ?? 'il personaggio'}?`}
	subtitle="Stai per cancellare questo personaggio."
	consequences={[
		'il personaggio e il suo ritratto;',
		'tutti i suoi diari, con ogni pagina scritta;',
		'le sue schede in PDF;',
		'le copertine che hai caricato.'
	]}
	fallbackError="Non riesco a eliminare il personaggio."
	onconfirm={removeCharacter}
	onclose={() => {
		deleting = null;
	}}
/>

<NoticePopup {notice} />

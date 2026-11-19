<script lang="ts">
	import { resolve } from '$app/paths';
	import { glowColor, photoBackground } from '#lib/characters/appearance.js';
	import type { DiarySummary } from '#lib/diaries/api.js';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import CardActions from '#lib/components/ui/CardActions.svelte';
	import CardEditButton from '#lib/components/ui/CardEditButton.svelte';
	import { cardLinkClasses } from '#lib/components/ui/button.js';
	import PortraitCard from '#lib/components/ui/PortraitCard.svelte';
	import ClosedBook from './ClosedBook.svelte';

	interface Props {
		diary: DiarySummary;
		onedit: (diary: DiarySummary) => void;
		ondelete: (diary: DiarySummary) => void;
	}

	let { diary, onedit, ondelete }: Props = $props();

	const id = $derived(diary.id ?? '');
	const title = $derived(diary.title ?? '');
</script>

<PortraitCard {title} glow={glowColor(diary.coverColor)} clampTitle>
	{#snippet photo()}
		<div
			class="relative grid h-[330px] place-items-center rounded-t-[5px]"
			style:background={photoBackground(diary.coverColor)}
		>
			<ClosedBook {title} color={diary.coverColor} coverUrl={diary.coverUrl} />
			<div
				aria-hidden="true"
				class="pointer-events-none absolute inset-0 surface-portrait-light"
			></div>
		</div>
		<CardEditButton
			label={`Modifica ${title}`}
			onclick={() => {
				onedit(diary);
			}}
		/>
	{/snippet}
	{#snippet action()}
		<CardActions
			deleteLabel={`Elimina ${title}`}
			ondelete={() => {
				ondelete(diary);
			}}
		>
			<a href={resolve('/(app)/diaries/[diaryId]', { diaryId: id })} class={cardLinkClasses}>
				Apri il diario
				<ArrowIcon />
			</a>
		</CardActions>
	{/snippet}
</PortraitCard>

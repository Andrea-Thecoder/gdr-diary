<script lang="ts">
	import type { Snippet } from 'svelte';
	import { resolve } from '$app/paths';
	import CampaignLine from '#lib/components/characters/CampaignLine.svelte';
	import ClosedBook from '#lib/components/diaries/ClosedBook.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import { backLinkClasses } from '#lib/components/ui/button.js';
	import type { CharacterDetail } from '#lib/characters/api.js';
	import type { DiaryDetail } from '#lib/diaries/api.js';

	interface Props {
		diary: DiaryDetail;
		character: CharacterDetail;
		counter: string;
		status: Snippet;
	}

	let { diary, character, counter, status }: Props = $props();

	const title = $derived(diary.title ?? '');
	const characterName = $derived(character.name ?? '');
</script>

<a
	href={resolve('/(app)/characters/[characterId]', { characterId: character.id ?? '' })}
	class={backLinkClasses}
>
	<ArrowIcon direction="left" size={18} />
	I diari di {characterName}
</a>
<div class="mt-3 flex flex-wrap items-center justify-between gap-x-8 gap-y-4">
	<div class="flex min-w-0 flex-[1_1_320px] items-center gap-4">
		<ClosedBook {title} color={diary.coverColor} coverUrl={diary.coverUrl} size="thumb" />
		<div class="min-w-0">
			<h1
				id="page-title"
				class="m-0 font-display text-[clamp(28px,4.5vw,48px)] leading-[1.04] font-bold [overflow-wrap:anywhere] text-ink"
			>
				{title}
			</h1>
			<CampaignLine campaign={character.campaignName?.trim() ?? ''} />
		</div>
	</div>
	<div class="flex flex-wrap items-center gap-x-5 gap-y-2">
		<p class="m-0 font-label text-[13px] tracking-[.12em] text-soft uppercase" aria-live="polite">
			{counter}
		</p>
		{@render status()}
	</div>
</div>

<script lang="ts">
	import { resolve } from '$app/paths';
	import CampaignLine from '#lib/components/characters/CampaignLine.svelte';
	import CharacterPortrait from '#lib/components/characters/CharacterPortrait.svelte';
	import CharacterTabs from '#lib/components/characters/CharacterTabs.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { backLinkClasses } from '#lib/components/ui/button.js';
	import { silhouetteFor } from '#lib/characters/appearance.js';
	import type { CharacterDetail } from '#lib/characters/api.js';
	import type { CharacterTab } from '#lib/characters/tabs.js';

	interface Props {
		character: CharacterDetail;
		tab: CharacterTab;
		subtitle: string;
		count?: number | undefined;
		newLabel?: string | undefined;
		onnew?: (() => void) | undefined;
	}

	let { character, tab, subtitle, count, newLabel, onnew }: Props = $props();

	const name = $derived(character.name ?? '');
	const description = $derived(character.description?.trim() ?? '');
	const hasAvatar = $derived(character.avatarUrl !== null && character.avatarUrl !== undefined);
</script>

<Container as="section" aria-labelledby="page-title" class="pt-[clamp(8px,2vw,24px)]">
	<a href={resolve('/(app)/characters')} class={backLinkClasses}>
		<ArrowIcon direction="left" size={18} />
		Tutti i personaggi
	</a>
	<div class="mt-4 flex flex-wrap items-end justify-between gap-x-8 gap-y-7">
		<div class="flex min-w-0 flex-[1_1_420px] flex-wrap items-center gap-x-6 gap-y-4">
			<CharacterPortrait
				label={hasAvatar ? `Ritratto di ${name}` : `Silhouette in penombra di ${name}`}
				color={character.backgroundColor}
				avatarUrl={character.avatarUrl}
				silhouette={silhouetteFor(character.id ?? '', character.kind)}
				class="size-28 flex-none rounded-full border-2 border-orn"
			/>
			<div class="min-w-0 flex-[1_1_240px]">
				<Eyebrow class="mb-2 text-label">Il personaggio</Eyebrow>
				<h1
					id="page-title"
					class="m-0 font-display text-[clamp(34px,4.6vw,56px)] leading-[1.04] font-bold [overflow-wrap:anywhere] text-ink"
				>
					{name}
				</h1>
				<CampaignLine campaign={character.campaignName?.trim() ?? ''} />
			</div>
		</div>
		{#if onnew !== undefined && newLabel !== undefined}
			<Button onclick={onnew}>
				<Icon size={18} strokeWidth={2.2}>
					<path d="M12 5v14M5 12h14" />
				</Icon>
				{newLabel}
			</Button>
		{/if}
	</div>
	{#if description !== ''}
		<p
			data-character-description
			class="mx-0 mt-6 mb-0 max-w-[40em] text-[clamp(17px,1.8vw,20px)] leading-[1.55] [overflow-wrap:anywhere] whitespace-pre-line text-soft"
		>
			{description}
		</p>
	{:else}
		<p class="mx-0 mt-6 mb-0 max-w-[36em] text-[clamp(17px,1.8vw,20px)] text-soft">{subtitle}</p>
	{/if}
	<CharacterTabs characterId={character.id ?? ''} {tab} {count} class="mt-[clamp(28px,4vw,44px)]" />
</Container>

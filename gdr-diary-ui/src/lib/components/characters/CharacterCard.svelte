<script lang="ts">
	import { resolve } from '$app/paths';
	import { glowColor, silhouetteFor } from '#lib/characters/appearance.js';
	import type { CharacterSummary } from '#lib/characters/api.js';
	import { isMaster } from '#lib/characters/kind.js';
	import CardActions from '#lib/components/ui/CardActions.svelte';
	import CardEditButton from '#lib/components/ui/CardEditButton.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import { cardLinkClasses } from '#lib/components/ui/button.js';
	import PortraitCard from '#lib/components/ui/PortraitCard.svelte';
	import CampaignLine from './CampaignLine.svelte';
	import CharacterPortrait from './CharacterPortrait.svelte';
	import MasterBadge from './MasterBadge.svelte';

	interface Props {
		character: CharacterSummary;
		onedit: (character: CharacterSummary) => void;
		ondelete: (character: CharacterSummary) => void;
	}

	let { character, onedit, ondelete }: Props = $props();

	const id = $derived(character.id ?? '');
	const name = $derived(character.name ?? '');
	const campaign = $derived(character.campaignName?.trim() ?? '');
	const master = $derived(isMaster(character.kind));
	const hasAvatar = $derived(character.avatarUrl !== null && character.avatarUrl !== undefined);
</script>

<PortraitCard title={name} glow={glowColor(character.backgroundColor)}>
	{#snippet photo()}
		<CharacterPortrait
			label={hasAvatar ? `Ritratto di ${name}` : `Silhouette in penombra di ${name}`}
			color={character.backgroundColor}
			avatarUrl={character.avatarUrl}
			silhouette={silhouetteFor(id, character.kind)}
			class="h-[240px] rounded-t-[5px]"
		/>
		{#if master}
			<MasterBadge class="absolute top-3 left-3" />
		{/if}
		<CardEditButton
			label={`Modifica ${name}`}
			onclick={() => {
				onedit(character);
			}}
		/>
	{/snippet}
	{#snippet subtitle()}
		<CampaignLine {campaign} />
	{/snippet}
	{#snippet action()}
		<CardActions
			deleteLabel={`Elimina ${name}`}
			ondelete={() => {
				ondelete(character);
			}}
		>
			<a
				href={resolve('/(app)/characters/[characterId]', { characterId: id })}
				class={cardLinkClasses}
			>
				Apri i diari
				<ArrowIcon />
			</a>
		</CardActions>
	{/snippet}
</PortraitCard>

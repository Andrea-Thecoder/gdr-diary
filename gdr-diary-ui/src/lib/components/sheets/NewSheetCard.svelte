<script lang="ts">
	import GhostCard from '#lib/components/ui/GhostCard.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import NewCard from '#lib/components/ui/NewCard.svelte';

	interface Props {
		title: string;
		first?: boolean;
		limit?: number | undefined;
		onclick: () => void;
	}

	let { title, first = false, limit, onclick }: Props = $props();
</script>

{#if limit !== undefined}
	<GhostCard
		title="Hai raggiunto il limite"
		description={`Un personaggio può avere al massimo ${limit} schede. Elimina quell${limit === 1 ? 'a' : 'e'} che non ti serve più per caricarne un’altra.`}
	>
		{#snippet icon()}
			<Icon size={36} strokeWidth={1.8}>
				<path d="M5 12h14" />
			</Icon>
		{/snippet}
	</GhostCard>
{:else}
	<NewCard
		{title}
		description={first
			? 'Un PDF e un titolo: la scheda resta qui, accanto ai diari.'
			: 'Carica un altro PDF: una versione nuova o una di un altro sistema.'}
		{onclick}
	/>
{/if}

<script lang="ts">
	import GhostCard from './GhostCard.svelte';
	import Icon from './Icon.svelte';

	interface Props {
		title: string;
		description: string;
		onclick: () => void;
	}

	let { title, description, onclick }: Props = $props();
</script>

<GhostCard {title} {description} {onclick}>
	{#snippet icon()}
		<Icon size={36} strokeWidth={1.8}>
			<path d="M12 5v14M5 12h14" />
		</Icon>
	{/snippet}
</GhostCard>

<script lang="ts">
	import CardGrid from '#lib/components/ui/CardGrid.svelte';
	import GhostCard from '#lib/components/ui/GhostCard.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
</script>

<CardGrid single>
	<li class="min-w-0">
		<GhostCard title="Coming Soon" description="Prossimamente">
			{#snippet icon()}
				<Icon size={36} strokeWidth={1.8}>
					<path d="M6 21V4M6 5h12l-3 4 3 4H6" />
				</Icon>
			{/snippet}
		</GhostCard>
	</li>
</CardGrid>

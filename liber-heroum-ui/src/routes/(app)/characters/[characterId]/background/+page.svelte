<script lang="ts">
	import BackgroundScreen from '#lib/components/background/BackgroundScreen.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>La storia di {data.character.name ?? 'questo personaggio'} · Liber Heroum</title>
</svelte:head>

{#key data.character.id}
	<BackgroundScreen character={data.character} initial={data.background} drafts={data.drafts} />
{/key}

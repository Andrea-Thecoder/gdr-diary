<script lang="ts">
	import DiariesScreen from '#lib/components/diaries/DiariesScreen.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.character.name ?? 'Diari'} · Diario GDR</title>
</svelte:head>

{#key data.character.id}
	<DiariesScreen character={data.character} />
{/key}

<script lang="ts">
	import SheetsScreen from '#lib/components/sheets/SheetsScreen.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.character.name ?? 'Schede'} · Schede · Diario GDR</title>
</svelte:head>

{#key data.character.id}
	<SheetsScreen character={data.character} />
{/key}

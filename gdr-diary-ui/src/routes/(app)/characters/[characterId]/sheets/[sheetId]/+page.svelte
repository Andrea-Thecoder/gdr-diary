<script lang="ts">
	import SheetEditorScreen from '#lib/components/sheets/SheetEditorScreen.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.sheet.title ?? 'Scheda'} · Scheda · Diario GDR</title>
</svelte:head>

{#key data.sheet.id}
	<SheetEditorScreen sheet={data.sheet} />
{/key}

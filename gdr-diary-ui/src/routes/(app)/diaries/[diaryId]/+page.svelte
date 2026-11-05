<script lang="ts">
	import DiaryScreen from '#lib/components/book/DiaryScreen.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.diary.title ?? 'Diario'} · Diario GDR</title>
</svelte:head>

{#key data.diary.id}
	<DiaryScreen
		diary={data.diary}
		character={data.character}
		initial={data.initial}
		spread={data.spread}
		drafts={data.drafts}
		bookmark={data.bookmark}
	/>
{/key}

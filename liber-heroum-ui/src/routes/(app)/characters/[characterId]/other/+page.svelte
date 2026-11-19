<script lang="ts">
	import OtherScreen from '#lib/components/characters/OtherScreen.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.character.name ?? 'Altro'} · Altro · Liber Heroum</title>
</svelte:head>

{#key data.character.id}
	<OtherScreen character={data.character} />
{/key}

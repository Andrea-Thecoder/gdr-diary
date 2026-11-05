<script lang="ts">
	import type { CharacterDetail } from '#lib/characters/api.js';
	import CharacterHeader from '#lib/components/diaries/CharacterHeader.svelte';
	import CardGrid from '#lib/components/ui/CardGrid.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import PlaceholderCard from './PlaceholderCard.svelte';

	interface Props {
		character: CharacterDetail;
	}

	let { character }: Props = $props();

	const SOON = [
		{
			id: 'maps',
			title: 'Mappe',
			text: 'Le mappe dei luoghi dove passa il tuo personaggio.',
			color: '#26382F',
			paths: ['M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2z', 'M9 4v14M15 6v14']
		},
		{
			id: 'images',
			title: 'Immagini',
			text: 'Ritratti, illustrazioni e riferimenti visivi.',
			color: '#3B2A6B',
			paths: [
				'M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
				'M9 8.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6z',
				'M3 17l5-4.5 4 3.5 3-2.5 6 5'
			]
		},
		{
			id: 'notes',
			title: 'Appunti e documenti',
			text: 'Handout del master, regole della casa, note di sessione.',
			color: '#5B4A2A',
			paths: ['M6 3h9l4 4v14H6z', 'M15 3v4h4M9 12h7M9 15.5h7M9 19h4']
		}
	] as const;
</script>

<CharacterHeader
	{character}
	tab="other"
	subtitle="Qui andranno gli altri file del personaggio. Per ora sono solo anteprime di quello che arriverà."
/>

<Container class="pt-[clamp(32px,4vw,48px)] pb-[clamp(64px,9vw,112px)]">
	<CardGrid>
		{#each SOON as item (item.id)}
			<li class="min-w-0">
				<PlaceholderCard title={item.title} text={item.text} color={item.color}>
					{#snippet icon()}
						<Icon size={84} strokeWidth={1.3}>
							{#each item.paths as path (path)}
								<path d={path} />
							{/each}
						</Icon>
					{/snippet}
				</PlaceholderCard>
			</li>
		{/each}
	</CardGrid>
</Container>

<script lang="ts">
	import TabNav from '#lib/components/ui/TabNav.svelte';
	import { characterTabs, type CharacterTab } from '#lib/characters/tabs.js';

	interface Props {
		characterId: string;
		tab: CharacterTab;
		count?: number | undefined;
		class?: string;
	}

	let { characterId, tab, count, class: className = '' }: Props = $props();

	const tabs = $derived(
		characterTabs(characterId).map((entry) => ({
			id: entry.id,
			label: entry.label,
			suffix: entry.suffix,
			href: entry.href,
			count: entry.id === tab ? count : undefined,
			countLabel: entry.id === tab && count !== undefined ? entry.countLabel?.(count) : undefined
		}))
	);
</script>

<TabNav label="Contenuti del personaggio" {tabs} value={tab} class={className} />

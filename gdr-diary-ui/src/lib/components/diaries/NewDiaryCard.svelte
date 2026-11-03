<script lang="ts">
	import NewCard from '#lib/components/ui/NewCard.svelte';

	interface Props {
		title: string;
		onclick: () => void;
	}

	let { title, onclick }: Props = $props();
</script>

<NewCard {title} description="Un titolo e una copertina: le pagine vengono dopo." {onclick} />

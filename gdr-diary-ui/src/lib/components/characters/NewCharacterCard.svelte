<script lang="ts">
	import NewCard from '#lib/components/ui/NewCard.svelte';

	interface Props {
		title: string;
		onclick: () => void;
	}

	let { title, onclick }: Props = $props();
</script>

<NewCard {title} description="Un nome, un volto e la campagna a cui appartiene." {onclick} />

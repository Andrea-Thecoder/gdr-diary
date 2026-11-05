<script lang="ts">
	import NewCard from '#lib/components/ui/NewCard.svelte';

	interface Props {
		title: string;
		first?: boolean;
		onclick: () => void;
	}

	let { title, first = false, onclick }: Props = $props();
</script>

<NewCard
	{title}
	description={first
		? 'Un PDF e un titolo: la scheda resta qui, accanto ai diari.'
		: 'Carica un altro PDF: una versione nuova o una di un altro sistema.'}
	{onclick}
/>

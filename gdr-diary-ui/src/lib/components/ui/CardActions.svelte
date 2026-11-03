<script lang="ts">
	import type { Snippet } from 'svelte';
	import CardDeleteButton from './CardDeleteButton.svelte';

	interface Props {
		deleteLabel: string;
		ondelete: () => void;
		children: Snippet;
	}

	let { deleteLabel, ondelete, children }: Props = $props();
</script>

<div class="flex items-center gap-3">
	{@render children()}
	<CardDeleteButton label={deleteLabel} onclick={ondelete} />
</div>

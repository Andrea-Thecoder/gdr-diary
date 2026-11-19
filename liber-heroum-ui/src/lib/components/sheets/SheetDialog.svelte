<script lang="ts">
	import Dialog from '#lib/components/ui/Dialog.svelte';
	import type { SavedSheet, SheetSummary } from '#lib/sheets/api.js';
	import SheetForm from './SheetForm.svelte';

	interface Props {
		open: boolean;
		characterId: string;
		characterName: string;
		sheet?: SheetSummary | undefined;
		onclose: () => void;
		onsaved: (result: SavedSheet) => void;
	}

	let { open, characterId, characterName, sheet, onclose, onsaved }: Props = $props();

	const titleId = $props.id();

	let submitting = $state(false);

	function close(): void {
		if (!submitting) {
			onclose();
		}
	}
</script>

<Dialog {open} labelledby={titleId} onclose={close}>
	{#key sheet?.id}
		<SheetForm
			{titleId}
			{characterId}
			{characterName}
			{sheet}
			{onsaved}
			oncancel={onclose}
			bind:submitting
		/>
	{/key}
</Dialog>

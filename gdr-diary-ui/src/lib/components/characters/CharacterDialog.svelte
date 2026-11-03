<script lang="ts">
	import type { CharacterDetail, CreatedCharacter } from '#lib/characters/api.js';
	import Dialog from '#lib/components/ui/Dialog.svelte';
	import CharacterForm from './CharacterForm.svelte';

	interface Props {
		open: boolean;
		character?: CharacterDetail | undefined;
		onclose: () => void;
		onsaved: (result: CreatedCharacter) => void;
	}

	let { open, character, onclose, onsaved }: Props = $props();

	const titleId = $props.id();

	let submitting = $state(false);

	function close(): void {
		if (!submitting) {
			onclose();
		}
	}
</script>

<Dialog {open} labelledby={titleId} onclose={close}>
	{#key character?.id}
		<CharacterForm {titleId} {character} {onsaved} oncancel={onclose} bind:submitting />
	{/key}
</Dialog>

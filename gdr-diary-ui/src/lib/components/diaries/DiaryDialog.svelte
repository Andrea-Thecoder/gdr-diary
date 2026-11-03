<script lang="ts">
	import type { DiaryDetail, SavedDiary } from '#lib/diaries/api.js';
	import Dialog from '#lib/components/ui/Dialog.svelte';
	import DiaryForm from './DiaryForm.svelte';

	interface Props {
		open: boolean;
		characterId: string;
		characterName: string;
		diary?: DiaryDetail | undefined;
		onclose: () => void;
		onsaved: (result: SavedDiary) => void;
	}

	let { open, characterId, characterName, diary, onclose, onsaved }: Props = $props();

	const titleId = $props.id();

	let submitting = $state(false);

	function close(): void {
		if (!submitting) {
			onclose();
		}
	}
</script>

<Dialog {open} labelledby={titleId} onclose={close}>
	{#key diary?.id}
		<DiaryForm
			{titleId}
			{characterId}
			{characterName}
			{diary}
			{onsaved}
			oncancel={onclose}
			bind:submitting
		/>
	{/key}
</Dialog>

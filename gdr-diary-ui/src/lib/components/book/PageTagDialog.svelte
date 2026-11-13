<script lang="ts">
	import { TAG_MAX_LENGTH } from '#lib/book/tags.js';
	import Button from '#lib/components/ui/Button.svelte';
	import Dialog from '#lib/components/ui/Dialog.svelte';
	import DialogHeader from '#lib/components/ui/DialogHeader.svelte';
	import TagChip from '#lib/components/ui/TagChip.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import { untrack } from 'svelte';

	interface Props {
		open: boolean;
		page: number;
		tag: string | null;
		suggestions: readonly string[];
		onsubmit: (text: string) => string | null;
		onremove: () => void;
		ontype: (text: string) => void;
		onclose: () => void;
	}

	let { open, page, tag, suggestions, onsubmit, onremove, ontype, onclose }: Props = $props();

	const titleId = $props.id();

	let text = $state('');
	let error = $state<string | null>(null);

	$effect(() => {
		if (open) {
			untrack(() => {
				text = tag ?? '';
				error = null;
			});
		}
	});

	$effect(() => {
		const typed = text;
		if (open) {
			untrack(() => {
				ontype(typed);
			});
		}
	});

	function submit(event: SubmitEvent): void {
		event.preventDefault();
		error = onsubmit(text);
		if (error === null) {
			onclose();
		}
	}
</script>

<Dialog {open} labelledby={titleId} {onclose}>
	<form onsubmit={submit} novalidate class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
		<DialogHeader
			{titleId}
			title={tag === null ? 'Nuovo tag' : 'Modifica il tag'}
			subtitle={`Pagina ${String(page)}`}
			{onclose}
		/>

		<div class="mt-[22px] flex flex-col gap-4">
			<TextField
				label="Nome del tag"
				bind:value={text}
				maxlength={TAG_MAX_LENGTH}
				autocomplete="off"
				placeholder="Per esempio: combattimento"
				{error}
			/>
			{#if suggestions.length > 0}
				<div role="group" aria-label="Tag già usati nel diario" class="flex flex-wrap gap-2">
					{#each suggestions as suggestion (suggestion)}
						<TagChip
							label={suggestion}
							onselect={() => {
								text = suggestion;
							}}
						/>
					{/each}
				</div>
			{/if}
		</div>

		<div class="mt-7 flex flex-wrap justify-end gap-3">
			{#if tag !== null}
				<Button variant="dangerOutline" size="form" class="mr-auto" onclick={onremove}>
					Togli il tag
				</Button>
			{/if}
			<Button variant="outline" size="form" onclick={onclose}>Annulla</Button>
			<Button type="submit" size="form">Salva</Button>
		</div>
	</form>
</Dialog>

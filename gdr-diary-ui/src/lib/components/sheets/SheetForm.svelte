<script lang="ts">
	import { untrack } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import Button from '#lib/components/ui/Button.svelte';
	import DialogHeader from '#lib/components/ui/DialogHeader.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import { createSheet, updateSheet, type SavedSheet, type SheetSummary } from '#lib/sheets/api.js';
	import PdfField from './PdfField.svelte';

	interface Props {
		titleId: string;
		characterId: string;
		characterName: string;
		sheet?: SheetSummary | undefined;
		onsaved: (result: SavedSheet) => void;
		oncancel: () => void;
		submitting?: boolean;
	}

	let {
		titleId,
		characterId,
		characterName,
		sheet,
		onsaved,
		oncancel,
		submitting = $bindable(false)
	}: Props = $props();

	const initial = untrack(() => sheet);
	const editing = initial !== undefined;

	let title = $state(initial?.title ?? '');
	let system = $state(initial?.system ?? '');
	let file = $state<File | null>(null);
	let titleError = $state<string | null>(null);
	let fileError = $state<string | null>(null);
	let submitError = $state<string | null>(null);

	const current = $derived(initial === undefined ? undefined : { name: 'PDF attuale' });

	function cancel(): void {
		if (!submitting) {
			oncancel();
		}
	}

	function choose(chosen: File): void {
		file = chosen;
		fileError = null;
	}

	async function submit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		submitError = null;
		titleError = title.trim() === '' ? 'Il titolo è obbligatorio.' : null;
		const missingFile = !editing && file === null;
		fileError = missingFile ? 'Scegli il PDF da caricare.' : null;
		if (titleError !== null || missingFile) {
			return;
		}
		submitting = true;
		try {
			const fields = { title, system };
			const id = initial?.id;
			if (id !== undefined) {
				onsaved(await updateSheet(getApi(), id, fields, file));
			} else if (file !== null) {
				onsaved({ sheet: await createSheet(getApi(), characterId, fields, file), fileError: null });
			}
		} catch (cause) {
			submitError =
				cause instanceof Error
					? cause.message
					: editing
						? 'Non riesco a salvare la scheda.'
						: 'Non riesco a caricare la scheda.';
		} finally {
			submitting = false;
		}
	}
</script>

<form onsubmit={submit} novalidate class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
	<DialogHeader
		{titleId}
		title={editing ? 'Modifica scheda' : 'Nuova scheda'}
		subtitle={editing
			? `Per ${characterName}. Cambia quello che vuoi.`
			: `Per ${characterName}. Carica il PDF e dagli un nome: il resto è facoltativo.`}
		onclose={cancel}
	/>

	<div class="mt-6">
		<PdfField {file} {current} error={fileError} onfile={choose} />
	</div>

	<div class="mt-[22px] flex flex-col gap-[18px]">
		<TextField
			label="Titolo"
			required
			bind:value={title}
			placeholder="Scheda livello 5"
			maxlength={100}
			error={titleError}
		/>
		<TextField
			label="Sistema di gioco"
			bind:value={system}
			placeholder="D&D 5e, Pathfinder 2e..."
			maxlength={100}
		/>
	</div>

	{#if submitError !== null}
		<p role="alert" class="mx-0 mt-5 mb-0 text-base text-label">{submitError}</p>
	{/if}

	<div class="mt-7 flex flex-wrap justify-end gap-3">
		<Button variant="outline" size="form" onclick={cancel}>Annulla</Button>
		<Button type="submit" size="form" disabled={submitting}>
			{#if editing}
				{submitting ? 'Salvo…' : 'Salva le modifiche'}
			{:else}
				{submitting ? 'Archivio…' : 'Archivia la scheda'}
			{/if}
		</Button>
	</div>
</form>

<script lang="ts">
	import { untrack } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import type { ImageChange } from '#lib/api/multipart.js';
	import {
		AVATAR_TYPES,
		COLOR_PRESETS,
		DEFAULT_COLOR,
		isHexColor
	} from '#lib/characters/appearance.js';
	import Button from '#lib/components/ui/Button.svelte';
	import ColorField from '#lib/components/ui/ColorField.svelte';
	import DialogHeader from '#lib/components/ui/DialogHeader.svelte';
	import ImageUpload from '#lib/components/ui/ImageUpload.svelte';
	import SelectField from '#lib/components/ui/SelectField.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import { COVER_FRAME } from '#lib/diaries/appearance.js';
	import { createDiary, updateDiary, type DiaryDetail, type SavedDiary } from '#lib/diaries/api.js';
	import { fontOptions, sizeOptions } from '#lib/diaries/typography.js';
	import ClosedBook from './ClosedBook.svelte';

	interface Props {
		titleId: string;
		characterId: string;
		characterName: string;
		diary?: DiaryDetail | undefined;
		onsaved: (result: SavedDiary) => void;
		oncancel: () => void;
		submitting?: boolean;
	}

	let {
		titleId,
		characterId,
		characterName,
		diary,
		onsaved,
		oncancel,
		submitting = $bindable(false)
	}: Props = $props();

	const initial = untrack(() => diary);
	const editing = initial !== undefined;
	const existingCover = initial?.coverUrl ?? null;
	const initialFont = initial?.font ?? '';
	const initialSize =
		initial?.fontSize === null || initial?.fontSize === undefined ? '' : String(initial.fontSize);

	let title = $state(initial?.title ?? '');
	let description = $state(initial?.description ?? '');
	let hex = $state<string>(
		initial?.coverColor !== null &&
			initial?.coverColor !== undefined &&
			isHexColor(initial.coverColor)
			? initial.coverColor
			: DEFAULT_COLOR
	);
	let font = $state(initialFont);
	let fontSize = $state(initialSize);
	let removeExisting = $state(false);
	let cover = $state<File | null>(null);
	let coverPreview = $state<string | null>(null);
	let titleError = $state<string | null>(null);
	let submitError = $state<string | null>(null);

	const shownCover = $derived(coverPreview ?? (removeExisting ? null : existingCover));
	const hexValid = $derived(isHexColor(hex));
	const hexError = $derived(hexValid ? null : 'Usa il formato #RRGGBB, per esempio #3B2A6B.');
	const previewColor = $derived(hexValid ? hex : DEFAULT_COLOR);

	$effect(() => {
		const url = coverPreview;
		return () => {
			if (url !== null) {
				URL.revokeObjectURL(url);
			}
		};
	});

	function chooseFile(file: File): void {
		cover = file;
		coverPreview = URL.createObjectURL(file);
	}

	function removeFile(): void {
		removeExisting = existingCover !== null;
		cover = null;
		coverPreview = null;
	}

	function coverChange(): ImageChange {
		if (cover !== null) {
			return { type: 'replace', file: cover };
		}
		return removeExisting ? { type: 'remove' } : { type: 'keep' };
	}

	function cancel(): void {
		if (!submitting) {
			oncancel();
		}
	}

	async function submit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		submitError = null;
		titleError = title.trim() === '' ? 'Il titolo è obbligatorio.' : null;
		if (titleError !== null || !hexValid) {
			return;
		}
		submitting = true;
		try {
			const fields = { title, description, coverColor: hex.toUpperCase(), font, fontSize };
			const result =
				diary?.id === undefined
					? await createDiary(getApi(), characterId, fields, cover)
					: await updateDiary(getApi(), diary.id, fields, coverChange());
			onsaved(result);
		} catch (cause) {
			submitError =
				cause instanceof Error
					? cause.message
					: editing
						? 'Non riesco a salvare il diario.'
						: 'Non riesco a creare il diario.';
		} finally {
			submitting = false;
		}
	}
</script>

<form onsubmit={submit} novalidate class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
	<DialogHeader
		{titleId}
		title={editing ? 'Modifica diario' : 'Nuovo diario'}
		subtitle={editing
			? `Per ${characterName}. Cambia quello che vuoi.`
			: `Per ${characterName}. Basta un titolo per cominciare: il resto lo aggiungi quando vuoi.`}
		onclose={cancel}
	/>

	<ImageUpload
		hasImage={shownCover !== null}
		accept={AVATAR_TYPES}
		chooseLabel="Carica una copertina"
		changeLabel="Cambia copertina"
		hint="Facoltativa. PNG, JPEG o WebP. Se la carichi, prende il posto del colore."
		rejectMessage="Formato non supportato: usa un file PNG, JPEG o WebP."
		crop={COVER_FRAME}
		onfile={chooseFile}
		onremove={removeFile}
	>
		{#snippet preview()}
			<ClosedBook
				title={title.trim() === '' ? 'Il tuo diario' : title.trim()}
				color={previewColor}
				coverUrl={shownCover}
				size="preview"
			/>
		{/snippet}
	</ImageUpload>

	<div class="mt-[22px] flex flex-col gap-[18px]">
		<TextField
			label="Titolo"
			required
			bind:value={title}
			placeholder="Cronache del Nord"
			maxlength={255}
			error={titleError}
		/>
		<TextField
			label="Descrizione"
			multiline
			bind:value={description}
			placeholder="Di che cosa parla questo diario."
			maxlength={2000}
		/>
		<ColorField
			label="Colore della copertina"
			bind:value={hex}
			presets={COLOR_PRESETS}
			error={hexError}
		/>
		<div>
			<div class="flex flex-wrap gap-4">
				<SelectField
					label="Font"
					bind:value={font}
					options={fontOptions(initialFont)}
					class="min-w-0 flex-[1_1_200px]"
				/>
				<SelectField
					label="Dimensione"
					bind:value={fontSize}
					options={sizeOptions(initialSize)}
					class="min-w-0 flex-[1_1_200px]"
				/>
			</div>
			<p class="mx-0 mt-2 mb-0 text-base text-soft">
				Font e dimensione si applicano solo alle pagine di questo diario. Se lasciati vuoti,
				verranno utilizzati i valori impostati nelle impostazioni generali.
			</p>
		</div>
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
				{submitting ? 'Rilego…' : 'Rilega il diario'}
			{/if}
		</Button>
	</div>
</form>

<script lang="ts">
	import { untrack } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import type { ImageChange } from '#lib/api/multipart.js';
	import { AVATAR_TYPES } from '#lib/characters/appearance.js';
	import Button from '#lib/components/ui/Button.svelte';
	import Dialog from '#lib/components/ui/Dialog.svelte';
	import DialogHeader from '#lib/components/ui/DialogHeader.svelte';
	import ImageUpload from '#lib/components/ui/ImageUpload.svelte';
	import { changeAvatar } from '#lib/profile/api.js';
	import { AVATAR_FRAME } from '#lib/profile/avatar.js';
	import { profileAvatar } from '#lib/profile/avatar.svelte.js';
	import UserAvatar from './UserAvatar.svelte';

	interface Props {
		open: boolean;
		name: string;
		onclose: () => void;
		onsaved: () => void;
	}

	let { open, name, onclose, onsaved }: Props = $props();

	const titleId = $props.id();

	let file = $state<File | null>(null);
	let preview = $state<string | null>(null);
	let removeExisting = $state(false);
	let submitError = $state<string | null>(null);
	let submitting = $state(false);

	const shown = $derived(preview ?? (removeExisting ? null : profileAvatar.url));
	const change = $derived<ImageChange>(
		file !== null
			? { type: 'replace', file }
			: removeExisting
				? { type: 'remove' }
				: { type: 'keep' }
	);

	$effect(() => {
		const url = preview;
		return () => {
			if (url !== null) {
				URL.revokeObjectURL(url);
			}
		};
	});

	$effect(() => {
		if (open) {
			untrack(() => {
				file = null;
				preview = null;
				removeExisting = false;
				submitError = null;
			});
		}
	});

	function close(): void {
		if (!submitting) {
			onclose();
		}
	}

	function chooseFile(chosen: File): void {
		file = chosen;
		preview = URL.createObjectURL(chosen);
		removeExisting = false;
	}

	function removeFile(): void {
		removeExisting = profileAvatar.url !== null;
		file = null;
		preview = null;
	}

	async function submit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		if (change.type === 'keep') {
			onclose();
			return;
		}
		submitError = null;
		submitting = true;
		try {
			profileAvatar.url = await changeAvatar(getApi(), change);
			onsaved();
		} catch (cause) {
			submitError =
				cause instanceof Error ? cause.message : 'Non riesco a salvare l’immagine del profilo.';
		} finally {
			submitting = false;
		}
	}
</script>

<Dialog {open} labelledby={titleId} onclose={close}>
	<form onsubmit={submit} novalidate class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
		<DialogHeader
			{titleId}
			title="Immagine del profilo"
			subtitle="Compare nel menu in alto e nel tuo profilo."
			onclose={close}
		/>

		<ImageUpload
			hasImage={shown !== null}
			accept={AVATAR_TYPES}
			chooseLabel="Carica un’immagine"
			changeLabel="Cambia immagine"
			hint="Facoltativa. PNG, JPEG o WebP. Dopo averla scelta la inquadri come preferisci."
			rejectMessage="Formato non supportato: usa un file PNG, JPEG o WebP."
			crop={AVATAR_FRAME}
			onfile={chooseFile}
			onremove={removeFile}
		>
			{#snippet preview()}
				<UserAvatar
					label={name}
					url={shown}
					class="size-24 border-2 border-orn font-display text-[44px] leading-none font-bold"
				/>
			{/snippet}
		</ImageUpload>

		{#if submitError !== null}
			<p role="alert" class="mx-0 mt-5 mb-0 text-base text-label">{submitError}</p>
		{/if}

		<div class="mt-7 flex flex-wrap justify-end gap-3">
			<Button variant="outline" size="form" onclick={close}>Annulla</Button>
			<Button type="submit" size="form" disabled={submitting || change.type === 'keep'}>
				{submitting ? 'Salvo…' : 'Salva l’immagine'}
			</Button>
		</div>
	</form>
</Dialog>

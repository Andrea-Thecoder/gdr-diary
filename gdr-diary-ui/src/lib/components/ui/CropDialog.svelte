<script lang="ts">
	import { untrack } from 'svelte';
	import {
		MAX_OUTPUT_SIDE,
		frameAspect,
		initialView,
		outputSize,
		sourceRect,
		type CropFrame,
		type CropView,
		type Size
	} from '#lib/ui/crop.js';
	import { loadImage, renderCrop } from '#lib/ui/crop-image.js';
	import Button from './Button.svelte';
	import Dialog from './Dialog.svelte';
	import DialogHeader from './DialogHeader.svelte';
	import ImageCropper from './ImageCropper.svelte';

	interface Props {
		open: boolean;
		file: File | null;
		frame: CropFrame;
		title?: string;
		subtitle?: string;
		label?: string;
		confirmLabel?: string;
		onconfirm: (file: File) => void;
		oncancel: () => void;
	}

	let {
		open,
		file,
		frame,
		title = 'Inquadra l’immagine',
		subtitle = 'Sposta e ingrandisci la foto dentro la cornice.',
		label = 'Inquadratura dell’immagine',
		confirmLabel = 'Ritaglia',
		onconfirm,
		oncancel
	}: Props = $props();

	const titleId = $props.id();

	let image = $state<HTMLImageElement | null>(null);
	let view = $state<CropView>({ zoom: 1, centerX: 0, centerY: 0 });
	let unreadable = $state(false);
	let failure = $state<string | null>(null);
	let working = $state(false);

	const size = $derived<Size | null>(
		image === null ? null : { width: image.naturalWidth, height: image.naturalHeight }
	);

	$effect(() => {
		if (!open || file === null) {
			return;
		}
		const url = URL.createObjectURL(file);
		let stale = false;
		untrack(() => {
			image = null;
			unreadable = false;
			failure = null;
			working = false;
		});
		void loadImage(url).then(
			(loaded) => {
				if (!stale) {
					view = initialView({ width: loaded.naturalWidth, height: loaded.naturalHeight });
					image = loaded;
				}
			},
			() => {
				if (!stale) {
					unreadable = true;
				}
			}
		);
		return () => {
			stale = true;
			URL.revokeObjectURL(url);
		};
	});

	async function confirm(): Promise<void> {
		if (image === null || size === null || file === null || working) {
			return;
		}
		working = true;
		failure = null;
		try {
			const source = sourceRect(view, size, frameAspect(frame));
			const cropped = await renderCrop(
				image,
				source,
				outputSize(source, MAX_OUTPUT_SIDE),
				file.name
			);
			onconfirm(cropped);
		} catch (cause) {
			failure = cause instanceof Error ? cause.message : 'Non riesco a ritagliare l’immagine.';
		} finally {
			working = false;
		}
	}
</script>

<Dialog {open} labelledby={titleId} onclose={oncancel}>
	<div class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
		<DialogHeader {titleId} {title} {subtitle} onclose={oncancel} />

		<div class="mt-6">
			{#if image !== null && size !== null}
				<ImageCropper src={image.src} {size} {frame} bind:view {label} />
			{:else if unreadable}
				<p role="alert" class="m-0 text-base text-label">
					Non riesco a leggere questa immagine. Prova con un altro file.
				</p>
			{:else}
				<p role="status" class="m-0 text-base text-soft">Carico l’immagine…</p>
			{/if}
		</div>

		{#if failure !== null}
			<p role="alert" class="mx-0 mt-5 mb-0 text-base text-label">{failure}</p>
		{/if}

		<div class="mt-7 flex flex-wrap justify-end gap-3">
			<Button variant="outline" size="form" onclick={oncancel}>Annulla</Button>
			<Button
				size="form"
				disabled={image === null || working}
				onclick={() => {
					void confirm();
				}}
			>
				{working ? 'Ritaglio…' : confirmLabel}
			</Button>
		</div>
	</div>
</Dialog>

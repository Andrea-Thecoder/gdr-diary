<script lang="ts">
	import { untrack } from 'svelte';
	import Button from './Button.svelte';
	import Dialog from './Dialog.svelte';
	import DialogHeader from './DialogHeader.svelte';

	interface Props {
		open: boolean;
		title: string;
		subtitle: string;
		consequences: string[];
		intro?: string;
		warning?: string;
		confirmLabel?: string;
		pendingLabel?: string;
		fallbackError?: string;
		onconfirm: () => Promise<void>;
		onclose: () => void;
	}

	let {
		open,
		title,
		subtitle,
		consequences,
		intro = 'Verranno cancellati per sempre:',
		warning = 'Non si può annullare e non c’è modo di recuperare i dati.',
		confirmLabel = 'Elimina per sempre',
		pendingLabel = 'Elimino…',
		fallbackError = 'Non riesco a eliminare. Riprova più tardi.',
		onconfirm,
		onclose
	}: Props = $props();

	const titleId = $props.id();

	let error = $state<string | null>(null);
	let submitting = $state(false);

	$effect(() => {
		if (open) {
			untrack(() => {
				error = null;
				submitting = false;
			});
		}
	});

	function close(): void {
		if (!submitting) {
			onclose();
		}
	}

	async function confirm(): Promise<void> {
		error = null;
		submitting = true;
		try {
			await onconfirm();
		} catch (cause) {
			error = cause instanceof Error ? cause.message : fallbackError;
			submitting = false;
		}
	}
</script>

<Dialog {open} labelledby={titleId} onclose={close}>
	<div class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
		<DialogHeader {titleId} {title} {subtitle} onclose={close} />

		<div class="mt-6 text-[18px] text-ink">
			<p class="m-0">{intro}</p>
			<ul class="mx-0 mt-2 mb-0 list-disc pl-6 text-soft">
				{#each consequences as consequence (consequence)}
					<li>{consequence}</li>
				{/each}
			</ul>
			<p class="mx-0 mt-5 mb-0 font-semibold text-danger">{warning}</p>
		</div>

		{#if error !== null}
			<p role="alert" class="mx-0 mt-5 mb-0 text-base text-danger">{error}</p>
		{/if}

		<div class="mt-7 flex flex-wrap justify-end gap-3">
			<Button variant="outline" size="form" disabled={submitting} onclick={close}>Annulla</Button>
			<Button variant="danger" size="form" disabled={submitting} onclick={confirm}>
				{submitting ? pendingLabel : confirmLabel}
			</Button>
		</div>
	</div>
</Dialog>

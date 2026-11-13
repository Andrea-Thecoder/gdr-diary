<script lang="ts">
	import Button from '#lib/components/ui/Button.svelte';
	import Dialog from '#lib/components/ui/Dialog.svelte';
	import DialogHeader from '#lib/components/ui/DialogHeader.svelte';

	interface Props {
		open: boolean;
		onstay: () => void;
		onleave: () => void;
	}

	let { open, onstay, onleave }: Props = $props();

	const titleId = $props.id();
</script>

<Dialog {open} labelledby={titleId} onclose={onstay}>
	<div class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
		<DialogHeader
			{titleId}
			title="Modifiche non salvate"
			subtitle="Se esci adesso perdi quello che hai scritto nella scheda."
			onclose={onstay}
		/>
		<div class="mt-7 flex flex-wrap justify-end gap-3">
			<Button variant="outline" size="form" onclick={onstay}>Resta qui</Button>
			<Button variant="danger" size="form" onclick={onleave}>Esci senza salvare</Button>
		</div>
	</div>
</Dialog>

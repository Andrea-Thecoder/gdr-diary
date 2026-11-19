<script lang="ts">
	import Dialog from './Dialog.svelte';

	interface Props {
		refuseClose?: boolean;
		onclose?: () => void;
	}

	let { refuseClose = false, onclose = () => undefined }: Props = $props();

	let open = $state(false);

	function close(): void {
		onclose();
		if (!refuseClose) {
			open = false;
		}
	}
</script>

<button type="button" onclick={() => (open = true)}>Apri</button>
<Dialog {open} labelledby="harness-title" onclose={close}>
	<div class="p-8">
		<h2 id="harness-title">Titolo</h2>
		<input aria-label="Dentro" />
	</div>
</Dialog>

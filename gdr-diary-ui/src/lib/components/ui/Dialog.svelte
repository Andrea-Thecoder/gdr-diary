<script lang="ts">
	import { tick, type Snippet } from 'svelte';
	import { createBackdropPress } from '#lib/ui/outside.js';

	interface Props {
		open: boolean;
		labelledby: string;
		onclose: () => void;
		children: Snippet;
	}

	let { open, labelledby, onclose, children }: Props = $props();

	let element = $state<HTMLDialogElement | null>(null);

	$effect(() => {
		if (element === null) {
			return;
		}
		if (open && !element.open) {
			element.showModal();
		} else if (!open && element.open) {
			element.close();
		}
	});

	function cancel(event: Event): void {
		event.preventDefault();
		onclose();
	}

	function closed(): void {
		if (open) {
			onclose();
			void reopen();
		}
	}

	async function reopen(): Promise<void> {
		await tick();
		if (open && element !== null && !element.open) {
			element.showModal();
		}
	}

	const press = createBackdropPress(
		(target) => target === element,
		() => {
			onclose();
		}
	);
</script>

<dialog
	bind:this={element}
	aria-labelledby={labelledby}
	oncancel={cancel}
	onclose={closed}
	onpointerdown={press.onpointerdown}
	onclick={press.onclick}
	class="m-auto w-[calc(100%-32px)] max-w-[600px] overflow-auto rounded-[8px] border border-(color:--card-border) p-0 text-ink shadow-(--modal-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card max-[559px]:mt-auto max-[559px]:mb-0 max-[559px]:max-h-[94dvh] max-[559px]:w-full max-[559px]:max-w-none max-[559px]:rounded-t-[14px] max-[559px]:rounded-b-none min-[560px]:max-h-[calc(100dvh-48px)]"
>
	{#if open}
		{@render children()}
	{/if}
</dialog>

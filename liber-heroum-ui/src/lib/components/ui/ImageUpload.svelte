<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { CropFrame } from '#lib/ui/crop.js';
	import Button from './Button.svelte';
	import CropDialog from './CropDialog.svelte';
	import Icon from './Icon.svelte';

	interface Props {
		preview: Snippet;
		hasImage: boolean;
		accept: readonly string[];
		chooseLabel: string;
		changeLabel: string;
		hint: string;
		rejectMessage: string;
		crop?: CropFrame | undefined;
		onfile: (file: File) => void;
		onremove: () => void;
	}

	let {
		preview,
		hasImage,
		accept,
		chooseLabel,
		changeLabel,
		hint,
		rejectMessage,
		crop,
		onfile,
		onremove
	}: Props = $props();

	let input = $state<HTMLInputElement | null>(null);
	let rejected = $state(false);
	let pending = $state<File | null>(null);

	function choose(event: Event & { currentTarget: HTMLInputElement }): void {
		const target = event.currentTarget;
		const file = target.files?.[0];
		if (file === undefined) {
			return;
		}
		if (!accept.includes(file.type)) {
			rejected = true;
			target.value = '';
			return;
		}
		rejected = false;
		if (crop === undefined) {
			onfile(file);
			return;
		}
		pending = file;
		target.value = '';
	}

	function remove(): void {
		rejected = false;
		if (input !== null) {
			input.value = '';
		}
		onremove();
	}
</script>

<div class="mt-6 flex flex-wrap items-center gap-x-5 gap-y-4">
	{@render preview()}
	<div class="min-w-0 flex-[1_1_220px]">
		<div class="flex flex-wrap items-center gap-3">
			<Button variant="outline" size="form" onclick={() => input?.click()}>
				<Icon size={16} strokeWidth={1.8}>
					<path d="M12 16V4M7 9l5-5 5 5M5 20h14" />
				</Icon>
				{hasImage ? changeLabel : chooseLabel}
			</Button>
			{#if hasImage}
				<button
					type="button"
					onclick={remove}
					class="inline-flex min-h-[44px] cursor-pointer items-center text-soft underline underline-offset-4 hover:opacity-80"
				>
					Rimuovi
				</button>
			{/if}
		</div>
		<input
			bind:this={input}
			type="file"
			accept={accept.join(',')}
			onchange={choose}
			aria-label="File dell’immagine"
			class="sr-only"
			tabindex="-1"
		/>
		<p class="mx-0 mt-2 mb-0 text-base text-soft">{hint}</p>
		{#if rejected}
			<p role="alert" class="mx-0 mt-1.5 mb-0 text-base text-label">{rejectMessage}</p>
		{/if}
	</div>
</div>

{#if crop !== undefined}
	<CropDialog
		open={pending !== null}
		file={pending}
		frame={crop}
		onconfirm={(cropped: File) => {
			pending = null;
			onfile(cropped);
		}}
		oncancel={() => {
			pending = null;
		}}
	/>
{/if}

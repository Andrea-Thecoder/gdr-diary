<script lang="ts">
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { checkSheetFile, MAX_SHEET_LABEL } from '#lib/sheets/format.js';

	interface Current {
		name: string;
	}

	interface Props {
		file: File | null;
		current?: Current | undefined;
		error?: string | null;
		onfile: (file: File) => void;
	}

	let { file, current, error = null, onfile }: Props = $props();

	const labelId = $props.id();
	const errorId = `${labelId}-error`;

	let input = $state<HTMLInputElement | null>(null);
	let zone = $state<HTMLDivElement | null>(null);
	let rejected = $state<string | null>(null);
	let dragging = $state(false);

	const shown = $derived(file === null ? current : { name: file.name });
	const message = $derived(rejected ?? error);

	function take(candidate: File | undefined): void {
		if (candidate === undefined) {
			return;
		}
		const problem = checkSheetFile(candidate);
		rejected = problem;
		if (problem === null) {
			onfile(candidate);
		}
	}

	function choose(event: Event & { currentTarget: HTMLInputElement }): void {
		take(event.currentTarget.files?.[0]);
		event.currentTarget.value = '';
	}

	function pick(): void {
		input?.click();
	}

	$effect(() => {
		const target = zone;
		if (target === null) {
			return;
		}
		const over = (event: DragEvent): void => {
			event.preventDefault();
			dragging = true;
		};
		const leave = (): void => {
			dragging = false;
		};
		const drop = (event: DragEvent): void => {
			event.preventDefault();
			dragging = false;
			take(event.dataTransfer?.files[0]);
		};
		target.addEventListener('dragover', over);
		target.addEventListener('dragleave', leave);
		target.addEventListener('drop', drop);
		return () => {
			target.removeEventListener('dragover', over);
			target.removeEventListener('dragleave', leave);
			target.removeEventListener('drop', drop);
		};
	});
</script>

<div>
	<div
		id={labelId}
		class="mb-1.5 font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
	>
		File PDF *
	</div>
	<div
		bind:this={zone}
		role="group"
		aria-labelledby={labelId}
		aria-describedby={message === null ? undefined : errorId}
		class={[
			'rounded-[6px]',
			shown === undefined
				? [
						'flex flex-col items-center gap-2.5 border-2 border-dashed bg-(--ghost-bg) px-5 py-[26px] text-center',
						dragging ? 'border-orn' : 'border-(--ghost-border)'
					]
				: 'flex items-center gap-4 border border-(--field-border) bg-(--field-bg) px-4 py-3.5'
		]}
	>
		{#if shown === undefined}
			<Icon size={34} strokeWidth={1.6} class="text-orn">
				<path d="M12 16V4M7 9l5-5 5 5M5 20h14" />
			</Icon>
			<span class="text-[18px] text-ink">Trascina qui il PDF oppure</span>
			<Button variant="outline" size="form" onclick={pick}>Scegli un file</Button>
		{:else}
			<span
				aria-hidden="true"
				class="relative flex h-[58px] w-[46px] flex-none items-end justify-center rounded-[3px] border border-[#8A7650] bg-[#FBF7EC] pb-1.5"
			>
				<span
					class="rounded-[2px] bg-[#A8271F] px-1.5 py-0.5 font-label text-[10px] font-semibold tracking-[.1em] text-[#FFF3E0]"
				>
					PDF
				</span>
			</span>
			<span class="min-w-0 flex-1 truncate text-[18px] text-ink">{shown.name}</span>
			<Button variant="outline" size="sm" onclick={pick}>Cambia</Button>
		{/if}
	</div>
	<input
		bind:this={input}
		type="file"
		accept="application/pdf,.pdf"
		onchange={choose}
		aria-label="File del PDF"
		class="sr-only"
		tabindex="-1"
	/>
	<p class="mx-0 mt-2 mb-0 text-base text-soft">Solo file PDF, fino a {MAX_SHEET_LABEL}.</p>
	{#if message !== null}
		<p id={errorId} role="alert" class="mx-0 mt-1.5 mb-0 text-base text-label">{message}</p>
	{/if}
</div>

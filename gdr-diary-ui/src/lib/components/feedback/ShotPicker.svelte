<script lang="ts">
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { formatSize, SHOT_TYPES, SHOTS_MAX } from '#lib/feedback/feedback.js';

	interface Props {
		shots: readonly File[];
		problem: string | null;
		onadd: (files: File[]) => void;
		onremove: (index: number) => void;
	}

	let { shots, problem, onadd, onremove }: Props = $props();

	const labelId = $props.id();
	let input = $state<HTMLInputElement | null>(null);
	let over = $state(false);

	function choose(): void {
		input?.click();
	}

	function picked(): void {
		onadd(Array.from(input?.files ?? []));
		if (input !== null) {
			input.value = '';
		}
	}

	function dropped(event: DragEvent): void {
		event.preventDefault();
		over = false;
		onadd(Array.from(event.dataTransfer?.files ?? []));
	}
</script>

<div>
	<div
		id={labelId}
		class="mb-1.5 font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
	>
		Screenshot (facoltativo)
	</div>
	<input
		bind:this={input}
		type="file"
		accept={SHOT_TYPES.join(',')}
		multiple
		onchange={picked}
		tabindex="-1"
		aria-label="Scegli le schermate da allegare"
		class="sr-only"
	/>
	{#if shots.length > 0}
		<ul class="m-0 flex list-none flex-col gap-2 p-0" aria-labelledby={labelId}>
			{#each shots as shot, index (`${shot.name}-${String(index)}`)}
				<li
					class="flex min-h-[56px] items-center gap-3 rounded-[6px] border border-(--field-border) bg-(--field-bg) px-3.5"
				>
					<Icon size={24} strokeWidth={1.6} class="flex-none text-orn">
						<rect x="3" y="4" width="18" height="16" rx="2" />
						<circle cx="9" cy="10" r="1.6" />
						<path d="M21 16l-5-5-8 8" />
					</Icon>
					<span class="min-w-0 flex-1">
						<span class="block truncate text-[17px] text-ink">{shot.name}</span>
						<span class="block text-[14px] text-soft">{formatSize(shot.size)}</span>
					</span>
					<button
						type="button"
						aria-label={`Rimuovi ${shot.name}`}
						onclick={() => {
							onremove(index);
						}}
						class="inline-flex size-[44px] flex-none cursor-pointer items-center justify-center rounded-full border-0 bg-transparent text-soft hover:opacity-80"
					>
						<Icon size={20} strokeWidth={2}>
							<path d="M6 6l12 12M18 6L6 18" />
						</Icon>
					</button>
				</li>
			{/each}
		</ul>
		{#if shots.length < SHOTS_MAX}
			<Button variant="outline" size="sm" onclick={choose} class="mt-3">Aggiungi un’altra</Button>
		{/if}
	{:else}
		<div
			role="group"
			aria-labelledby={labelId}
			ondragover={(event) => {
				event.preventDefault();
				over = true;
			}}
			ondragleave={() => {
				over = false;
			}}
			ondrop={dropped}
			class={[
				'flex flex-wrap items-center justify-center gap-x-4 gap-y-2 rounded-[6px] border border-dashed px-4 py-6 text-center text-soft',
				over ? 'border-orn' : 'border-(--field-border)'
			]}
		>
			<span>Trascina qui un’immagine oppure</span>
			<Button variant="outline" size="sm" onclick={choose}>Scegli un file</Button>
		</div>
	{/if}
	{#if problem !== null}
		<p role="alert" class="mt-2 mb-0 text-base text-label">{problem}</p>
	{/if}
	<p class="mt-2 mb-0 text-[15px] text-soft">
		PNG, JPEG o WebP, al massimo {SHOTS_MAX} e fino a 5 MB in tutto. Se nella schermata c’è il testo del
		tuo diario, lo leggo anch’io.
	</p>
</div>

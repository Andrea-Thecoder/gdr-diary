<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		position: number;
		count: number;
		written: number;
		describe: (position: number) => string;
		mark: (position: number) => string;
		ongo: (position: number) => Promise<unknown>;
		class?: string;
		end?: Snippet;
	}

	let {
		position,
		count,
		written,
		describe,
		mark,
		ongo,
		class: className = '',
		end
	}: Props = $props();

	let preview = $state<number | null>(null);
	let landing = $state<number | null>(null);
	let active = $state(false);

	const shown = $derived(Math.min(Math.max(preview ?? landing ?? position, 1), Math.max(count, 1)));
	const fraction = $derived(count > 1 ? (shown - 1) / (count - 1) : 0);
	const label = $derived(written === 1 ? '1 pagina scritta' : `${String(written)} pagine scritte`);
</script>

<div class={['flex items-center gap-3.5', className]}>
	<div class="relative min-w-0 flex-1">
		<input
			type="range"
			min={1}
			max={Math.max(count, 1)}
			step={1}
			value={shown}
			disabled={count < 2}
			aria-label="Sfoglia le pagine"
			aria-valuetext={describe(shown)}
			style:--fraction={String(fraction)}
			class="scrubber"
			oninput={(event) => {
				preview = Number(event.currentTarget.value);
			}}
			onchange={(event) => {
				const target = Number(event.currentTarget.value);
				preview = null;
				landing = target;
				void ongo(target).finally(() => {
					if (landing === target) {
						landing = null;
					}
				});
			}}
			onpointerdown={() => {
				active = true;
			}}
			onpointerup={() => {
				active = false;
			}}
			onpointercancel={() => {
				active = false;
			}}
			onblur={() => {
				active = false;
				preview = null;
			}}
		/>
		{#if active && preview !== null}
			<span aria-hidden="true" class="scrubber-bubble" style:--fraction={String(fraction)}>
				{mark(shown)}
			</span>
		{/if}
	</div>
	<div class="text-[13px] whitespace-nowrap text-stage-ink">{label}</div>
	{#if end !== undefined}
		{@render end()}
	{/if}
</div>

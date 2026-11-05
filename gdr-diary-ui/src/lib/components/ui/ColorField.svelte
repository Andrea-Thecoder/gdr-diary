<script lang="ts">
	interface Preset {
		name: string;
		hex: string;
	}

	interface Props {
		label: string;
		value?: string;
		presets: readonly Preset[];
		error?: string | null;
	}

	let { label, value = $bindable(''), presets, error = null }: Props = $props();

	const labelId = $props.id();
</script>

<div>
	<div
		id={labelId}
		class="mb-1.5 font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
	>
		{label}
	</div>
	<div role="group" aria-labelledby={labelId} class="flex flex-wrap items-center gap-x-1.5 gap-y-1">
		{#each presets as preset (preset.hex)}
			{@const selected = value.toUpperCase() === preset.hex}
			<button
				type="button"
				aria-label={`${preset.name} ${preset.hex}`}
				aria-pressed={selected}
				onclick={() => {
					value = preset.hex;
				}}
				class={[
					'flex size-[44px] cursor-pointer items-center justify-center rounded-full border-2 p-0',
					selected ? 'border-orn' : 'border-transparent'
				]}
			>
				<span class="block size-7 rounded-full" style:background={preset.hex}></span>
			</button>
		{/each}
	</div>
	{#if error !== null}
		<p role="alert" class="mx-0 mt-1.5 mb-0 text-base text-label">{error}</p>
	{/if}
</div>

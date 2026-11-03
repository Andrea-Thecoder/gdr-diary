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
	const hexId = `${labelId}-hex`;
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
		<label for={hexId} class="sr-only">Colore in formato HEX</label>
		<input
			id={hexId}
			type="text"
			bind:value
			maxlength={7}
			aria-invalid={error === null ? undefined : true}
			class="ml-2.5 min-h-[46px] w-[116px] rounded-[6px] border border-(--field-border) bg-(--field-bg) px-3 text-[18px] text-ink"
		/>
	</div>
	{#if error !== null}
		<p role="alert" class="mx-0 mt-1.5 mb-0 text-base text-label">{error}</p>
	{/if}
</div>

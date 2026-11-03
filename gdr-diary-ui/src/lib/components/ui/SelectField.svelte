<script lang="ts">
	interface Option {
		value?: string;
		label: string;
	}

	interface Props {
		label: string;
		value?: string;
		options: readonly Option[];
		hideLabel?: boolean;
		class?: string;
	}

	let {
		label,
		value = $bindable(''),
		options,
		hideLabel = false,
		class: className = ''
	}: Props = $props();

	const id = $props.id();
</script>

<div class={className}>
	<label
		for={id}
		class={[
			hideLabel ? 'sr-only' : 'mb-1.5 block',
			'font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase'
		]}
	>
		{label}
	</label>
	<select
		{id}
		bind:value
		class="min-h-[50px] w-full cursor-pointer rounded-[6px] border border-(--field-border) bg-(--field-bg) px-3.5 text-[18px] text-ink"
	>
		{#each options as option (option.value)}
			<option value={option.value}>{option.label}</option>
		{/each}
	</select>
</div>

<script lang="ts">
	interface Props {
		value: number;
		min: number;
		max: number;
		unit: string;
		labelledby: string;
		onchange: (value: number) => void;
	}

	let { value, min, max, unit, labelledby, onchange }: Props = $props();
</script>

<div class="flex min-h-[44px] items-center gap-4">
	<input
		type="range"
		{min}
		{max}
		step="1"
		{value}
		aria-labelledby={labelledby}
		oninput={(event) => {
			onchange(Number(event.currentTarget.value));
		}}
		class="h-11 min-w-0 flex-1 cursor-pointer accent-orn"
	/>
	<output class="w-16 flex-none text-right text-[18px] text-ink">{value} {unit}</output>
</div>

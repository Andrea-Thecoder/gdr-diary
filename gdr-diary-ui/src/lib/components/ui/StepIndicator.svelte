<script lang="ts">
	interface Props {
		step: number;
		total: number;
		class?: string;
	}

	let { step, total, class: className = '' }: Props = $props();

	const label = $derived(`Passo ${String(step)} di ${String(total)}`);
</script>

<div class={['flex items-center gap-3', className]}>
	<div
		role="progressbar"
		aria-label={label}
		aria-valuemin={1}
		aria-valuemax={total}
		aria-valuenow={step}
		class="flex flex-1 gap-1.5"
	>
		{#each Array.from({ length: total }, (_, index) => index + 1) as position (position)}
			<div
				class={['h-1 flex-1 rounded-[2px]', position <= step ? 'bg-fill' : 'bg-(--track)']}
			></div>
		{/each}
	</div>
	<div
		aria-live="polite"
		class="font-label text-[12px] font-semibold tracking-[.16em] whitespace-nowrap text-label uppercase"
	>
		{label}
	</div>
</div>

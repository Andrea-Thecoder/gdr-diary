<script lang="ts">
	interface Props {
		value: number;
		max: number;
		label: string;
		tone?: 'page' | 'stage';
		class?: string;
	}

	let { value, max, label, tone = 'page', class: className = '' }: Props = $props();

	const percent = $derived(max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0);
</script>

<div class={['flex items-center gap-2.5', className]}>
	<div
		role="progressbar"
		aria-label={label}
		aria-valuemin={0}
		aria-valuemax={max}
		aria-valuenow={value}
		class="h-1 flex-1 rounded-[2px] bg-(--track)"
	>
		<div class="h-1 min-w-1.5 rounded-[2px] bg-fill" style:width={`${String(percent)}%`}></div>
	</div>
	<div
		class={['text-[13px] whitespace-nowrap', tone === 'page' ? 'text-page-soft' : 'text-stage-ink']}
	>
		{label}
	</div>
</div>

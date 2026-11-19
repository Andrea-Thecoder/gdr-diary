<script lang="ts">
	import WarningIcon from './WarningIcon.svelte';

	interface Props {
		value: string;
		labelledby?: string;
		warning?: boolean;
	}

	let { value, labelledby, warning = false }: Props = $props();
</script>

<p
	aria-labelledby={labelledby}
	class="m-0 flex min-h-[50px] items-center justify-between gap-3 rounded-[6px] border border-dashed border-(--field-border) bg-(--chip-bg) px-3.5 py-2 text-[18px] text-ink"
>
	<span class="min-w-0 [overflow-wrap:anywhere]">{value}</span>
	{#if warning}
		<WarningIcon size={22} class="flex-none text-warn" />
	{:else}
		<svg
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="1.6"
			stroke-linecap="round"
			stroke-linejoin="round"
			aria-hidden="true"
			class="flex-none text-soft"
		>
			<rect x="5" y="11" width="14" height="9" rx="2" />
			<path d="M8 11V8a4 4 0 0 1 8 0v3" />
		</svg>
	{/if}
</p>

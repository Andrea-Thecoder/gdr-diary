<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		color: number | null;
		tilt?: number;
		label?: string | undefined;
		title?: string | undefined;
		disabled?: boolean;
		onclick: () => void;
		class?: string;
		children: Snippet;
	}

	let {
		color,
		tilt = 0,
		label,
		title,
		disabled = false,
		onclick,
		class: className = '',
		children
	}: Props = $props();
</script>

<button
	type="button"
	aria-label={label}
	{title}
	{disabled}
	{onclick}
	style:background={color === null ? undefined : `var(--postit-${String(color)})`}
	style:rotate={`${String(tilt)}deg`}
	class={[
		'flex h-[104px] w-[48px] flex-none cursor-pointer flex-col items-center justify-start gap-2 rounded-[2px] px-1 py-2.5 font-hand text-[20px] leading-[1.1] text-postit-ink shadow-(--postit-shadow) transition-transform duration-150 hover:-translate-y-0.5 focus-visible:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(color:--accent) disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:translate-y-0 motion-reduce:transition-none',
		color === null && 'border border-dashed border-(--page-rule) bg-(--page)',
		className
	]}
>
	{@render children()}
</button>

<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		description: string;
		icon: Snippet;
		onclick?: () => void;
	}

	let { title, description, icon, onclick }: Props = $props();

	const shape =
		'flex h-full min-h-[380px] w-full min-w-0 flex-col items-center justify-center gap-4 rounded-[6px] border-2 border-dashed border-(--ghost-border) bg-(--ghost-bg) p-8 text-center text-ink';
</script>

<svelte:element
	this={onclick === undefined ? 'div' : 'button'}
	type={onclick === undefined ? undefined : 'button'}
	role={onclick === undefined ? undefined : 'button'}
	{onclick}
	class={[shape, onclick !== undefined && 'cursor-pointer hover:opacity-85']}
>
	<span
		aria-hidden="true"
		class="flex size-[88px] items-center justify-center rounded-full border-2 border-dashed border-orn text-orn"
	>
		{@render icon()}
	</span>
	<span class="font-display text-[32px] leading-[1.08] font-bold">{title}</span>
	<span class="max-w-[16em] text-[18px] text-soft">{description}</span>
</svelte:element>

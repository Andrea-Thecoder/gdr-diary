<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		tone: 'error' | 'info';
		children: Snippet;
		action?: Snippet;
		class?: string;
	}

	let { tone, children, action, class: className = '' }: Props = $props();
</script>

<div
	role={tone === 'error' ? 'alert' : 'status'}
	class={[
		'flex flex-wrap items-center justify-between gap-x-6 gap-y-3 rounded-[8px] border px-5 py-4 text-[18px] text-ink',
		tone === 'error' ? 'border-danger surface-err' : 'border-(color:--card-border) surface-card',
		className
	]}
>
	<p class="m-0 min-w-0 flex-[1_1_260px]">{@render children()}</p>
	{#if action !== undefined}
		{@render action()}
	{/if}
</div>

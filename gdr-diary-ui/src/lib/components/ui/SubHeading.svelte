<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		tone?: 'default' | 'dark-band';
		size?: 'sm' | 'md';
		as?: 'h2' | 'h3';
		id?: string;
		class?: string;
		children: Snippet;
	}

	let {
		tone = 'default',
		size = 'md',
		as = 'h3',
		id,
		class: className = '',
		children
	}: Props = $props();
</script>

<svelte:element
	this={as}
	{id}
	class={[
		'm-0 font-display font-bold',
		tone === 'dark-band' ? 'text-s4-h3' : 'text-h3',
		size === 'sm' ? 'text-[28px] leading-[1.1]' : 'text-[31px] leading-[1.08]',
		className
	]}
>
	{@render children()}
</svelte:element>

<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAttributes } from 'svelte/elements';

	interface Props extends HTMLAttributes<HTMLElement> {
		as?: 'div' | 'section' | 'header' | 'footer';
		class?: string;
		children: Snippet;
	}

	let { as = 'div', class: className = '', children, ...rest }: Props = $props();
</script>

<svelte:element
	this={as}
	{...rest}
	class={[
		'mx-auto max-w-[1200px] pr-[max(clamp(16px,4vw,48px),env(safe-area-inset-right))] pl-[max(clamp(16px,4vw,48px),env(safe-area-inset-left))]',
		className
	]}
>
	{@render children()}
</svelte:element>

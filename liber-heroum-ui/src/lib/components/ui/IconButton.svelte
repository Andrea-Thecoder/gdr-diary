<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends Omit<
		HTMLButtonAttributes,
		'children' | 'type' | 'class' | 'aria-label' | 'title'
	> {
		label: string;
		class?: string;
		element?: HTMLButtonElement | null;
		children: Snippet;
	}

	let {
		label,
		class: className = '',
		element = $bindable(null),
		children,
		...rest
	}: Props = $props();
</script>

<button
	bind:this={element}
	type="button"
	aria-label={label}
	title={label}
	{...rest}
	class={[
		'inline-flex size-[44px] cursor-pointer items-center justify-center rounded-[4px] border border-(color:--nav-border) text-nav-ink hover:opacity-80',
		className
	]}
>
	{@render children()}
</button>

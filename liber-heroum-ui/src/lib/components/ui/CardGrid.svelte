<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAttributes } from 'svelte/elements';

	interface Props extends Omit<HTMLAttributes<HTMLUListElement>, 'children' | 'class'> {
		single?: boolean;
		class?: string;
		children: Snippet;
	}

	let { single = false, class: className = '', children, ...rest }: Props = $props();
</script>

<ul
	{...rest}
	class={[
		'm-0 list-none p-0',
		single
			? 'grid grid-cols-[minmax(0,420px)] justify-center'
			: 'grid grid-cols-[repeat(auto-fill,minmax(min(280px,100%),1fr))] gap-9',
		className
	]}
>
	{@render children()}
</ul>

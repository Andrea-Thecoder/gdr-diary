<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		onclick: () => void;
		disabled?: boolean;
		class?: string;
		children: Snippet;
	}

	let { onclick, disabled = false, class: className = '', children }: Props = $props();
</script>

<button
	type="button"
	{onclick}
	{disabled}
	class={[
		'inline-flex min-h-[44px] cursor-pointer items-center text-soft underline underline-offset-4 hover:opacity-80 disabled:cursor-not-allowed disabled:no-underline disabled:opacity-60',
		className
	]}
>
	{@render children()}
</button>

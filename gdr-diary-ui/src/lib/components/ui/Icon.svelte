<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		size?: number;
		strokeWidth?: number;
		class?: string;
		children: Snippet;
	}

	let { size = 24, strokeWidth = 1.5, class: className = '', children }: Props = $props();
</script>

<svg
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill="none"
	stroke="currentColor"
	stroke-width={strokeWidth}
	stroke-linecap="round"
	stroke-linejoin="round"
	aria-hidden="true"
	class={className}
>
	{@render children()}
</svg>

<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';
	import { buttonClasses, type ButtonSize, type ButtonVariant } from './button.js';

	interface Props extends Omit<HTMLButtonAttributes, 'children' | 'class'> {
		variant?: ButtonVariant;
		size?: ButtonSize;
		class?: string;
		children: Snippet;
	}

	let {
		variant = 'solid',
		size = 'md',
		class: className = '',
		type = 'button',
		children,
		...rest
	}: Props = $props();
</script>

<button {type} {...rest} class={[buttonClasses(variant, size), className]}>
	{@render children()}
</button>

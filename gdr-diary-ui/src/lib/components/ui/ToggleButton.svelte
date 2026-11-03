<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends Omit<
		HTMLButtonAttributes,
		'children' | 'type' | 'class' | 'aria-label' | 'aria-pressed' | 'title'
	> {
		label: string;
		pressed?: boolean | undefined;
		class?: string;
		children: Snippet;
	}

	let { label, pressed, class: className = '', children, ...rest }: Props = $props();
</script>

<button
	type="button"
	aria-label={label}
	title={label}
	aria-pressed={pressed}
	onmousedown={(event) => {
		event.preventDefault();
	}}
	{...rest}
	class={[
		'inline-flex size-11 flex-none cursor-pointer items-center justify-center rounded-[4px] border text-tool-ink hover:opacity-80 disabled:cursor-not-allowed disabled:opacity-35',
		pressed === true ? 'border-orn bg-(--tool-active)' : 'border-transparent',
		className
	]}
>
	{@render children()}
</button>

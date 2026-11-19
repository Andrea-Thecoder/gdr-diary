<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		id: string;
		tone?: 'default' | 'dark-band';
		size?: 'lg' | 'xl';
		class?: string;
		children: Snippet;
	}

	let { id, tone = 'default', size = 'lg', class: className = '', children }: Props = $props();
</script>

<h2
	{id}
	class={[
		'm-0 font-display font-bold',
		tone === 'dark-band' ? 'text-s4-ink' : 'text-ink',
		size === 'xl'
			? 'text-[clamp(40px,5.8vw,70px)] leading-[1.04]'
			: 'text-[clamp(38px,5.2vw,62px)] leading-[1.04]',
		className
	]}
>
	{@render children()}
</h2>

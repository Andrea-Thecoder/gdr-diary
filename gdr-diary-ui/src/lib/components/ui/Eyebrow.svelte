<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		class?: string;
		children: Snippet;
	}

	let { class: className = '', children }: Props = $props();
</script>

<p class={['font-label text-[13px] font-medium tracking-[.2em] uppercase', className]}>
	{@render children()}
</p>

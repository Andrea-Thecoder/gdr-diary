<script lang="ts">
	interface Props {
		layout: 'inline' | 'stacked';
		onnavigate?: () => void;
	}

	let { layout, onnavigate }: Props = $props();

	const linkClass = $derived(
		layout === 'stacked'
			? 'flex min-h-[52px] items-center border-t border-(color:--rule) text-ink no-underline'
			: 'inline-flex min-h-[44px] items-center text-ink no-underline hover:opacity-80'
	);
</script>

<a href="#come-funziona" class={linkClass} onclick={onnavigate}>Come funziona</a>
<a href="#scrittura" class={linkClass} onclick={onnavigate}>La scrittura</a>
<a href="#per-chi" class={linkClass} onclick={onnavigate}>Per chi è</a>

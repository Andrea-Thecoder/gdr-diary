<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		direction?: 'left' | 'right';
		size?: number;
	}

	let { direction = 'right', size = 16 }: Props = $props();
</script>

<Icon {size} strokeWidth={2}>
	{#if direction === 'right'}
		<path d="M5 12h14M13 6l6 6-6 6" />
	{:else}
		<path d="M19 12H5M11 6l-6 6 6 6" />
	{/if}
</Icon>

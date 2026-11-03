<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		size?: number;
		strokeWidth?: number;
		class?: string;
	}

	let { size = 32, strokeWidth = 1.5, class: className = '' }: Props = $props();
</script>

<Icon {size} {strokeWidth} class={className}>
	<path d="M20 4c-6 0-11 4-13 10l-3 6 6-3c6-2 10-7 10-13z" />
	<path d="M7 17l7-7" />
</Icon>

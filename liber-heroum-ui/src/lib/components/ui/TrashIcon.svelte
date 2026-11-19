<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		size?: number;
		strokeWidth?: number;
		class?: string;
	}

	let { size = 20, strokeWidth = 1.75, class: className = '' }: Props = $props();
</script>

<Icon {size} {strokeWidth} class={className}>
	<path d="M4 7h16" />
	<path d="M9 7V4.5h6V7" />
	<path d="M6 7l1 13h10l1-13" />
	<path d="M10 11v5M14 11v5" />
</Icon>

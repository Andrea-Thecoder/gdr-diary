<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		size?: number;
		strokeWidth?: number;
		class?: string;
	}

	let { size = 22, strokeWidth = 1.75, class: className = '' }: Props = $props();
</script>

<Icon {size} {strokeWidth} class={className}>
	<path d="M10.3 4.2 2.8 17.5A2 2 0 0 0 4.5 20.5h15a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0z" />
	<path d="M12 9.5v4.5" />
	<path d="M12 17.2h.01" />
</Icon>

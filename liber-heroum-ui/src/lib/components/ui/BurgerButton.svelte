<script lang="ts">
	import Icon from './Icon.svelte';
	import IconButton from './IconButton.svelte';

	interface Props {
		open: boolean;
		controls: string;
		onclick: () => void;
		openLabel?: string;
		closeLabel?: string;
		element?: HTMLButtonElement | null;
		class?: string;
	}

	let {
		open,
		controls,
		onclick,
		openLabel = 'Apri il menu',
		closeLabel = 'Chiudi il menu',
		element = $bindable(null),
		class: className = ''
	}: Props = $props();
</script>

<IconButton
	bind:element
	label={open ? closeLabel : openLabel}
	aria-expanded={open}
	aria-controls={open ? controls : undefined}
	{onclick}
	class={className}
>
	<Icon size={22}>
		{#if open}
			<path d="M6 6l12 12M18 6L6 18" />
		{:else}
			<path d="M4 7h16M4 12h16M4 17h16" />
		{/if}
	</Icon>
</IconButton>

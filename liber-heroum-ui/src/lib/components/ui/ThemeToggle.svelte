<script lang="ts">
	import { theme } from '#lib/theme/theme.svelte.js';
	import { nextPreference, PREFERENCE_NAMES } from '#lib/theme/theme.js';
	import Icon from './Icon.svelte';
	import IconButton from './IconButton.svelte';

	interface Props {
		showLabel?: boolean;
		class?: string;
	}

	let { showLabel = false, class: className = '' }: Props = $props();

	const label = $derived(
		`Tema: ${PREFERENCE_NAMES[theme.preference]}. Passa a ${PREFERENCE_NAMES[nextPreference(theme.preference)]}`
	);

	$effect(() => {
		theme.init();
	});
</script>

<div
	class={[showLabel ? 'flex w-full items-center justify-between gap-4' : 'inline-flex', className]}
>
	{#if showLabel}
		<span>Tema: {PREFERENCE_NAMES[theme.preference]}</span>
	{/if}
	<IconButton
		{label}
		onclick={() => {
			theme.cycle();
		}}
	>
		<Icon size={22}>
			{#if theme.preference === 'system'}
				<rect x="3" y="4" width="18" height="12" rx="1.5" />
				<path d="M8 20h8M12 16v4" />
			{:else if theme.preference === 'pergamena'}
				<circle cx="12" cy="12" r="4" />
				<path
					d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
				/>
			{:else}
				<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
			{/if}
		</Icon>
	</IconButton>
</div>

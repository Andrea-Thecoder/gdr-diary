<script lang="ts">
	import { theme } from '#lib/theme/theme.svelte.js';
	import { PREFERENCE_NAMES, type ThemePreference } from '#lib/theme/theme.js';
	import Icon from './Icon.svelte';
	import SegmentedControl from './SegmentedControl.svelte';

	const options = (['system', 'pergamena', 'viola'] as const).map((value) => ({
		value,
		label: PREFERENCE_NAMES[value]
	}));

	interface Props {
		onselect?: (preference: ThemePreference) => void;
	}

	let { onselect }: Props = $props();

	$effect(() => {
		theme.init();
	});
</script>

<SegmentedControl
	legend="Tema"
	{options}
	value={theme.preference}
	onselect={(value: ThemePreference) => {
		theme.select(value);
		onselect?.(value);
	}}
>
	{#snippet icon(value)}
		<Icon size={16} strokeWidth={1.8}>
			{#if value === 'system'}
				<rect x="3" y="4" width="18" height="12" rx="1.5" />
				<path d="M8 20h8M12 16v4" />
			{:else if value === 'pergamena'}
				<circle cx="12" cy="12" r="4" />
				<path
					d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
				/>
			{:else}
				<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
			{/if}
		</Icon>
	{/snippet}
</SegmentedControl>

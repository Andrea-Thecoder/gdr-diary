<script lang="ts">
	import Icon from '#lib/components/ui/Icon.svelte';
	import { cellLabel, type PlanCell } from '#lib/info/plans.js';

	interface Props {
		value: PlanCell;
	}

	let { value }: Props = $props();
</script>

{#if value.kind === 'yes'}
	<Icon size={22} strokeWidth={2} class="mx-auto text-orn">
		<path d="M5 12.5l4.5 4.5L19 7.5" />
	</Icon>
	<span class="sr-only">{cellLabel(value)}</span>
{:else if value.kind === 'no'}
	<span aria-hidden="true" class="text-soft">—</span>
	<span class="sr-only">{cellLabel(value)}</span>
{:else}
	<span class="text-[18px] text-ink">{value.text}</span>
{/if}

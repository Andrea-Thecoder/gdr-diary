<script lang="ts">
	import { isMaster, type CharacterKind } from '#lib/characters/kind.js';
	import Icon from '#lib/components/ui/Icon.svelte';

	interface Props {
		kind: CharacterKind;
		size?: number;
	}

	let { kind, size = 24 }: Props = $props();
</script>

<Icon {size}>
	{#if isMaster(kind)}
		<path d="M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5L3 8z" />
		<path d="M5.5 19h13" />
	{:else}
		<circle cx="12" cy="8" r="4" />
		<path d="M4 21c1-5 4.5-7 8-7s7 2 8 7" />
	{/if}
</Icon>

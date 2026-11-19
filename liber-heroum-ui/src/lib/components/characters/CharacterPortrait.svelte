<script lang="ts">
	import {
		photoBackground,
		type Silhouette as SilhouetteKind
	} from '#lib/characters/appearance.js';
	import Silhouette from './Silhouette.svelte';

	interface Props {
		label: string;
		color: string | null | undefined;
		avatarUrl: string | null | undefined;
		silhouette: SilhouetteKind;
		class?: string;
	}

	let { label, color, avatarUrl, silhouette, class: className = '' }: Props = $props();
</script>

<div
	role="img"
	aria-label={label}
	style:background={photoBackground(color)}
	class={['relative overflow-hidden', className]}
>
	{#if avatarUrl !== null && avatarUrl !== undefined && avatarUrl !== ''}
		<img src={avatarUrl} alt="" class="absolute inset-0 size-full object-cover" />
	{:else}
		<Silhouette kind={silhouette} />
	{/if}
	<div aria-hidden="true" class="pointer-events-none absolute inset-0 surface-portrait-light"></div>
</div>

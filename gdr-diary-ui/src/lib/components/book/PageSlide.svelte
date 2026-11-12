<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		forward: boolean;
		away: Snippet;
		arrive: Snippet;
		onfinish: () => void;
	}

	let { forward, away, arrive, onfinish }: Props = $props();
</script>

<div
	class="pointer-events-none absolute inset-0 z-20 overflow-hidden"
	style:--dir={forward ? '1' : '-1'}
	aria-hidden="true"
>
	<div class="absolute inset-0 page-slide-away">
		{@render away()}
	</div>
	<div
		class="absolute inset-0 page-slide-arrive"
		onanimationend={(event) => {
			if (event.target === event.currentTarget && event.animationName === 'page-slide-arrive') {
				onfinish();
			}
		}}
	>
		{@render arrive()}
	</div>
</div>

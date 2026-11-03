<script lang="ts">
	import { afterNavigate } from '$app/navigation';
	import AppHeader from '#lib/components/app/AppHeader.svelte';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import { lastPlace } from '#lib/ui/last-place.svelte.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	afterNavigate(({ to }) => {
		if (to !== null) {
			lastPlace.record(to.url);
		}
	});

	$effect(() => {
		if (preferences.value.reduceMotion) {
			document.documentElement.dataset['motion'] = 'reduced';
		} else {
			delete document.documentElement.dataset['motion'];
		}
	});
</script>

<div class="relative min-h-dvh overflow-x-clip surface-page">
	<div
		aria-hidden="true"
		class="pointer-events-none absolute inset-x-0 top-0 h-[36rem] surface-hero"
	></div>
	<div class="relative">
		<AppHeader />
		<main>
			{@render children()}
		</main>
	</div>
</div>

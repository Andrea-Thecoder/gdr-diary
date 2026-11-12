<script lang="ts">
	import { afterNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import AppFooter from '#lib/components/app/AppFooter.svelte';
	import AppHeader from '#lib/components/app/AppHeader.svelte';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import { lastPlace } from '#lib/ui/last-place.svelte.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const openDiary = $derived(page.route.id === '/(app)/diaries/[diaryId]');

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

<div class="relative flex min-h-dvh flex-col overflow-x-clip surface-page">
	<div
		aria-hidden="true"
		class="pointer-events-none absolute inset-x-0 top-0 h-[36rem] surface-hero"
	></div>
	<div class="relative flex-1">
		<AppHeader />
		<main>
			{@render children()}
		</main>
	</div>
	{#if !openDiary}
		<AppFooter />
	{/if}
</div>

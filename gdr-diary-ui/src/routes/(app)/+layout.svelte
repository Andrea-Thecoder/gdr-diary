<script lang="ts">
	import { afterNavigate, goto } from '$app/navigation';
	import { page } from '$app/state';
	import { withoutAuthCallback } from '#lib/auth/callback.js';
	import AppFooter from '#lib/components/app/AppFooter.svelte';
	import AppHeader from '#lib/components/app/AppHeader.svelte';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import { lastPlace } from '#lib/ui/last-place.svelte.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const bareRoutes: readonly (string | null)[] = [
		'/(app)/diaries/[diaryId]',
		'/(app)/characters/[characterId]/background'
	];
	const openDiary = $derived(bareRoutes.includes(page.route.id));

	afterNavigate(({ to }) => {
		if (to !== null) {
			lastPlace.record(to.url);
		}
		const clean = withoutAuthCallback(window.location.href);
		if (clean !== null) {
			// eslint-disable-next-line svelte/no-navigation-without-resolve -- è l'indirizzo in cui siamo già, senza i parametri lasciati da Keycloak
			void goto(clean, { state: page.state, shallow: true, replace: true });
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
		class="pointer-events-none absolute inset-x-0 top-0 h-[36rem] surface-hero print:hidden"
	></div>
	<div class="relative flex-1">
		<div class="print:hidden">
			<AppHeader />
		</div>
		<main>
			{@render children()}
		</main>
	</div>
	{#if !openDiary}
		<AppFooter />
	{/if}
</div>

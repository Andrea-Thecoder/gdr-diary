<script lang="ts">
	import { onMount } from 'svelte';
	import './layout.css';
	import favicon from '#lib/assets/favicon.svg';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import { errorNotice } from '#lib/ui/error-notice.svelte.js';
	import { recallError, recallNotice } from '#lib/ui/flash-error.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	onMount(() => {
		const message = recallError();
		if (message !== null) {
			errorNotice.show(message, true);
		}
		const notice = recallNotice();
		if (notice !== null) {
			errorNotice.show(notice);
		}
	});
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

{@render children()}

<NoticePopup notice={errorNotice} placement="corner" />

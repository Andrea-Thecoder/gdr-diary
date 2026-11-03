<script lang="ts">
	import { userInitial } from '#lib/characters/appearance.js';
	import { profileAvatar } from '#lib/profile/avatar.svelte.js';

	interface Props {
		label: string;
		url: string | null;
		class?: string;
	}

	let { label, url, class: className = '' }: Props = $props();

	function renewExpiredUrl(): void {
		if (url === profileAvatar.url) {
			void profileAvatar.refresh();
		}
	}
</script>

<span
	aria-hidden="true"
	class={[
		'flex flex-none items-center justify-center overflow-hidden rounded-full border text-nav-ink',
		className
	]}
	style:background="var(--chip-bg)"
>
	{#if url === null}
		{userInitial(label)}
	{:else}
		<img src={url} alt="" onerror={renewExpiredUrl} class="size-full object-cover" />
	{/if}
</span>

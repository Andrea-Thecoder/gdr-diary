<script lang="ts">
	import type { Notice } from '#lib/ui/notice.svelte.js';
	import Popup from './Popup.svelte';

	interface Props {
		notice: Notice;
		placement?: 'center' | 'corner';
	}

	let { notice, placement = 'center' }: Props = $props();
</script>

{#if notice.message !== null}
	{#key notice.message}
		<Popup
			tone={notice.failed ? 'alert' : 'status'}
			{placement}
			onclose={() => {
				notice.clear();
			}}
		>
			{notice.message}
		</Popup>
	{/key}
{/if}

<script lang="ts">
	import type { PrivacyContact } from '#lib/env.js';

	interface Props {
		contact: PrivacyContact | undefined;
	}

	let { contact }: Props = $props();
</script>

{#if contact !== undefined}
	<p class="m-0">
		<strong class="font-medium">{contact.name}</strong> ·
		<a href={`mailto:${contact.email}`} class="text-ink underline underline-offset-4">
			{contact.email}
		</a>
	</p>
{:else}
	<p class="m-0 text-soft">
		I recapiti del titolare saranno indicati qui prima dell’apertura del servizio al pubblico.
	</p>
{/if}

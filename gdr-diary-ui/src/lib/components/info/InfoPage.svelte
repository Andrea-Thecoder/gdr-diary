<script lang="ts">
	import type { Snippet } from 'svelte';
	import { auth } from '#lib/auth/auth.svelte.js';
	import AppHeader from '#lib/components/app/AppHeader.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import InfoFooter from './InfoFooter.svelte';
	import InfoHeader from './InfoHeader.svelte';

	interface Props {
		eyebrow: string;
		title: string;
		lead: string;
		extra?: Snippet;
		children: Snippet;
	}

	let { eyebrow, title, lead, extra, children }: Props = $props();

	$effect(() => {
		void auth.init();
	});
</script>

<div class="min-h-dvh overflow-x-hidden font-body text-[20px] leading-[1.55] text-ink surface-page">
	<div class="surface-hero">
		{#if auth.status === 'authenticated'}
			<AppHeader />
		{:else}
			<InfoHeader />
		{/if}
		<Container as="section" aria-labelledby="page-title" class="pb-[clamp(24px,4vw,48px)]">
			<div class="pt-[clamp(8px,2vw,24px)]">
				<Eyebrow class="text-label">{eyebrow}</Eyebrow>
				<h1
					id="page-title"
					class="m-0 mt-3 font-display text-[clamp(34px,5vw,60px)] leading-[1.04] font-bold text-ink"
				>
					{title}
				</h1>
				<p class="mx-0 mt-5 mb-0 max-w-[36em] text-[clamp(19px,2vw,22px)] text-soft">{lead}</p>
				{#if extra !== undefined}
					<div class="mt-7">
						{@render extra()}
					</div>
				{/if}
			</div>
		</Container>
	</div>
	<main>
		<Container class="pt-[clamp(24px,4vw,48px)] pb-[clamp(56px,9vw,112px)]">
			{@render children()}
		</Container>
	</main>
	<InfoFooter />
</div>

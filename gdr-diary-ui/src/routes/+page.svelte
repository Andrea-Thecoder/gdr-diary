<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import AudienceSection from '#lib/components/landing/AudienceSection.svelte';
	import FinalCta from '#lib/components/landing/FinalCta.svelte';
	import Hero from '#lib/components/landing/Hero.svelte';
	import OrnamentDivider from '#lib/components/landing/OrnamentDivider.svelte';
	import SiteFooter from '#lib/components/landing/SiteFooter.svelte';
	import SiteHeader from '#lib/components/landing/SiteHeader.svelte';
	import StepCards from '#lib/components/landing/StepCards.svelte';
	import WritingSection from '#lib/components/landing/WritingSection.svelte';

	$effect(() => {
		void auth.init();
	});

	$effect(() => {
		if (auth.status === 'authenticated') {
			void goto(resolve('/(app)/characters'), { replace: true });
		}
	});
</script>

<svelte:head>
	<title>Diario GDR · Il diario di ogni avventuriero</title>
	<meta
		name="description"
		content="Scrivi le cronache del tuo personaggio su un diario vero: una copertina, cento pagine e il piacere di sfogliarle."
	/>
</svelte:head>

<div id="top" class="overflow-x-hidden font-body text-[20px] leading-[1.55] text-ink surface-page">
	<main>
		<div class="surface-hero">
			<SiteHeader />
			<Hero />
		</div>
		<OrnamentDivider />
		<StepCards />
		<WritingSection />
		<AudienceSection />
		<FinalCta />
	</main>
	<SiteFooter />
</div>

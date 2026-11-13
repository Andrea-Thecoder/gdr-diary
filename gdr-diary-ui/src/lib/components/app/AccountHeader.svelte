<script lang="ts">
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import { backLinkClasses } from '#lib/components/ui/button.js';
	import { lastPlace } from '#lib/ui/last-place.svelte.js';

	interface Props {
		title: string;
		subtitle: string;
	}

	let { title, subtitle }: Props = $props();
</script>

<Container as="section" aria-labelledby="page-title" class="pt-[clamp(8px,2vw,24px)]">
	<!-- eslint-disable svelte/no-navigation-without-resolve -- l'indirizzo è un percorso dell'app già completo (con base e query), salvato da lastPlace -->
	<a href={lastPlace.href} class={backLinkClasses}>
		<ArrowIcon direction="left" size={18} />
		{lastPlace.label}
	</a>
	<!-- eslint-enable svelte/no-navigation-without-resolve -->
	<h1
		id="page-title"
		class="m-0 mt-[clamp(12px,2vw,20px)] font-display text-[clamp(34px,4.6vw,56px)] leading-[1.04] font-bold text-ink"
	>
		{title}
	</h1>
	<p class="mx-0 mt-5 mb-0 max-w-[30em] text-[clamp(19px,2vw,22px)] text-soft">{subtitle}</p>
</Container>

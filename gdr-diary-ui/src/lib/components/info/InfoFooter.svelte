<script lang="ts">
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { loginToApp } from '#lib/auth/login.js';
	import BrandLink from '#lib/components/ui/BrandLink.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import LinkButton from '#lib/components/ui/LinkButton.svelte';
	import FooterColumn from './FooterColumn.svelte';

	const linkClass =
		'inline-flex min-h-[44px] items-center text-soft underline underline-offset-4 hover:opacity-80';

	const disabled = $derived(auth.status === 'loading' || auth.status === 'error');
</script>

<Container
	as="footer"
	class="border-t border-(color:--rule) pt-10 pb-[max(40px,env(safe-area-inset-bottom))] text-soft"
>
	<div class="flex flex-wrap justify-between gap-x-12 gap-y-8">
		<div class="max-w-[22rem]">
			<BrandLink />
			<p class="mx-0 mt-2 mb-0 text-[17px]">
				Un diario per ogni eroe, un posto per le sue cronache.
			</p>
		</div>
		<div class="flex flex-wrap gap-x-14 gap-y-8">
			<FooterColumn title="Prodotto">
				<a href={resolve('/piani')} class={linkClass}>Piani</a>
				<a href={resolve('/faq')} class={linkClass}>Domande frequenti</a>
			</FooterColumn>
			<FooterColumn title="Aiuto">
				<a href={resolve('/feedback')} class={linkClass}>Feedback</a>
				<a href={resolve('/contatti')} class={linkClass}>Contatti</a>
			</FooterColumn>
			<FooterColumn title="Legale">
				<a href={resolve('/privacy')} class={linkClass}>Privacy</a>
				{#if auth.status === 'authenticated'}
					<a href={resolve('/(app)/profile')} class={linkClass}>Il mio profilo</a>
				{:else}
					<LinkButton onclick={loginToApp} {disabled}>Accedi</LinkButton>
				{/if}
			</FooterColumn>
		</div>
	</div>
	<p class="mx-0 mt-8 mb-0 text-[16px]">
		{new Date().getFullYear()} Diario GDR. Fatto per chi gioca di ruolo.
	</p>
</Container>

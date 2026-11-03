<script lang="ts">
	import { auth } from '#lib/auth/auth.svelte.js';
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import BookSpread from './BookSpread.svelte';
	import AuthButton from './AuthButton.svelte';
</script>

<Container
	as="section"
	aria-labelledby="hero-title"
	class="flex flex-wrap items-center gap-x-12 gap-y-16 pt-[clamp(24px,5vw,64px)] pb-[clamp(64px,9vw,120px)]"
>
	<div class="min-w-0 flex-[1_1_420px]">
		<Eyebrow class="mb-[22px] text-label">Il diario di ogni avventuriero</Eyebrow>
		<h1
			id="hero-title"
			class="m-0 font-display text-[clamp(48px,7.4vw,92px)] leading-[1.04] font-bold text-ink"
		>
			Ogni eroe merita il suo <em class="surface-em text-em-ink not-italic">diario</em>.
		</h1>
		<p class="mx-0 mt-7 mb-0 max-w-[29em] text-[clamp(19px,2vw,22px)] text-soft">
			Scrivi le cronache dei tuoi personaggi su un diario vero e portale sempre con te. Il piacere
			di sfogliarle, sessione dopo sessione.
		</p>
		<div class="mt-[38px] flex flex-wrap items-center gap-x-7 gap-y-3.5">
			<AuthButton action="register">Crea il tuo diario</AuthButton>
			<AuthButton variant="outline">Accedi</AuthButton>
			<a
				href="#come-funziona"
				class="inline-flex min-h-[44px] items-center text-link underline underline-offset-6 hover:opacity-80"
			>
				Scopri come funziona
			</a>
		</div>
		{#if auth.status !== 'authenticated'}
			<p class="mx-0 mt-[22px] mb-0 text-base text-soft">
				Registrarsi richiede un minuto ed è gratuito.
			</p>
		{/if}
		{#if auth.status === 'error'}
			<p role="alert" class="mx-0 mt-4 mb-0 text-base text-label">
				Il servizio di accesso non risponde in questo momento. Riprova più tardi.
			</p>
		{/if}
	</div>

	<BookSpread />
</Container>

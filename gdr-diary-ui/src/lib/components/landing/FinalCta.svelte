<script lang="ts">
	import Container from '#lib/components/ui/Container.svelte';
	import QuillIcon from '#lib/components/ui/QuillIcon.svelte';
	import SectionHeading from '#lib/components/ui/SectionHeading.svelte';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { loginToApp } from '#lib/auth/login.js';
	import LinkButton from '#lib/components/ui/LinkButton.svelte';
	import AuthButton from './AuthButton.svelte';
</script>

<section id="accedi" aria-labelledby="cta-title" class="surface-cta">
	<Container class="flex flex-col items-center py-[clamp(64px,10vw,128px)] text-center">
		<div
			aria-hidden="true"
			class="flex size-[100px] items-center justify-center rounded-full shadow-(--seal-shadow) surface-seal"
		>
			<QuillIcon size={44} class="text-seal-ink" />
		</div>
		<SectionHeading id="cta-title" size="xl" class="mt-[34px] max-w-[14em]">
			Il tuo eroe ha già una storia da scrivere.
		</SectionHeading>
		<p class="mx-0 mt-5 mb-0 max-w-[30em] text-[21px] text-soft">
			Crea un account, poi apri il primo diario e scrivi la prima riga.
		</p>
		<AuthButton action="register" size="lg" class="mt-[38px]"
			>Registrati e inizia a scrivere</AuthButton
		>
		{#if auth.status !== 'authenticated'}
			<p
				class="mx-0 mt-3 mb-0 flex flex-wrap items-center justify-center gap-x-2 text-base text-soft"
			>
				Hai già un account?
				<LinkButton onclick={loginToApp}>Accedi</LinkButton>
			</p>
		{/if}
	</Container>
</section>

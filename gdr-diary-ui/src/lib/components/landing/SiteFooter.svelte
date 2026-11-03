<script lang="ts">
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { loginToApp } from '#lib/auth/login.js';
	import { registerToApp } from '#lib/auth/register.js';
	import Container from '#lib/components/ui/Container.svelte';
	import LinkButton from '#lib/components/ui/LinkButton.svelte';

	const disabled = $derived(auth.status === 'loading' || auth.status === 'error');
</script>

<Container
	as="footer"
	class="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-t border-(color:--rule) pt-7 pb-[max(40px,env(safe-area-inset-bottom))] text-base text-soft"
>
	<div class="font-display text-[26px] font-bold text-ink">Diario GDR</div>
	<div class="flex flex-wrap gap-x-6 gap-y-1">
		<a
			href={resolve('/privacy')}
			class="inline-flex min-h-[44px] items-center text-soft underline underline-offset-4 hover:opacity-80"
		>
			Privacy
		</a>
		{#if auth.status !== 'authenticated'}
			<LinkButton onclick={loginToApp} {disabled}>Accedi</LinkButton>
			<LinkButton onclick={registerToApp} {disabled}>Registrati</LinkButton>
		{/if}
	</div>
</Container>

<script lang="ts">
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { translateMessage } from '#lib/api/messages.js';
	import { auth } from '#lib/auth/auth.svelte.js';
	import BrandLink from '#lib/components/ui/BrandLink.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import OrnamentRule from '#lib/components/ui/OrnamentRule.svelte';

	const status = $derived(page.status);
	const title = $derived(
		status === 403
			? 'Accesso non consentito'
			: status === 404
				? 'Pagina non trovata'
				: status >= 500
					? 'Qualcosa non risponde'
					: 'Qualcosa è andato storto'
	);
	const reason = $derived(translateMessage(page.error?.message ?? 'Qualcosa è andato storto.'));
	const retry = $derived(status >= 500);
	const signedIn = $derived(auth.status === 'authenticated');
	const actionClass = 'w-full min-[480px]:w-auto';

	function leave(): void {
		void auth.logout();
	}

	function reload(): void {
		window.location.reload();
	}
</script>

<svelte:head>
	<title>{title} · Diario GDR</title>
</svelte:head>

<div class="flex min-h-dvh flex-col font-body text-ink surface-hero">
	<header
		class="mx-auto flex w-full max-w-[1200px] items-center py-[22px] pr-[max(clamp(16px,4vw,48px),env(safe-area-inset-right))] pl-[max(clamp(16px,4vw,48px),env(safe-area-inset-left))]"
	>
		<BrandLink />
	</header>

	<main
		class="mx-auto flex w-full max-w-[560px] flex-1 flex-col justify-center pr-[max(clamp(16px,4vw,48px),env(safe-area-inset-right))] pb-[max(64px,env(safe-area-inset-bottom))] pl-[max(clamp(16px,4vw,48px),env(safe-area-inset-left))]"
	>
		<div
			class="min-w-0 rounded-[6px] border border-(color:--card-border) px-[clamp(20px,6vw,40px)] pt-9 pb-10 text-center shadow-(--card-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
		>
			<div
				aria-hidden="true"
				class="mx-auto flex size-[72px] items-center justify-center rounded-full text-seal-ink shadow-(--seal-shadow) surface-seal"
			>
				<Icon size={32}>
					<path d="M12 8v5M12 16.5v.01" />
					<circle cx="12" cy="12" r="9" />
				</Icon>
			</div>

			<Eyebrow class="mt-6 text-label">Errore {status}</Eyebrow>
			<h1
				class="mx-0 mt-2 mb-0 font-display text-[clamp(30px,8vw,38px)] leading-[1.08] font-bold text-h3"
			>
				{title}
			</h1>

			<OrnamentRule class="my-6" />

			<p class="m-0 text-[19px] text-soft">
				{reason}
			</p>

			<div
				class="mt-8 flex flex-col items-stretch gap-3 min-[480px]:flex-row min-[480px]:flex-wrap min-[480px]:justify-center"
			>
				{#if retry}
					<Button size="md" class={actionClass} onclick={reload}>Riprova</Button>
				{/if}
				{#if signedIn}
					<Button variant="outline" size="md" class={actionClass} onclick={leave}>Esci</Button>
				{/if}
				<a
					href={resolve('/')}
					class={[buttonClasses('outline', 'md'), 'no-underline', actionClass]}
				>
					Torna alla home
				</a>
			</div>
		</div>
	</main>
</div>

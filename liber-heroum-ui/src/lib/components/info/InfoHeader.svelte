<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import AuthButton from '#lib/components/landing/AuthButton.svelte';
	import BrandLink from '#lib/components/ui/BrandLink.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import ThemeToggle from '#lib/components/ui/ThemeToggle.svelte';

	const linkClass =
		'inline-flex min-h-[44px] items-center border-b-2 border-transparent text-ink no-underline hover:opacity-80 aria-[current=page]:border-orn';

	const here = $derived(page.url.pathname);
</script>

<Container
	as="header"
	class="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-[22px]"
>
	<BrandLink />
	<nav
		aria-label="Principale"
		class="flex flex-wrap items-center gap-x-4 gap-y-1 font-label text-[13px] font-medium tracking-[.14em] uppercase min-[561px]:gap-x-[26px]"
	>
		<a
			href={resolve('/piani')}
			aria-current={here === resolve('/piani') ? 'page' : undefined}
			class={linkClass}
		>
			Piani
		</a>
		<a
			href={resolve('/faq')}
			aria-current={here === resolve('/faq') ? 'page' : undefined}
			class={linkClass}
		>
			FAQ
		</a>
		<a
			href={resolve('/contatti')}
			aria-current={here === resolve('/contatti') ? 'page' : undefined}
			class={linkClass}
		>
			Contatti
		</a>
		<AuthButton variant="outline">Accedi</AuthButton>
		<ThemeToggle />
	</nav>
</Container>

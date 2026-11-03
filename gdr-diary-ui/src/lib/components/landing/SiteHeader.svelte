<script lang="ts">
	import BurgerButton from '#lib/components/ui/BurgerButton.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import QuillIcon from '#lib/components/ui/QuillIcon.svelte';
	import ThemeToggle from '#lib/components/ui/ThemeToggle.svelte';
	import AuthButton from './AuthButton.svelte';
	import NavLinks from './NavLinks.svelte';

	const menuId = 'menu-principale';
	const navText = 'font-label text-[13px] font-medium tracking-[.14em] uppercase';

	let open = $state(false);
	let burger = $state<HTMLButtonElement | null>(null);

	function close(): void {
		open = false;
	}

	function closeOnEscape(event: KeyboardEvent): void {
		if (open && event.key === 'Escape') {
			open = false;
			burger?.focus();
		}
	}

	$effect(() => {
		const wide = window.matchMedia('(min-width: 900px)');
		const closeWhenWide = (): void => {
			if (wide.matches) {
				open = false;
			}
		};
		wide.addEventListener('change', closeWhenWide);
		return () => {
			wide.removeEventListener('change', closeWhenWide);
		};
	});
</script>

<svelte:window onkeydown={closeOnEscape} />

<Container as="header" class="flex flex-wrap items-center justify-between gap-x-4 py-[22px]">
	<a href="#top" class="inline-flex min-h-[44px] items-center gap-2.5 text-ink no-underline">
		<QuillIcon size={30} class="text-orn" />
		<span class="font-display text-[clamp(24px,7vw,32px)] leading-none font-bold">Diario GDR</span>
	</a>
	<div class="ml-auto flex items-center gap-x-2 min-[900px]:gap-x-[26px]">
		<nav
			aria-label="Principale"
			class={['hidden items-center gap-x-[26px] min-[900px]:flex', navText]}
		>
			<NavLinks layout="inline" />
			<div class="flex items-center gap-3">
				<AuthButton variant="outline">Accedi</AuthButton>
				<AuthButton action="register" size="sm">Registrati</AuthButton>
			</div>
			<ThemeToggle />
		</nav>
		<BurgerButton
			bind:element={burger}
			{open}
			controls={menuId}
			onclick={() => {
				open = !open;
			}}
			class="min-[900px]:hidden"
		/>
	</div>
	{#if open}
		<nav
			id={menuId}
			aria-label="Principale"
			class={['mt-2 flex basis-full flex-col min-[900px]:hidden', navText]}
		>
			<NavLinks layout="stacked" onnavigate={close} />
			<div class="min-h-[64px] border-t border-(color:--rule) py-2.5">
				<ThemeToggle showLabel class="min-h-[44px]" />
			</div>
			<div class="flex flex-col gap-3 border-t border-(color:--rule) py-4">
				<AuthButton variant="outline" class="w-full">Accedi</AuthButton>
				<AuthButton action="register" size="sm" class="w-full">Registrati</AuthButton>
			</div>
		</nav>
	{/if}
</Container>

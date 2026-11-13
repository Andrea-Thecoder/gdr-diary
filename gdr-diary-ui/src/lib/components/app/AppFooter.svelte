<script lang="ts">
	import { resolve } from '$app/paths';
	import Container from '#lib/components/ui/Container.svelte';

	const linkClass =
		'inline-flex min-h-[44px] items-center text-soft underline underline-offset-4 hover:opacity-80';
</script>

<Container
	as="footer"
	class="mt-[clamp(40px,6vw,72px)] flex flex-wrap items-center justify-between gap-x-6 gap-y-1 border-t border-(color:--rule) pt-3 pb-[max(24px,env(safe-area-inset-bottom))] text-[17px]"
>
	<span class="font-display text-[22px] font-bold text-ink">Diario GDR</span>
	<nav
		aria-label="Pagine informative"
		class="grid grid-cols-3 gap-x-5 min-[561px]:flex min-[561px]:flex-wrap min-[561px]:gap-x-6"
	>
		<a href={resolve('/piani')} class={linkClass}>Piani</a>
		<a href={resolve('/faq')} class={linkClass}>FAQ</a>
		<a href={resolve('/feedback')} class={linkClass}>Feedback</a>
		<a href={resolve('/contatti')} class={linkClass}>Contatti</a>
		<a href={resolve('/privacy')} class={linkClass}>Privacy</a>
	</nav>
</Container>

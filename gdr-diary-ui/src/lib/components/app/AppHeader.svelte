<script lang="ts">
	import { resolve } from '$app/paths';
	import Container from '#lib/components/ui/Container.svelte';
	import QuillIcon from '#lib/components/ui/QuillIcon.svelte';
	import AccountMenu from './AccountMenu.svelte';
</script>

<Container as="header" class="flex items-center justify-between gap-x-4 py-[22px]">
	<a
		href={resolve('/(app)/characters')}
		class="inline-flex min-h-[44px] items-center gap-2.5 text-ink no-underline"
	>
		<QuillIcon size={30} class="text-orn" />
		<span class="font-display text-[clamp(24px,7vw,32px)] leading-none font-bold">Diario GDR</span>
	</a>
	<AccountMenu />
</Container>

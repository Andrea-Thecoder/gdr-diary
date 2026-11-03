<script lang="ts">
	import { profileAvatar } from '#lib/profile/avatar.svelte.js';
	import Button from '#lib/components/ui/Button.svelte';
	import CardEditButton from '#lib/components/ui/CardEditButton.svelte';
	import UserAvatar from './UserAvatar.svelte';

	interface Props {
		name: string;
		premium: boolean;
		onedit: () => void;
		onlogout: () => void;
	}

	let { name, premium, onedit, onlogout }: Props = $props();
</script>

<section
	aria-label="Riepilogo dell’account"
	class="flex flex-col items-center gap-4 rounded-[8px] border border-(color:--card-border) p-[clamp(20px,3vw,32px)] text-center outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card min-[641px]:max-[899px]:grid min-[641px]:max-[899px]:grid-cols-[104px_1fr_auto] min-[641px]:max-[899px]:gap-x-9 min-[641px]:max-[899px]:text-left"
>
	<div class="relative justify-self-center">
		<UserAvatar
			label={name}
			url={profileAvatar.url}
			class="size-[104px] border-2 border-(color:--nav-border) font-display text-[48px] leading-none font-bold"
		/>
		<CardEditButton
			label="Cambia l’immagine del profilo"
			onclick={onedit}
			class="right-[-14px] bottom-0"
		/>
	</div>
	<div class="flex min-w-0 flex-col items-center gap-4 min-[641px]:max-[899px]:items-start">
		<p class="m-0 max-w-full text-[21px] [overflow-wrap:anywhere] text-ink">{name}</p>
		<span
			class="rounded-full border border-(color:--nav-border) px-4 py-1 font-label text-[12px] font-semibold tracking-[.14em] text-label uppercase"
		>
			{premium ? 'Piano premium' : 'Piano gratuito'}
		</span>
	</div>
	<Button
		variant="outline"
		size="block"
		class="min-[641px]:max-[899px]:w-auto min-[641px]:max-[899px]:px-8"
		onclick={onlogout}>Esci</Button
	>
</section>

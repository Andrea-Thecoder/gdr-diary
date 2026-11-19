<script lang="ts">
	import type { Snippet } from 'svelte';
	import { glowColor, photoBackground } from '#lib/characters/appearance.js';
	import PortraitCard from '#lib/components/ui/PortraitCard.svelte';

	interface Props {
		title: string;
		text: string;
		color: string;
		icon: Snippet;
	}

	let { title, text, color, icon }: Props = $props();
</script>

<PortraitCard {title} glow={glowColor(color)} lift={false}>
	{#snippet photo()}
		<div
			role="img"
			aria-label={`${title}: in arrivo`}
			class="relative flex h-[220px] items-center justify-center overflow-hidden rounded-t-[5px] text-[#F3D98B]"
			style:background={photoBackground(color)}
		>
			<div
				aria-hidden="true"
				class="absolute inset-[18px] rounded-[4px] border-2 border-dashed border-[rgba(226,190,106,.45)]"
			></div>
			{@render icon()}
			<span
				aria-hidden="true"
				class="absolute top-[30px] left-[30px] inline-flex min-h-[30px] items-center rounded-full border border-[rgba(226,190,106,.65)] bg-[rgba(14,8,32,.62)] px-3.5 font-label text-[12px] font-semibold tracking-[.14em] text-[#F3D98B] uppercase"
			>
				Presto
			</span>
		</div>
	{/snippet}
	{#snippet subtitle()}
		<p class="mx-0 mt-2.5 mb-0 text-[18px] text-soft">{text}</p>
	{/snippet}
	{#snippet action()}
		<button
			type="button"
			disabled
			class="flex min-h-[50px] w-full cursor-not-allowed items-center justify-center rounded-[4px] border-2 border-dashed border-(--ghost-border) px-5 font-label text-[14px] font-semibold tracking-[.12em] text-soft uppercase"
		>
			In arrivo
		</button>
	{/snippet}
</PortraitCard>

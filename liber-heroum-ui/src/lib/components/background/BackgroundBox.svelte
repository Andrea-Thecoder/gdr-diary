<script lang="ts">
	import { resolve } from '$app/paths';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';

	interface Props {
		characterId: string;
		name: string;
		preview?: string | null | undefined;
		class?: string;
	}

	let { characterId, name, preview = null, class: className = '' }: Props = $props();

	const text = $derived(preview?.trim() ?? '');
	const href = $derived(resolve('/(app)/characters/[characterId]/background', { characterId }));
</script>

{#if text !== ''}
	<section
		data-background-box
		aria-label="La storia del personaggio"
		class={[
			'flex max-w-[44em] flex-wrap items-center justify-between gap-x-6 gap-y-4 rounded-[8px] border border-(color:--card-border) px-5 py-4 surface-card',
			className
		]}
	>
		<div class="min-w-0 flex-[1_1_280px]">
			<Eyebrow class="mb-2 text-label">La storia</Eyebrow>
			<p
				class="m-0 line-clamp-3 text-[18px] leading-[1.5] [overflow-wrap:anywhere] text-ink italic"
			>
				{text}
			</p>
		</div>
		<a {href} class={[buttonClasses('outline', 'sm'), 'no-underline']}>Leggi tutto</a>
	</section>
{:else}
	<a
		{href}
		data-background-invite
		class={[
			'flex min-h-[44px] max-w-[44em] items-center justify-between gap-x-6 rounded-[8px] border-2 border-dashed border-(--ghost-border) bg-(--ghost-bg) px-5 py-4 text-[18px] text-ink no-underline hover:opacity-85',
			className
		]}
	>
		<span class="min-w-0 [overflow-wrap:anywhere]">Scrivi la storia di {name}</span>
		<span aria-hidden="true" class="text-orn">→</span>
	</a>
{/if}

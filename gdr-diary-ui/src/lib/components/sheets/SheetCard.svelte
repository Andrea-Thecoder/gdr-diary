<script lang="ts">
	import { glowColor, photoBackground } from '#lib/characters/appearance.js';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import CardActions from '#lib/components/ui/CardActions.svelte';
	import CardEditButton from '#lib/components/ui/CardEditButton.svelte';
	import { cardLinkClasses } from '#lib/components/ui/button.js';
	import Icon from '#lib/components/ui/Icon.svelte';
	import PortraitCard from '#lib/components/ui/PortraitCard.svelte';
	import type { SheetSummary } from '#lib/sheets/api.js';
	import { sheetColorFor } from '#lib/sheets/appearance.js';
	import { formatDate } from '#lib/sheets/format.js';
	import SheetPile from './SheetPile.svelte';

	interface Props {
		sheet: SheetSummary;
		onedit: (sheet: SheetSummary) => void;
		ondelete: (sheet: SheetSummary) => void;
	}

	let { sheet, onedit, ondelete }: Props = $props();

	const title = $derived(sheet.title ?? '');
	const system = $derived(sheet.system?.trim() ?? '');
	const color = $derived(sheetColorFor(sheet.id ?? ''));
	const date = $derived(formatDate(sheet.createdAt));
</script>

<PortraitCard {title} glow={glowColor(color)} clampTitle>
	{#snippet photo()}
		<div
			class="relative grid h-[300px] place-items-center overflow-hidden rounded-t-[5px]"
			style:background={photoBackground(color)}
		>
			<SheetPile {title} {color} />
			<div
				aria-hidden="true"
				class="pointer-events-none absolute inset-0 surface-portrait-light"
			></div>
		</div>
		<CardEditButton
			label={`Modifica ${title}`}
			onclick={() => {
				onedit(sheet);
			}}
		/>
	{/snippet}
	{#snippet subtitle()}
		<div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-[17px] text-soft">
			{#if system !== ''}
				<span
					class="inline-flex min-h-[28px] items-center rounded-full border border-(color:--rule) bg-(--tool-bg) px-3 font-label text-[12px] font-semibold tracking-[.12em] text-ink uppercase"
				>
					{system}
				</span>
			{/if}
			<span>PDF</span>
		</div>
		{#if date !== ''}
			<p class="mx-0 mt-2 mb-0 text-base text-soft">Caricata il {date}</p>
		{/if}
	{/snippet}
	{#snippet action()}
		<!-- eslint-disable svelte/no-navigation-without-resolve -- gli URL del PDF sono assoluti (storage), non rotte dell'app -->
		<CardActions
			deleteLabel={`Elimina ${title}`}
			ondelete={() => {
				ondelete(sheet);
			}}
		>
			<a
				href={sheet.url}
				target="_blank"
				rel="noopener noreferrer"
				class={[cardLinkClasses, 'min-w-0 flex-1']}
			>
				<span>Apri<span class="sr-only"> la scheda</span></span>
				<ArrowIcon />
			</a>
			<a
				href={sheet.downloadUrl}
				download
				aria-label={`Scarica ${title}`}
				title={`Scarica ${title}`}
				class="relative z-10 flex size-[50px] flex-none items-center justify-center rounded-[4px] border border-(color:--nav-border) text-nav-ink hover:opacity-85"
			>
				<Icon size={20} strokeWidth={1.8}>
					<path d="M12 4v11M7 11l5 5 5-5M5 20h14" />
				</Icon>
			</a>
		</CardActions>
		<!-- eslint-enable svelte/no-navigation-without-resolve -->
	{/snippet}
</PortraitCard>

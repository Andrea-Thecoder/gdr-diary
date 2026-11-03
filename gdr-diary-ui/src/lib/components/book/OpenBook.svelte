<script lang="ts">
	import type { DiaryBook } from '#lib/book/book.svelte.js';
	import {
		PAGE_HEIGHT,
		PAGE_WIDTH,
		bookScale,
		fontPixels,
		lineHeightFor
	} from '#lib/book/layout.js';
	import { thickness } from '#lib/book/spread.js';
	import { coverTints } from '#lib/diaries/appearance.js';
	import type { LineSpacing } from '#lib/settings/preferences.js';
	import ScaledPage from './ScaledPage.svelte';
	import DogEar from './DogEar.svelte';
	import PageTrash from './PageTrash.svelte';
	import PageTurn from './PageTurn.svelte';

	interface Props {
		book: DiaryBook;
		font: string;
		fontSize: number;
		lineSpacing: LineSpacing;
		coverColor: string | null | undefined;
		ondeletepage: (page: number) => void;
		class?: string;
	}

	let {
		book,
		font,
		fontSize,
		lineSpacing,
		coverColor,
		ondeletepage,
		class: className = ''
	}: Props = $props();

	const COVER_PADDING = 18;
	const STAGE_PADDING = 12;

	let stageWidth = $state(0);

	const typography = $derived({
		font,
		size: fontPixels(font, fontSize),
		lineHeight: lineHeightFor(lineSpacing)
	});
	const scale = $derived(
		bookScale(stageWidth - 2 * (COVER_PADDING + STAGE_PADDING) - 2, book.single)
	);
	const tints = $derived(coverTints(coverColor));
	const frameWidth = $derived(PAGE_WIDTH * scale);
	const frameHeight = $derived(PAGE_HEIGHT * scale);
	const first = $derived(book.visible[0] ?? 1);
	const lastVisible = $derived(book.visible[book.visible.length - 1] ?? 1);
	const edges = $derived(thickness(first, book.last));
	const ribbon = $derived(!book.single && book.last > 0 && lastVisible < book.last);

	function stack(count: number, direction: 'left' | 'right'): string {
		const sign = direction === 'left' ? -1 : 1;
		return Array.from({ length: count }, (_, index) => {
			const step = (index + 1) * 2;
			return `${String(sign * step)}px ${String(step / 2)}px 0 0 var(--ear-under)`;
		}).join(', ');
	}

	function hintFor(page: number): string {
		return book.last === 0 && page === 1 ? 'Comincia a scrivere la tua storia...' : '';
	}
</script>

<div
	bind:clientWidth={stageWidth}
	class={['rounded-[10px] border border-(--stage-border) p-3 surface-stage', className]}
>
	<div
		class="relative mx-auto w-fit rounded-[8px] shadow-(--book-shadow)"
		style:padding={`${String(COVER_PADDING)}px`}
		style:background={`linear-gradient(135deg, ${tints.light}, ${tints.dark})`}
	>
		<div
			aria-hidden="true"
			class="pointer-events-none absolute rounded-[4px] border border-gold-frame"
			style:inset="6px"
		></div>
		<div class="relative flex" style:height={`${String(frameHeight)}px`}>
			{#if book.turning === null}
				{#key book.generation}
					{#each book.visible as page, index (page)}
						{@const side = book.single || index === 1 ? 'right' : 'left'}
						<div
							class="relative"
							style:width={`${String(frameWidth)}px`}
							style:height={`${String(frameHeight)}px`}
							style:box-shadow={book.single
								? `${stack(edges.before, 'left')}, ${stack(edges.after, 'right')}`
								: index === 0
									? stack(edges.before, 'left')
									: stack(edges.after, 'right')}
						>
							<ScaledPage
								{book}
								slot={book.slot(page)}
								{side}
								{typography}
								{scale}
								live
								hint={hintFor(page)}
							/>
							{#if !book.single && book.deletable(page)}
								<PageTrash
									side={side === 'left' ? 'right' : 'left'}
									label={`Elimina la pagina ${String(page)}`}
									onclick={() => {
										ondeletepage(page);
									}}
								/>
							{/if}
							{#if ribbon && index === 1}
								<span
									aria-hidden="true"
									class="absolute -top-2 right-6 z-10 h-16 w-5 [clip-path:polygon(0_0,100%_0,100%_100%,50%_78%,0_100%)] surface-ribbon"
								></span>
							{/if}
							{#if book.previous && (index === 0 || book.single)}
								<DogEar
									side="left"
									label="Pagine precedenti"
									onclick={() => {
										void book.turn('backward');
									}}
								/>
							{/if}
							{#if book.next && index === book.visible.length - 1}
								<DogEar
									side="right"
									label="Pagine successive"
									onclick={() => {
										void book.turn('forward');
									}}
								/>
							{/if}
						</div>
					{/each}
				{/key}
			{:else}
				{@const turning = book.turning}
				{#if turning.single}
					{@const old = turning.from[0]}
					{@const fresh = turning.to[0]}
					{#if old !== undefined && fresh !== undefined}
						{@const forward = turning.direction === 'forward'}
						<div
							class="relative"
							style:width={`${String(frameWidth)}px`}
							style:height={`${String(frameHeight)}px`}
						>
							<ScaledPage
								{book}
								slot={forward ? fresh : old}
								side="right"
								{typography}
								{scale}
								live={false}
							/>
							<PageTurn
								from={forward ? 0 : -180}
								to={forward ? -180 : 0}
								spread={false}
								onfinish={() => {
									book.completeTurn();
								}}
							>
								{#snippet front()}
									<div class="absolute inset-0">
										<ScaledPage
											{book}
											slot={forward ? old : fresh}
											side="right"
											{typography}
											{scale}
											live={false}
										/>
									</div>
								{/snippet}
								{#snippet back()}
									<div class="absolute inset-0 bg-(--page)"></div>
								{/snippet}
							</PageTurn>
						</div>
					{/if}
				{:else}
					{@const oldLeft = turning.from[0]}
					{@const oldRight = turning.from[1]}
					{@const newLeft = turning.to[0]}
					{@const newRight = turning.to[1]}
					{#if oldLeft !== undefined && oldRight !== undefined && newLeft !== undefined && newRight !== undefined}
						{@const forward = turning.direction === 'forward'}
						<div
							class="relative"
							style:width={`${String(frameWidth)}px`}
							style:height={`${String(frameHeight)}px`}
						>
							<ScaledPage
								{book}
								slot={forward ? oldLeft : newLeft}
								side="left"
								{typography}
								{scale}
								live={false}
							/>
						</div>
						<div
							class="relative"
							style:width={`${String(frameWidth)}px`}
							style:height={`${String(frameHeight)}px`}
						>
							<ScaledPage
								{book}
								slot={forward ? newRight : oldRight}
								side="right"
								{typography}
								{scale}
								live={false}
							/>
							<PageTurn
								from={forward ? 0 : -180}
								to={forward ? -180 : 0}
								onfinish={() => {
									book.completeTurn();
								}}
							>
								{#snippet front()}
									<div class="absolute inset-0">
										<ScaledPage
											{book}
											slot={forward ? oldRight : newRight}
											side="right"
											{typography}
											{scale}
											live={false}
										/>
									</div>
								{/snippet}
								{#snippet back()}
									<div class="absolute inset-0">
										<ScaledPage
											{book}
											slot={forward ? newLeft : oldLeft}
											side="left"
											{typography}
											{scale}
											live={false}
										/>
									</div>
								{/snippet}
							</PageTurn>
						</div>
					{/if}
				{/if}
			{/if}
		</div>
	</div>
</div>

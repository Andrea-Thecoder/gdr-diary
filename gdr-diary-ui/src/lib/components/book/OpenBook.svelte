<script lang="ts">
	import type { DiaryBook } from '#lib/book/book.svelte.js';
	import {
		PAGE_HEIGHT,
		PAGE_WIDTH,
		bookScale,
		fontPixels,
		lineHeightFor,
		ribbonOffset
	} from '#lib/book/layout.js';
	import { thickness } from '#lib/book/spread.js';
	import { checkTag } from '#lib/book/tags.js';
	import { coverTints } from '#lib/diaries/appearance.js';
	import type { LineSpacing } from '#lib/settings/preferences.js';
	import ScaledPage from './ScaledPage.svelte';
	import DogEar from './DogEar.svelte';
	import PageBookmark from './PageBookmark.svelte';
	import PageTagDialog from './PageTagDialog.svelte';
	import PageTagNotes from './PageTagNotes.svelte';
	import PageTrash from './PageTrash.svelte';
	import PageSlide from './PageSlide.svelte';
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
	let editing = $state<number | null>(null);

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
	const edges = $derived(thickness(first, book.last));

	function stack(count: number, direction: 'left' | 'right'): string {
		const sign = direction === 'left' ? -1 : 1;
		return Array.from({ length: count }, (_, index) => {
			const step = (index + 1) * 2;
			return `${String(sign * step)}px ${String(step / 2)}px 0 0 var(--ear-under)`;
		}).join(', ');
	}

	const editingId = $derived(editing === null ? null : book.pageId(editing));
	const editingTag = $derived(editingId === null ? null : book.tags.tagOf(editingId));
	const showsTags = $derived(book.visible.some((page) => book.markable(page)));

	function submitTag(text: string): string | null {
		if (editingId === null) {
			return null;
		}
		const problem = checkTag(text);
		if (problem !== null) {
			return problem;
		}
		book.tags.set(editingId, text);
		return null;
	}

	function removeTag(): void {
		if (editingId !== null) {
			book.tags.clear(editingId);
		}
		closeTag();
	}

	function closeTag(): void {
		editing = null;
		book.tags.clearSuggestions();
	}

	function hintFor(page: number): string {
		if (book.last === 0 && page === 1) {
			return 'Comincia a scrivere la tua storia...';
		}
		return book.editable(page) ? '' : 'Questa pagina si apre quando la precedente è piena.';
	}
</script>

<div
	bind:clientWidth={stageWidth}
	class={[
		'rounded-[10px] border border-(--stage-border) px-3 pt-3 surface-stage',
		showsTags ? 'pb-[112px]' : 'pb-3',
		className
	]}
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
							{#if book.markable(page)}
								<PageBookmark
									{side}
									{page}
									marked={book.marked(page)}
									disabled={book.marking}
									offset={ribbonOffset(scale)}
									onclick={() => {
										void book.toggleBookmark(page);
									}}
								/>
							{/if}
							{#if book.markable(page)}
								{@const id = book.pageId(page)}
								<PageTagNotes
									{page}
									tag={id === null ? null : book.tags.tagOf(id)}
									ready={id !== null}
									gone={id !== null && book.tags.goneOf(id)}
									onopen={() => {
										editing = page;
									}}
								/>
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
							<PageSlide
								{forward}
								onfinish={() => {
									book.completeTurn();
								}}
							>
								{#snippet away()}
									<div class="absolute inset-0">
										<ScaledPage {book} slot={old} side="right" {typography} {scale} live={false} />
									</div>
								{/snippet}
								{#snippet arrive()}
									<div class="absolute inset-0">
										<ScaledPage
											{book}
											slot={fresh}
											side="right"
											{typography}
											{scale}
											live={false}
										/>
									</div>
								{/snippet}
							</PageSlide>
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

<PageTagDialog
	open={editing !== null}
	page={editing ?? 0}
	tag={editingTag}
	suggestions={book.tags.suggestions}
	onsubmit={submitTag}
	onremove={removeTag}
	ontype={(text: string) => {
		book.tags.suggest(text, editingTag === null ? [] : [editingTag]);
	}}
	onclose={closeTag}
/>

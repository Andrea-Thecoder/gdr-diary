<script lang="ts">
	import type { Editor, JSONContent } from '@tiptap/core';
	import { afterNavigate, beforeNavigate, onNavigate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onDestroy, untrack } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import { downloadBackgroundPdf, type BackgroundData } from '#lib/background/api.js';
	import { CharacterBackground } from '#lib/background/background.svelte.js';
	import { ROLL_START, rollSizes, type RollSizes } from '#lib/background/roll.js';
	import { fillLabel, fillPercent, formatCount, isNearlyFull } from '#lib/background/text.js';
	import { createDraftStore, type Draft } from '#lib/book/draft.js';
	import { fontPixels, lineHeightFor } from '#lib/book/layout.js';
	import { hasText } from '#lib/book/pages.js';
	import type { CharacterDetail } from '#lib/characters/api.js';
	import { getMaxBackgroundChars } from '#lib/env.js';
	import BookMessage from '#lib/components/book/BookMessage.svelte';
	import EditorToolbar from '#lib/components/book/EditorToolbar.svelte';
	import SaveStatus from '#lib/components/book/SaveStatus.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import { backLinkClasses, buttonClasses } from '#lib/components/ui/button.js';
	import Container from '#lib/components/ui/Container.svelte';
	import ShortcutHint from '#lib/components/ui/ShortcutHint.svelte';
	import ScrollIcon from './ScrollIcon.svelte';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import { startDownload } from '#lib/sheets/download.js';
	import { errorNotice } from '#lib/ui/error-notice.svelte.js';
	import BackgroundEditor from './BackgroundEditor.svelte';
	import ScrollRoll from './ScrollRoll.svelte';

	interface Props {
		character: CharacterDetail;
		initial: BackgroundData;
		drafts?: Draft[];
	}

	let { character, initial, drafts = [] }: Props = $props();

	const maxChars = getMaxBackgroundChars();
	const calm = window.matchMedia('(prefers-reduced-motion: reduce)');

	const background = untrack(
		() =>
			new CharacterBackground(getApi(), character.id ?? '', initial, {
				autoSave: () => preferences.value.autoSave,
				saveDelay: () => preferences.value.autoSaveDelay * 1000,
				drafts: createDraftStore(),
				maxChars
			})
	);
	untrack(() => {
		background.restore(drafts);
	});

	let editing = $state(untrack(() => background.restored || !hasText(background.content)));
	let editor = $state.raw<Editor | null>(null);
	let touched = $state(0);
	let refused = $state(false);
	let exporting = $state(false);
	let cameFromApp = $state(false);
	let rolls = $state<RollSizes>({ top: ROLL_START, bottom: ROLL_START });
	let frame = 0;

	const name = $derived(character.name ?? '');
	const campaign = $derived((character.campaignName ?? '').trim());
	const charactersHref = $derived(
		resolve('/(app)/characters/[characterId]', { characterId: character.id ?? '' })
	);
	const nearlyFull = $derived(isNearlyFull(background.length, maxChars));
	const percent = $derived(fillPercent(background.length, maxChars));
	const fill = $derived(fillLabel(background.length, maxChars));
	const typography = $derived({
		font: preferences.value.font,
		size: fontPixels(preferences.value.font, preferences.value.fontSize),
		lineHeight: lineHeightFor(preferences.value.lineSpacing)
	});

	function measure(): void {
		const root = document.documentElement;
		rolls = rollSizes({
			scrolled: window.scrollY,
			height: root.scrollHeight,
			viewport: window.innerHeight,
			width: window.innerWidth,
			still: preferences.value.reduceMotion || calm.matches
		});
	}

	function schedule(): void {
		if (frame === 0) {
			frame = requestAnimationFrame(() => {
				frame = 0;
				measure();
			});
		}
	}

	$effect(() => {
		measure();
		const observer = new ResizeObserver(schedule);
		observer.observe(document.body);
		return () => {
			observer.disconnect();
			cancelAnimationFrame(frame);
			frame = 0;
		};
	});

	$effect(() => {
		if (preferences.value.reduceMotion) {
			untrack(measure);
		}
	});

	afterNavigate(({ from }) => {
		cameFromApp = from !== null;
	});

	beforeNavigate((navigation) => {
		if (navigation.type !== 'popstate' && (background.failure !== null || background.conflict)) {
			navigation.cancel();
		}
	});

	onNavigate(async () => {
		await background.flush();
	});

	onDestroy(() => {
		void background.flush();
		background.dispose();
	});

	function goBack(event: MouseEvent): void {
		if (cameFromApp) {
			event.preventDefault();
			history.back();
		}
	}

	async function finish(): Promise<void> {
		if (await background.flush()) {
			editing = false;
		}
	}

	async function downloadPdf(): Promise<void> {
		exporting = true;
		try {
			if (!(await background.flush())) {
				errorNotice.show('Salva la storia prima di scaricare il PDF.', true);
				return;
			}
			const file = await downloadBackgroundPdf(getApi(), character.id ?? '');
			const url = URL.createObjectURL(file.blob);
			startDownload(url, file.fileName);
			setTimeout(() => {
				URL.revokeObjectURL(url);
			}, 60_000);
		} catch (cause) {
			errorNotice.show(
				cause instanceof Error ? cause.message : 'Non riesco a preparare il PDF della storia.',
				true
			);
		} finally {
			exporting = false;
		}
	}

	function saveShortcut(event: KeyboardEvent): void {
		if (editing && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
			event.preventDefault();
			void background.flush();
		}
	}

	function leaving(event: BeforeUnloadEvent): void {
		if (background.unsaved) {
			event.preventDefault();
		}
	}

	function hide(): void {
		if (document.visibilityState === 'hidden') {
			void background.flush();
		}
	}
</script>

<svelte:window
	onscroll={schedule}
	onresize={schedule}
	onbeforeunload={leaving}
	onkeydown={saveShortcut}
	ononline={() => {
		background.reconnect();
	}}
/>
<svelte:document onvisibilitychange={hide} />

<Container as="section" aria-labelledby="page-title" class="pt-[clamp(8px,2vw,24px)] pb-0">
	<div class="print:hidden">
		<a href={charactersHref} class={backLinkClasses}>
			<ArrowIcon direction="left" size={18} />
			{name}
		</a>
		<div class="mt-2 flex flex-wrap items-center justify-between gap-x-8 gap-y-4 pb-6">
			<div class="flex min-w-0 flex-[1_1_360px] items-center gap-[18px]">
				<ScrollIcon />
				<div class="min-w-0">
					<h1
						id="page-title"
						class="m-0 font-display text-[clamp(32px,4.4vw,48px)] leading-[1.05] font-bold text-ink"
					>
						Il Background
					</h1>
					<p
						class="mx-0 mt-1.5 mb-0 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[18px] text-soft"
					>
						<span class="[overflow-wrap:anywhere]">{name}</span>
						{#if campaign !== ''}
							<span class="inline-flex items-center gap-1.5 [overflow-wrap:anywhere]">
								<svg
									width="16"
									height="16"
									viewBox="0 0 24 24"
									fill="none"
									stroke="currentColor"
									stroke-width="1.8"
									stroke-linecap="round"
									stroke-linejoin="round"
									aria-hidden="true"
									class="flex-none text-orn"
								>
									<path d="M6 21V4M6 5h12l-3 4 3 4H6" />
								</svg>
								{campaign}
							</span>
						{/if}
					</p>
					<p class="mx-0 mt-2 mb-0 max-w-[34em] text-[17px] text-soft max-[760px]:hidden">
						Chi era, da dove viene, che cosa spinge {name}. Un solo rotolo, senza pagine: scrivi
						quanto vuoi.
					</p>
				</div>
			</div>
			<div class="flex flex-wrap items-center gap-x-4 gap-y-2.5">
				{#if editing}
					<p
						class={[
							'm-0 font-label text-[14px] font-semibold tracking-[.14em] uppercase',
							nearlyFull ? 'text-danger' : 'text-label'
						]}
						aria-live="polite"
						data-background-counter
					>
						<span class="max-[760px]:hidden">
							{formatCount(background.length)} / {formatCount(maxChars)} caratteri
						</span>
						<span class="min-[761px]:hidden">
							{formatCount(background.length)} / {formatCount(maxChars)}
						</span>
					</p>
					<SaveStatus state={background.state} />
					<Button
						variant="outline"
						size="sm"
						aria-label="Salva"
						disabled={background.state === 'saved' ||
							background.state === 'saving' ||
							background.state === 'conflict'}
						onclick={() => {
							void background.flush();
						}}
					>
						Salva
					</Button>
					<Button
						size="sm"
						onclick={() => {
							void finish();
						}}
					>
						Fatto
					</Button>
				{:else}
					<Button
						variant="outline"
						size="sm"
						onclick={() => {
							editing = true;
						}}
					>
						Modifica
					</Button>
				{/if}
				<Button
					variant="outline"
					size="sm"
					disabled={exporting || !hasText(background.content)}
					aria-busy={exporting}
					onclick={() => {
						void downloadPdf();
					}}
				>
					Scarica PDF
				</Button>
			</div>
		</div>
	</div>
</Container>

<div class="bg-(--stage-flat) text-stage-ink print:bg-transparent">
	<div class="[--ov:7px] max-[760px]:[--ov:5px]">
		<div
			class="sticky top-0 z-20 bg-(--stage-flat) px-[clamp(8px,2.4vw,32px)] pt-3 print:hidden"
			data-background-bar
		>
			{#if editing}
				<div class="flex justify-center">
					<EditorToolbar {editor} {touched} class="max-w-full" />
				</div>
			{/if}
			<div class="mx-auto w-[min(860px,calc(100%-2*var(--ov)-16px))]">
				<div class="mt-3 flex flex-col gap-3 empty:hidden">
					{#if background.conflict}
						<BookMessage tone="error">
							<strong class="text-danger">Il Background è stato modificato anche altrove.</strong>
							Scegli quale versione tenere: l’altra non si recupera.
							{#snippet action()}
								<div class="flex flex-wrap gap-3">
									<Button
										variant="outline"
										size="sm"
										onclick={() => {
											void background.resolveConflict('theirs');
										}}
									>
										Usa la versione salvata
									</Button>
									<Button
										size="sm"
										onclick={() => {
											void background.resolveConflict('mine');
										}}
									>
										Tieni la mia versione
									</Button>
								</div>
							{/snippet}
						</BookMessage>
					{:else if background.failure !== null && background.offline}
						<BookMessage tone="info">
							<strong>Sei offline.</strong>
							Puoi continuare a scrivere: il testo resta in questo dispositivo e parte appena torna la
							rete.
						</BookMessage>
					{:else if background.failure !== null}
						<BookMessage tone="error">
							<strong class="text-danger">Il Background non è stato salvato.</strong>
							{#if background.rejected}
								{background.failure} Il testo è ancora qui sotto: non lo perdi.
							{:else}
								Il testo è ancora qui sotto: non lo perdi. Appena la connessione torna proviamo di
								nuovo da soli.
							{/if}
							{#snippet action()}
								<Button
									variant="outline"
									size="sm"
									onclick={() => {
										background.retry();
									}}
								>
									Riprova ora
								</Button>
							{/snippet}
						</BookMessage>
					{:else if background.restored}
						<BookMessage tone="info">
							Ho ritrovato il testo che stavi scrivendo e non era ancora salvato: lo trovi qui
							sotto.
						</BookMessage>
					{/if}
					{#if background.full || refused}
						<BookMessage tone="info">
							<strong>
								{#if background.full}
									Il rotolo è pieno: {formatCount(maxChars)} caratteri su {formatCount(maxChars)}.
								{:else}
									Il testo non ci sta: restano {formatCount(maxChars - background.length)} caratteri.
								{/if}
							</strong>
							Per scrivere ancora, accorcia qualcosa o sposta una parte in un diario.
							{#snippet action()}
								<a
									href={charactersHref}
									class={[buttonClasses('solid', 'sm'), 'no-underline']}
									onclick={goBack}
								>
									Apri i diari
								</a>
							{/snippet}
						</BookMessage>
					{/if}
				</div>
			</div>
			<div aria-hidden="true" class="h-3"></div>
			<div class="relative h-0">
				<div class="relative mx-auto h-0 w-[min(860px,calc(100%-2*var(--ov)-16px))]">
					<ScrollRoll diameter={rolls.top} edge="top" />
				</div>
			</div>
		</div>

		<div class="relative mx-auto w-[min(860px,calc(100%-2*var(--ov)-16px))]">
			<article
				aria-label={`Il background di ${name}`}
				class="relative min-h-[60dvh] overflow-hidden bg-(--page) px-[76px] pt-[78px] pb-[104px] text-page-ink shadow-(--page-shadow) max-[900px]:px-[52px] max-[760px]:px-5 max-[760px]:pt-[62px] max-[760px]:pb-[84px] print:min-h-0 print:p-0 print:shadow-none"
			>
				<div
					aria-hidden="true"
					class="paper-aged pointer-events-none absolute inset-0 print:hidden"
				></div>
				<div
					aria-hidden="true"
					class="paper-grain pointer-events-none absolute inset-0 print:hidden"
				></div>
				<div class="relative">
					{#key background.generation}
						<BackgroundEditor
							content={background.content}
							editable={editing}
							{typography}
							{maxChars}
							label={`La storia di ${name}`}
							hint={editing
								? `Comincia da qui. Chi è ${name}? Da dove viene, e che cosa cerca?`
								: ''}
							onchange={(content: JSONContent) => {
								refused = false;
								background.update(content);
							}}
							onfull={() => {
								refused = true;
							}}
							ontransaction={() => {
								touched += 1;
							}}
							oneditor={(created: Editor | null) => {
								editor = created;
							}}
						/>
					{/key}
				</div>
			</article>
			<div class="sticky bottom-0 z-[15] h-0 print:hidden">
				<ScrollRoll diameter={rolls.bottom} edge="bottom" />
			</div>
		</div>
	</div>

	<div
		class="mx-auto flex max-w-[860px] flex-wrap items-center justify-center gap-x-9 gap-y-3.5 px-4 pt-[34px] pb-[clamp(48px,7vw,88px)] text-[16px] print:hidden"
	>
		<div class="max-w-[520px] flex-[1_1_320px]">
			<div class="flex justify-between gap-3">
				<span>{fill}</span>
				<span>{formatCount(maxChars)} caratteri al massimo</span>
			</div>
			<div role="img" aria-label={fill} class="mt-2 h-2 overflow-hidden rounded-full bg-(--track)">
				<div
					data-background-fill
					class={['h-full rounded-full', nearlyFull ? 'bg-danger' : 'bg-orn']}
					style:width={`${String(Math.max(percent, background.length > 0 ? 1.2 : 0))}%`}
				></div>
			</div>
		</div>
		{#if editing}
			<p class="m-0 flex-[1_1_100%] text-center">
				Il testo si salva da solo dopo qualche secondo di pausa.
			</p>
			<ShortcutHint />
		{/if}
	</div>
</div>

<script lang="ts">
	import { onDestroy, untrack } from 'svelte';
	import { beforeNavigate, goto, onNavigate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { getApi } from '#lib/api/index.js';
	import { DiaryBook } from '#lib/book/book.svelte.js';
	import type { Bookmark } from '#lib/book/bookmark.js';
	import { createDraftStore, type Draft } from '#lib/book/draft.js';
	import type { SpreadData } from '#lib/book/pages.js';
	import { SINGLE_BREAKPOINT } from '#lib/book/layout.js';
	import {
		counterLabel,
		leftPageOf,
		parseSpread,
		spreadOf,
		visiblePages,
		type View
	} from '#lib/book/spread.js';
	import type { CharacterDetail } from '#lib/characters/api.js';
	import Button from '#lib/components/ui/Button.svelte';
	import ConfirmDeleteDialog from '#lib/components/ui/ConfirmDeleteDialog.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import TrashIcon from '#lib/components/ui/TrashIcon.svelte';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import DiaryDialog from '#lib/components/diaries/DiaryDialog.svelte';
	import { deleteDiary, fetchDiary, type DiaryDetail, type SavedDiary } from '#lib/diaries/api.js';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import BookMessage from './BookMessage.svelte';
	import DiaryHeader from './DiaryHeader.svelte';
	import EditorToolbar from './EditorToolbar.svelte';
	import OpenBook from './OpenBook.svelte';
	import PageScrubber from './PageScrubber.svelte';
	import SaveStatus from './SaveStatus.svelte';

	interface Props {
		diary: DiaryDetail;
		character: CharacterDetail;
		initial: SpreadData;
		spread: number;
		drafts?: Draft[];
		bookmark?: Bookmark | null;
	}

	let { diary, character, initial, spread, drafts = [], bookmark = null }: Props = $props();

	const wide = window.matchMedia(`(min-width: ${String(SINGLE_BREAKPOINT)}px)`);
	const calm = window.matchMedia('(prefers-reduced-motion: reduce)');

	const book = untrack(
		() =>
			new DiaryBook(getApi(), diary.id ?? '', initial, spread, {
				autoSave: () => preferences.value.autoSave,
				saveDelay: () => preferences.value.autoSaveDelay * 1000,
				drafts: createDraftStore(),
				bookmark,
				animate: () =>
					preferences.value.pageTurn && !preferences.value.reduceMotion && !calm.matches,
				onview: (view) => {
					void goto(
						resolve(`/(app)/diaries/[diaryId]?vista=${String(view)}`, { diaryId: diary.id ?? '' }),
						{ shallow: true }
					);
				}
			})
	);
	untrack(() => {
		book.restore(drafts);
		book.setSingle(!wide.matches);
	});

	let deleting = $state<number | null>(null);
	let current = $state<DiaryDetail>(untrack(() => diary));
	let editingDiary = $state(false);
	let deletingDiary = $state(false);

	async function removePage(): Promise<void> {
		const target = deleting;
		if (target === null) {
			return;
		}
		await book.removePage(target);
		deleting = null;
	}

	async function removeDiary(): Promise<void> {
		await deleteDiary(getApi(), current.id ?? '');
		book.discard();
		await goto(resolve('/(app)/characters/[characterId]', { characterId: character.id ?? '' }));
	}

	async function diarySaved(result: SavedDiary): Promise<void> {
		editingDiary = false;
		const title = result.diary.title ?? 'Il diario';
		try {
			current = await fetchDiary(getApi(), current.id ?? '');
		} catch {
			current = result.diary;
		}
		if (result.coverError === null) {
			book.notice.show(`${title} è stato aggiornato.`);
		} else {
			book.notice.show(
				`${title} è stato salvato, ma la copertina non è cambiata: ${result.coverError}`,
				true
			);
		}
	}

	function viewAt(position: number): View {
		return { single: book.single, focus: book.single ? position : leftPageOf(position) };
	}

	const font = $derived(
		current.font !== undefined && current.font !== null && current.font.trim() !== ''
			? current.font
			: preferences.value.font
	);
	const fontSize = $derived(
		current.fontSize !== undefined && current.fontSize !== null && current.fontSize > 0
			? current.fontSize
			: preferences.value.fontSize
	);

	$effect(() => {
		const listener = (): void => {
			book.setSingle(!wide.matches);
		};
		wide.addEventListener('change', listener);
		return () => {
			wide.removeEventListener('change', listener);
		};
	});

	function addressView(): number {
		return parseSpread((page.shallow?.url ?? page.url).searchParams.get('vista'));
	}

	let observed = untrack(addressView);
	let waiting = false;

	$effect(() => {
		const view = addressView();
		const working = book.working;
		untrack(() => {
			if (view !== observed) {
				observed = view;
				waiting = true;
			}
			if (waiting && !working) {
				waiting = false;
				if (view !== book.spread) {
					void book.show(view);
				}
			}
		});
	});

	beforeNavigate((navigation) => {
		if (
			navigation.type !== 'popstate' &&
			(book.failure !== null || book.conflict !== null || book.held.length > 0)
		) {
			navigation.cancel();
		}
	});

	onNavigate(async () => {
		await book.flush();
	});

	onDestroy(() => {
		void book.leave();
		book.dispose();
	});

	function keys(event: KeyboardEvent): void {
		if (
			!event.ctrlKey &&
			!event.metaKey &&
			!event.altKey &&
			(event.key.length === 1 || event.key === 'Enter') &&
			book.capture(event.key === 'Enter' ? '\n' : event.key)
		) {
			event.preventDefault();
			return;
		}
		if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 's') {
			event.preventDefault();
			void book.flush();
			return;
		}
		if (!event.altKey || (event.target instanceof HTMLElement && event.target.isContentEditable)) {
			return;
		}
		if (event.key === 'ArrowLeft' && book.previous) {
			event.preventDefault();
			void book.turn('backward');
		} else if (event.key === 'ArrowRight' && book.next) {
			event.preventDefault();
			void book.turn('forward');
		}
	}

	function leaving(event: BeforeUnloadEvent): void {
		if (book.unsaved) {
			event.preventDefault();
		}
	}

	function reconnect(): void {
		book.reconnect();
	}

	function hide(): void {
		if (document.visibilityState === 'hidden') {
			void book.flush();
		}
	}
</script>

<svelte:head>
	<title>{current.title ?? 'Diario'} · Diario GDR</title>
</svelte:head>

<svelte:window onkeydown={keys} onbeforeunload={leaving} ononline={reconnect} />
<svelte:document onvisibilitychange={hide} />

<Container
	as="section"
	aria-labelledby="page-title"
	class="pt-[clamp(8px,2vw,24px)] pb-[clamp(32px,5vw,64px)]"
>
	<DiaryHeader
		diary={current}
		{character}
		counter={book.label}
		onedit={() => {
			editingDiary = true;
		}}
		ondelete={() => {
			deletingDiary = true;
		}}
	>
		{#snippet status()}
			<SaveStatus state={book.state} />
			<Button
				variant="outline"
				size="sm"
				disabled={book.state === 'saved' || book.state === 'saving' || book.state === 'conflict'}
				onclick={() => {
					void book.flush();
				}}
			>
				Salva
			</Button>
		{/snippet}
	</DiaryHeader>

	<div class="mt-5 flex flex-col gap-5">
		<EditorToolbar
			editor={book.toolbarEditor}
			touched={book.touched}
			class="self-center max-[759px]:sticky max-[759px]:bottom-3 max-[759px]:z-20 max-[759px]:order-last min-[760px]:max-[1109px]:sticky min-[760px]:max-[1109px]:top-0 min-[760px]:max-[1109px]:z-20"
		/>
		<OpenBook
			{book}
			{font}
			{fontSize}
			lineSpacing={preferences.value.lineSpacing}
			coverColor={current.coverColor}
			ondeletepage={(page: number) => {
				deleting = page;
			}}
		/>
		{#if book.conflict !== null}
			<BookMessage tone="error">
				La pagina {book.conflict.page} è stata modificata altrove, forse da un altro dispositivo o da
				un'altra scheda. Il salvataggio è in pausa: scegli quale versione tenere.
				{#snippet action()}
					<div class="flex flex-wrap gap-3">
						<Button
							variant="outline"
							size="sm"
							onclick={() => {
								void book.resolveConflict('theirs');
							}}
						>
							Usa la versione salvata
						</Button>
						<Button
							variant="outline"
							size="sm"
							onclick={() => {
								void book.resolveConflict('mine');
							}}
						>
							Tieni la mia versione
						</Button>
					</div>
				{/snippet}
			</BookMessage>
		{:else if book.failure !== null && book.gone}
			<BookMessage tone="error">
				Pagina {book.failure.page}: {book.failure.message}
			</BookMessage>
		{:else if book.failure !== null}
			<BookMessage tone="error">
				{#if book.offline}
					Sei offline. Il testo resta qui sopra e lo salvo appena torni online.
				{:else if book.rejected}
					La pagina {book.failure.page} non è stata salvata: {book.failure.message} Il testo è ancora
					qui sopra: non lo perdi.
				{:else}
					La pagina {book.failure.page} non è stata salvata. Il testo è ancora qui sopra: non lo perdi.
				{/if}
				{#snippet action()}
					<Button
						variant="outline"
						size="sm"
						onclick={() => {
							book.retry();
						}}
					>
						Riprova ora
					</Button>
				{/snippet}
			</BookMessage>
		{:else if book.held.length > 0}
			<BookMessage tone="error">
				Non riesco ad aprire la pagina {book.held[0]}: il testo che ci va lo tengo qui e lo aggiungo
				appena si apre.
				{#snippet action()}
					<Button
						variant="outline"
						size="sm"
						onclick={() => {
							void book.loadHeld();
						}}
					>
						Riprova ora
					</Button>
				{/snippet}
			</BookMessage>
		{/if}
		{#if book.full}
			<BookMessage tone="info">
				Il diario è pieno: 100 pagine su 100. Per scrivere ancora, rilegane uno nuovo.
				{#snippet action()}
					<a
						href={resolve('/(app)/characters/[characterId]', { characterId: character.id ?? '' })}
						class={buttonClasses('outline', 'sm')}
					>
						Nuovo diario
					</a>
				{/snippet}
			</BookMessage>
		{/if}
		<PageScrubber
			position={book.single ? book.focus : book.spread}
			count={book.single ? book.last : spreadOf(book.last)}
			written={book.last}
			describe={(position: number) => counterLabel(viewAt(position), book.last)}
			mark={(position: number) => {
				const pages = visiblePages(viewAt(position)).filter((page) => page <= book.last);
				return pages.map(String).join('–');
			}}
			ongo={(position: number) => book.goTo(book.single ? position : leftPageOf(position))}
		>
			{#snippet end()}
				{#if book.single && book.deletable(book.focus)}
					<IconButton
						label={`Elimina la pagina ${String(book.focus)}`}
						class="flex-none"
						onclick={() => {
							deleting = book.focus;
						}}
					>
						<TrashIcon size={20} />
					</IconButton>
				{/if}
			{/snippet}
		</PageScrubber>
	</div>
</Container>

<ConfirmDeleteDialog
	open={deleting !== null}
	title={`Eliminare la pagina ${String(deleting ?? 0)}?`}
	subtitle="Stai per cancellare questa pagina."
	intro="Verrà cancellato per sempre:"
	consequences={['tutto il testo scritto in questa pagina.']}
	fallbackError="Non riesco a eliminare la pagina."
	onconfirm={removePage}
	onclose={() => {
		deleting = null;
	}}
/>

<DiaryDialog
	open={editingDiary}
	characterId={character.id ?? ''}
	characterName={character.name ?? 'Il personaggio'}
	diary={current}
	onclose={() => {
		editingDiary = false;
	}}
	onsaved={diarySaved}
/>

<ConfirmDeleteDialog
	open={deletingDiary}
	title={`Eliminare ${current.title ?? 'il diario'}?`}
	subtitle="Stai per cancellare questo diario."
	consequences={['il diario e la sua copertina;', 'tutte le sue pagine, con il testo scritto.']}
	fallbackError="Non riesco a eliminare il diario."
	onconfirm={removeDiary}
	onclose={() => {
		deletingDiary = false;
	}}
/>

<NoticePopup notice={book.notice} />

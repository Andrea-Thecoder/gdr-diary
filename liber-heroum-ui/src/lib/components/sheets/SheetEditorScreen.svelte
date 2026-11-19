<script lang="ts">
	import 'pdfjs-dist/legacy/web/pdf_viewer.css';
	import { onDestroy, untrack } from 'svelte';
	import { resolve } from '$app/paths';
	import { beforeNavigate, goto } from '$app/navigation';
	import { getApi } from '#lib/api/index.js';
	import SaveStatus from '#lib/components/book/SaveStatus.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import { backLinkClasses } from '#lib/components/ui/button.js';
	import type { SaveState } from '#lib/book/book.svelte.js';
	import { AutoSaver, retryDelay } from '#lib/pdf/autosave.js';
	import { PdfEditor } from '#lib/pdf/editor.svelte.js';
	import { replaceSheetFile, type SheetDetail } from '#lib/sheets/api.js';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import { checkSheetFile } from '#lib/sheets/format.js';
	import { Notice } from '#lib/ui/notice.svelte.js';
	import LeaveEditorDialog from './LeaveEditorDialog.svelte';

	interface Props {
		sheet: SheetDetail;
	}

	let { sheet }: Props = $props();

	const editor = new PdfEditor();
	const notice = new Notice();

	let container = $state<HTMLDivElement | null>(null);
	let viewerElement = $state<HTMLDivElement | null>(null);
	let saving = $state(false);
	// dove si voleva andare mentre la finestra "Modifiche non salvate" è aperta
	let leavingTo = $state<URL | null>(null);
	let leaveConfirmed = false;
	// conta le modifiche: se si scrive mentre si salva, il salvataggio non azzera lo stato "da salvare"
	let changes = 0;
	let queue: Promise<void> = Promise.resolve();
	// come nel diario: l'ultimo salvataggio fallito (anche per rete assente) si riprova da solo
	let failed = $state(false);
	let offline = $state(false);
	let attempts = 0;
	let retryTimer: ReturnType<typeof setTimeout> | null = null;

	// stesse regole del diario: attivo e pausa dalle impostazioni
	const autoSaver = new AutoSaver({
		enabled: () => preferences.value.autoSave && editor.editable,
		delay: () => preferences.value.autoSaveDelay * 1000,
		run: () => save()
	});

	const saveState = $derived.by<SaveState>(() => {
		if (failed) {
			return offline ? 'offline' : 'unsaved';
		}
		if (saving) {
			return 'saving';
		}
		if (editor.dirty) {
			return preferences.value.autoSave ? 'pending' : 'unsaved';
		}
		return 'saved';
	});

	const title = $derived(sheet.title ?? 'Scheda');
	const sheetsHref = $derived(
		resolve('/(app)/characters/[characterId]/sheets', { characterId: sheet.characterId ?? '' })
	);

	$effect(() => {
		if (container !== null && viewerElement !== null) {
			const [box, pages, url] = [container, viewerElement, untrack(() => sheet.url ?? '')];
			void editor.open(box, pages, url);
		}
	});

	onDestroy(() => {
		autoSaver.cancel();
		clearRetry();
		editor.destroy();
	});

	// Dentro l'app chiede con una finestra nostra; chiudere la scheda o uscire dal sito lo chiede
	// il browser con il suo avviso (beforeunload), che non si può personalizzare.
	beforeNavigate((navigation) => {
		if (!editor.dirty || leaveConfirmed || navigation.willUnload || navigation.to === null) {
			return;
		}
		navigation.cancel();
		if (autoSaver.isEnabled()) {
			void saveThenGo(navigation.to.url);
		} else {
			leavingTo = navigation.to.url;
		}
	});

	// Con il salvataggio automatico acceso si salva e si esce; la finestra compare solo se non riesce.
	async function saveThenGo(target: URL): Promise<void> {
		autoSaver.cancel();
		await save();
		if (editor.dirty) {
			leavingTo = target;
			return;
		}
		leaveConfirmed = true;
		// eslint-disable-next-line svelte/no-navigation-without-resolve -- l'indirizzo è quello già risolto della navigazione annullata
		await goto(target);
	}

	function edited(): void {
		changes += 1;
		autoSaver.touch();
	}

	function hidden(): void {
		if (document.visibilityState === 'hidden' && editor.dirty && autoSaver.isEnabled()) {
			void save();
		}
	}

	async function leave(): Promise<void> {
		const target = leavingTo;
		leavingTo = null;
		if (target !== null) {
			leaveConfirmed = true;
			// eslint-disable-next-line svelte/no-navigation-without-resolve -- l'indirizzo è quello già risolto della navigazione annullata
			await goto(target);
		}
	}

	function warn(event: BeforeUnloadEvent): void {
		if (editor.dirty) {
			event.preventDefault();
		}
	}

	function clearRetry(): void {
		if (retryTimer !== null) {
			clearTimeout(retryTimer);
			retryTimer = null;
		}
	}

	// riprova dopo 5, 10, 20 e poi ogni 30 secondi; senza rete aspetta che torni (`online`)
	function retryLater(): void {
		clearRetry();
		if (!autoSaver.isEnabled() || offline) {
			return;
		}
		retryTimer = setTimeout(() => {
			retryTimer = null;
			void save();
		}, retryDelay(attempts));
		attempts += 1;
	}

	function retryNow(): void {
		if (failed && editor.dirty) {
			void save();
		}
	}

	function fail(message: string, retry: boolean): void {
		failed = true;
		notice.show(message, true);
		if (retry) {
			retryLater();
		}
	}

	// un salvataggio alla volta, in coda
	function save(): Promise<void> {
		queue = queue.then(write);
		return queue;
	}

	async function write(): Promise<void> {
		if (!editor.dirty) {
			return;
		}
		saving = true;
		const version = changes;
		try {
			const bytes = await editor.save();
			const file = new File([bytes as BlobPart], sheet.fileName ?? 'scheda.pdf', {
				type: 'application/pdf'
			});
			const problem = checkSheetFile(file);
			if (problem !== null) {
				offline = false;
				fail(problem, false);
				return;
			}
			await replaceSheetFile(getApi(), sheet.id ?? '', file);
			clearRetry();
			failed = false;
			offline = false;
			attempts = 0;
			if (changes === version) {
				editor.markSaved();
			}
			notice.show('Scheda salvata.');
		} catch (cause) {
			offline = cause instanceof TypeError;
			fail(cause instanceof Error ? cause.message : 'Non riesco a salvare il PDF.', true);
		} finally {
			saving = false;
		}
	}
</script>

<svelte:window onbeforeunload={warn} ononline={retryNow} />
<svelte:document onvisibilitychange={hidden} />

<Container as="section" aria-labelledby="page-title" class="pt-[clamp(8px,2vw,24px)]">
	<a href={sheetsHref} class={backLinkClasses}>
		<ArrowIcon direction="left" size={18} />
		Tutte le schede
	</a>
	<div class="mt-4 flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
		<div class="min-w-0">
			<h1
				id="page-title"
				class="m-0 font-display text-[clamp(28px,4vw,44px)] leading-[1.06] font-bold [overflow-wrap:anywhere] text-ink"
			>
				{title}
			</h1>
		</div>
		{#if editor.status === 'ready' && editor.editable}
			<div class="flex flex-wrap items-center gap-3">
				<SaveStatus state={saveState} />
				<Button onclick={() => save()} disabled={saving || !editor.dirty}>Salva la scheda</Button>
			</div>
		{/if}
	</div>

	{#if editor.status === 'ready' && !editor.editable}
		<p class="mx-0 mt-4 mb-0 text-[18px] text-soft" data-editor-hint>
			Questo PDF non ha campi da compilare, si può solo leggere.
		</p>
	{/if}

	{#if editor.status === 'error'}
		<div role="alert" class="mt-6 text-[18px] text-label">
			<p class="m-0">{editor.error}</p>
			<!-- eslint-disable svelte/no-navigation-without-resolve -- URL assoluto del PDF (storage), non una rotta dell'app -->
			<a
				href={sheet.url}
				target="_blank"
				rel="noopener noreferrer"
				class="text-ink underline underline-offset-4"
			>
				Aprila in una nuova scheda
			</a>
			<!-- eslint-enable svelte/no-navigation-without-resolve -->
		</div>
	{/if}

	{#if editor.status === 'loading'}
		<p role="status" class="mx-0 mt-6 mb-0 text-[18px] text-soft">Apro la scheda…</p>
	{/if}

	<!-- oninput/onchange arrivano dai campi che pdf.js disegna dentro la pagina: non è un elemento interattivo -->
	<div
		oninput={edited}
		onchange={edited}
		class={[
			'relative mt-6 mb-[clamp(48px,7vw,96px)] h-[min(78dvh,1100px)] min-h-[420px] overflow-hidden rounded-[6px] border border-(color:--rule)',
			editor.status === 'error' && 'hidden'
		]}
	>
		<div bind:this={container} class="absolute inset-0 overflow-auto">
			<div bind:this={viewerElement} class="pdfViewer"></div>
		</div>
	</div>
</Container>

<LeaveEditorDialog
	open={leavingTo !== null}
	onstay={() => {
		leavingTo = null;
	}}
	onleave={leave}
/>

<NoticePopup {notice} />

<style>
	/* pdf.js calcola larghezza e altezza della pagina senza il bordo trasparente (content-box);
	   con il box-sizing globale di Tailwind il bordo si mangia 18 px e i campi scivolano via
	   dal disegno. */
	:global(.pdfViewer .page) {
		box-sizing: content-box;
	}
</style>

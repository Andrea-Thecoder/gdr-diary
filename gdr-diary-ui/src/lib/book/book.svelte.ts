import type { Editor, JSONContent } from '@tiptap/core';
import { tick } from 'svelte';
import { SvelteMap, SvelteSet } from 'svelte/reactivity';
import type { ApiClient } from '#lib/api/client.js';
import { Notice } from '#lib/ui/notice.svelte.js';
import { removeBookmark, setBookmark, type Bookmark } from './bookmark.js';
import { judgeDraft, type Draft, type DraftStore } from './draft.js';
import {
	ConflictError,
	MAX_PAGES,
	MISSING_PAGE_MESSAGE,
	MissingPageError,
	RejectedPageError,
	createPage,
	deletePage,
	emptyDoc,
	fetchSpread,
	hasText,
	sameContent,
	updatePage,
	type DiaryPageData,
	type SpreadData
} from './pages.js';
import { isBlankRest, prependDoc } from './pagination.js';
import { PageTagStore } from './tags.svelte.js';
import {
	counterLabel,
	hasNext,
	hasPrevious,
	leftPageOf,
	nextFocus,
	spreadOf,
	visiblePages,
	type TurnDirection
} from './spread.js';

export type SaveState = 'saved' | 'pending' | 'saving' | 'unsaved' | 'offline' | 'conflict';

export const SAVE_DELAY = 5000;
export const SAVE_MAX_WAIT = 20000;
export const RETRY_DELAY = 5000;
export const RETRY_MAX_DELAY = 30000;
export const DRAFT_DELAY = 500;
const TURN_FALLBACK = 1400;
const CAPTURE_LIMIT = 3000;
const UNSAVED_BOOKMARK_MESSAGE =
	'Prima devo salvare la pagina, ma non ci riesco. Riprova tra poco.';

interface PageState {
	id: string | null;
	content: JSONContent;
	version: number;
}

export type ConflictChoice = 'mine' | 'theirs';

export interface SaveConflict {
	page: number;
}

export interface PageHandle {
	editor: Editor;
	prepend: (rest: JSONContent, cursor: number | null) => void;
	focus: () => void;
	focusEnd: () => void;
	insert: (pieces: string[]) => void;
}

export interface Slot {
	page: number;
	content: JSONContent;
}

export interface TurnSnapshot {
	direction: TurnDirection;
	single: boolean;
	from: Slot[];
	to: Slot[];
}

export interface SaveFailure {
	page: number;
	message: string;
}

export interface BookOptions {
	autoSave: () => boolean;
	saveDelay?: () => number;
	drafts: DraftStore;
	bookmark?: Bookmark | null;
	animate: () => boolean;
	onview: (spread: number) => void;
}

function isNetworkError(cause: unknown): boolean {
	return cause instanceof TypeError;
}

function failureMessage(cause: unknown, offline: boolean): string {
	if (offline) {
		return 'Non riesco a raggiungere il server.';
	}
	return cause instanceof Error ? cause.message : 'Non riesco a salvare la pagina.';
}

function bookmarkMessage(cause: unknown): string {
	if (isNetworkError(cause)) {
		return 'Non riesco a raggiungere il server.';
	}
	return cause instanceof Error ? cause.message : 'Non riesco a cambiare il segnalibro.';
}

function loadMessage(cause: unknown): string {
	if (isNetworkError(cause)) {
		return 'Non riesco a raggiungere il server: controlla la connessione e riprova.';
	}
	return cause instanceof Error ? cause.message : 'Non riesco ad aprire le pagine.';
}

export class DiaryBook {
	total = $state(0);
	focus = $state(1);
	single = $state(false);
	turning = $state<TurnSnapshot | null>(null);
	failure = $state<SaveFailure | null>(null);
	saving = $state(false);
	activePage = $state<number | null>(null);
	touched = $state(0);
	generation = $state(0);
	offline = $state(false);
	gone = $state(false);
	rejected = $state(false);
	conflict = $state<SaveConflict | null>(null);
	bookmark = $state<Bookmark | null>(null);
	marking = $state(false);
	readonly notice = new Notice();
	readonly tags: PageTagStore;
	readonly pages = new SvelteMap<number, PageState>();
	readonly dirty = new SvelteSet<number>();
	readonly editors = new SvelteMap<number, PageHandle>();
	readonly pending = new SvelteMap<number, { cursor: number | null }>();
	readonly filled = new SvelteMap<number, boolean>();

	private chain: Promise<boolean> = Promise.resolve(true);
	private saveTimer: ReturnType<typeof setTimeout> | undefined;
	private retryTimer: ReturnType<typeof setTimeout> | undefined;
	private readonly lastSaved = new SvelteMap<number, JSONContent>();
	private readonly leftovers = new SvelteMap<number, Draft>();
	private readonly draftTimers = new SvelteMap<number, ReturnType<typeof setTimeout>>();
	private readonly spilled = new SvelteMap<number, JSONContent[]>();
	private pendingSince: number | null = null;
	private attempts = 0;
	private finishTurn: (() => void) | null = null;
	private busy = $state(false);
	private disposed = false;
	private discarded = false;
	private touching = false;
	private capturing = false;
	private captureTimer: ReturnType<typeof setTimeout> | undefined;
	private typed: string[] = [];

	readonly last = $derived.by(() => {
		let last = this.total;
		for (const [page, state] of this.pages) {
			if (page > last && (state.id !== null || hasText(state.content))) {
				last = page;
			}
		}
		return last;
	});

	readonly ahead = $derived(
		this.last > 0 && this.last < MAX_PAGES && this.filled.get(this.last) === true
	);

	readonly visible = $derived(visiblePages({ single: this.single, focus: this.focus }));
	readonly label = $derived(counterLabel({ single: this.single, focus: this.focus }, this.last));
	readonly previous = $derived(hasPrevious({ single: this.single, focus: this.focus }));
	readonly next = $derived(
		hasNext({ single: this.single, focus: this.focus }, this.last + (this.ahead ? 1 : 0))
	);
	readonly full = $derived(this.last >= MAX_PAGES);
	readonly spread = $derived(spreadOf(this.focus));
	readonly held = $derived([...this.spilled.keys()].sort((a, b) => a - b));

	readonly state = $derived.by<SaveState>(() => {
		if (this.conflict !== null) {
			return 'conflict';
		}
		if (this.failure !== null) {
			return this.offline ? 'offline' : 'unsaved';
		}
		if (this.held.length > 0) {
			return 'unsaved';
		}
		if (this.saving) {
			return 'saving';
		}
		if (this.dirty.size > 0 || this.tags.unsaved) {
			return this.options.autoSave() ? 'pending' : 'unsaved';
		}
		return 'saved';
	});

	readonly toolbarEditor = $derived.by<Editor | null>(() => {
		const page = this.activePage ?? this.visible.find((number) => this.editable(number));
		return page === undefined ? null : (this.editors.get(page)?.editor ?? null);
	});

	constructor(
		private readonly api: ApiClient,
		private readonly diaryId: string,
		initial: SpreadData,
		spread: number,
		private readonly options: BookOptions
	) {
		this.tags = new PageTagStore(api, diaryId);
		for (const page of initial.pages) {
			this.tags.seed(page.id, page.tag);
		}
		this.total = initial.totalPages;
		this.bookmark = options.bookmark ?? null;
		this.store(initial);
		this.focus = leftPageOf(Math.max(spread, 1));
	}

	private store(data: SpreadData): void {
		this.total = data.totalPages;
		for (const page of data.pages) {
			if (!this.dirty.has(page.pageNumber)) {
				this.pages.set(page.pageNumber, {
					id: page.id,
					content: page.content,
					version: page.version
				});
				this.lastSaved.set(page.pageNumber, page.content);
			}
		}
		this.applyDrafts();
		this.applySpills();
	}

	private async read(spread: number): Promise<SpreadData> {
		const data = await fetchSpread(this.api, this.diaryId, spread);
		for (const page of data.pages) {
			this.tags.seed(page.id, page.tag);
		}
		return data;
	}

	restore(found: Draft[]): void {
		for (const draft of found) {
			this.leftovers.set(draft.page, draft);
		}
		this.applyDrafts();
	}

	private applyDrafts(): void {
		const restored: number[] = [];
		const strays: Draft[] = [];
		for (const [page, draft] of [...this.leftovers]) {
			if (this.dirty.has(page)) {
				this.leftovers.delete(page);
				continue;
			}
			const here = this.pages.get(page);
			const moved =
				draft.pageId !== null && here?.id !== draft.pageId ? this.loadedMove(draft) : undefined;
			if (moved !== undefined) {
				this.leftovers.delete(page);
				this.dropDraft(draft);
				continue;
			}
			const verdict = judgeDraft(draft, here, this.total);
			if (verdict === 'wait') {
				continue;
			}
			this.leftovers.delete(page);
			if (verdict === 'drop') {
				this.forget(page);
				continue;
			}
			if (verdict === 'search') {
				strays.push(draft);
				continue;
			}
			this.pages.set(page, {
				id: draft.pageId,
				content: draft.content,
				version: draft.baseVersion
			});
			this.dirty.add(page);
			restored.push(page);
		}
		if (restored.length > 0) {
			const label = restored.length === 1 ? 'pagina' : 'pagine';
			this.notice.show(`Modifiche non salvate recuperate (${label} ${restored.join(', ')})`);
			this.schedule();
		}
		if (strays.length > 0) {
			void this.recover(strays);
		}
	}

	private loadedMove(draft: Draft): { page: number; state: PageState } | undefined {
		for (const [page, state] of this.pages) {
			if (state.id !== null && state.id === draft.pageId && page !== draft.page) {
				return { page, state };
			}
		}
		return undefined;
	}

	private dropDraft(draft: Draft): void {
		if (!this.dirty.has(draft.page)) {
			this.forget(draft.page);
		}
	}

	private async recover(strays: Draft[]): Promise<void> {
		const ids = new SvelteSet(
			strays.flatMap((draft) => (draft.pageId === null ? [] : [draft.pageId]))
		);
		const hint = Math.max(...strays.map((draft) => draft.page));
		const scan = await this.scan(ids, hint);
		if (this.disposed) {
			return;
		}
		if (scan === null) {
			for (const draft of strays) {
				this.leftovers.set(draft.page, draft);
			}
			return;
		}
		this.total = scan.total;
		for (const draft of strays) {
			const found = draft.pageId === null ? undefined : scan.found.get(draft.pageId);
			if (found === undefined) {
				this.adopt(draft);
				continue;
			}
			if (!this.dirty.has(found.pageNumber)) {
				this.pages.set(found.pageNumber, {
					id: found.id,
					content: found.content,
					version: found.version
				});
				this.lastSaved.set(found.pageNumber, found.content);
			}
			this.dropDraft(draft);
		}
		this.schedule();
	}

	private adopt(draft: Draft): void {
		const target = this.last + 1;
		if (target > MAX_PAGES) {
			this.notice.show(
				`La pagina ${String(draft.page)} non esiste più e il diario è pieno: il testo resta salvato su questo dispositivo.`,
				true
			);
			this.leftovers.delete(draft.page);
			return;
		}
		const state = this.pages.get(target);
		this.pages.set(target, {
			id: state?.id ?? null,
			content: state === undefined ? draft.content : prependDoc(state.content, draft.content),
			version: state?.version ?? 0
		});
		this.dirty.add(target);
		this.forget(draft.page);
		this.keepDraft(target);
		this.notice.show(
			`La pagina ${String(draft.page)} non esiste più: ho messo il tuo testo in una nuova pagina (${String(target)}).`,
			true
		);
	}

	private async scan(
		ids: SvelteSet<string>,
		from: number
	): Promise<{ found: SvelteMap<string, DiaryPageData>; total: number } | null> {
		const found = new SvelteMap<string, DiaryPageData>();
		let total: number | null = null;
		let spread = spreadOf(Math.max(from, 1));
		try {
			while (spread >= 1 && (total === null || found.size < ids.size)) {
				const data = await this.read(spread);
				if (this.disposed) {
					return null;
				}
				total = data.totalPages;
				const top = spreadOf(Math.max(total, 1));
				if (spread > top) {
					spread = top;
					continue;
				}
				for (const page of data.pages) {
					if (ids.has(page.id)) {
						found.set(page.id, page);
					}
				}
				spread -= 1;
			}
		} catch {
			return null;
		}
		return { found, total: total ?? this.total };
	}

	private applySpills(): void {
		for (const [page, rests] of [...this.spilled]) {
			const state = this.pages.get(page);
			if (state === undefined && page <= this.total) {
				continue;
			}
			this.spilled.delete(page);
			this.pages.set(page, {
				id: state?.id ?? null,
				content: rests.reduce(
					(content, rest) => prependDoc(content, rest),
					state?.content ?? emptyDoc()
				),
				version: state?.version ?? 0
			});
			this.dirty.add(page);
			this.keepDraft(page);
			this.notice.show(`Ho aggiunto a pagina ${String(page)} il testo che non ci stava.`);
			this.schedule();
		}
	}

	async loadHeld(): Promise<void> {
		for (const page of this.held) {
			try {
				this.store(await this.read(spreadOf(page)));
			} catch (cause) {
				this.notice.show(loadMessage(cause), true);
				return;
			}
		}
	}

	private keepDraft(page: number): void {
		clearTimeout(this.draftTimers.get(page));
		this.draftTimers.set(
			page,
			setTimeout(() => {
				this.draftTimers.delete(page);
				this.writeDraft(page);
			}, DRAFT_DELAY)
		);
	}

	private flushDrafts(): void {
		for (const [page, timer] of [...this.draftTimers]) {
			clearTimeout(timer);
			this.draftTimers.delete(page);
			this.writeDraft(page);
		}
	}

	private writeDraft(page: number): void {
		const state = this.pages.get(page);
		if (state === undefined || !this.dirty.has(page)) {
			return;
		}
		this.options.drafts
			.put({
				diaryId: this.diaryId,
				page,
				pageId: state.id,
				baseVersion: state.version,
				content: state.content,
				savedAt: Date.now()
			})
			.catch(() => undefined);
	}

	private forget(page: number): void {
		clearTimeout(this.draftTimers.get(page));
		this.draftTimers.delete(page);
		this.options.drafts.remove(this.diaryId, page).catch(() => undefined);
	}

	content(page: number): JSONContent {
		return this.pages.get(page)?.content ?? emptyDoc();
	}

	editable(page: number): boolean {
		if (page < 1 || page > MAX_PAGES) {
			return false;
		}
		if (!this.pages.has(page) && page > this.total) {
			return (this.last === 0 && page === 1) || (page === this.last + 1 && this.ahead);
		}
		return page === 1 || hasText(this.content(page)) || (this.filled.get(page - 1) ?? true);
	}

	markFull(page: number, full: boolean): void {
		if (this.filled.get(page) !== full) {
			this.filled.set(page, full);
		}
	}

	slot(page: number): Slot {
		return { page, content: this.content(page) };
	}

	register(page: number, handle: PageHandle): void {
		this.editors.set(page, handle);
	}

	unregister(page: number, handle: PageHandle): void {
		if (this.editors.get(page) === handle) {
			this.editors.delete(page);
		}
		if (this.activePage === page) {
			this.activePage = null;
		}
	}

	activate(page: number): void {
		this.activePage = page;
	}

	touch(): void {
		if (this.touching) {
			return;
		}
		this.touching = true;
		queueMicrotask(() => {
			this.touching = false;
			this.touched += 1;
		});
	}

	capture(text: string): boolean {
		if (!this.capturing) {
			return false;
		}
		this.typed.push(text);
		return true;
	}

	takeTyped(keepBreaks = false): string[] {
		clearTimeout(this.captureTimer);
		this.capturing = false;
		const typed = this.typed;
		this.typed = [];
		if (keepBreaks) {
			return typed;
		}
		const first = typed.findIndex((piece) => piece !== '\n');
		return first === -1 ? [] : typed.slice(first);
	}

	private startCapture(): void {
		this.capturing = true;
		this.typed = [];
		clearTimeout(this.captureTimer);
		this.captureTimer = setTimeout(() => {
			this.capturing = false;
			this.typed = [];
		}, CAPTURE_LIMIT);
	}

	takePending(page: number): { cursor: number | null } | null {
		const request = this.pending.get(page) ?? null;
		this.pending.delete(page);
		return request;
	}

	setSingle(single: boolean): void {
		if (this.single === single) {
			return;
		}
		this.single = single;
		void this.ensure(this.focus);
	}

	update(page: number, content: JSONContent): void {
		const state = this.pages.get(page);
		if (state === undefined) {
			if (!hasText(content)) {
				return;
			}
			this.pages.set(page, { id: null, content, version: 0 });
		} else {
			this.pages.set(page, { id: state.id, content, version: state.version });
		}
		this.dirty.add(page);
		this.keepDraft(page);
		this.schedule();
	}

	private schedule(): void {
		clearTimeout(this.saveTimer);
		if (!this.options.autoSave() || this.conflict !== null || this.discarded) {
			return;
		}
		const now = Date.now();
		this.pendingSince ??= now;
		const delay = this.options.saveDelay?.() ?? SAVE_DELAY;
		const remaining = this.pendingSince + Math.max(SAVE_MAX_WAIT, delay) - now;
		this.saveTimer = setTimeout(
			() => {
				void this.flush();
			},
			Math.max(0, Math.min(delay, remaining))
		);
	}

	flush(): Promise<boolean> {
		if (this.discarded) {
			return Promise.resolve(true);
		}
		clearTimeout(this.saveTimer);
		this.pendingSince = null;
		void this.tags.flush();
		this.flushDrafts();
		this.chain = this.chain.then(() => this.round());
		return this.chain;
	}

	retry(): void {
		this.attempts = 0;
		this.resume();
	}

	reconnect(): void {
		if (this.offline && this.failure !== null) {
			this.retry();
		}
		if (this.held.length > 0) {
			void this.loadHeld();
		}
		this.applyDrafts();
	}

	private resume(): void {
		this.failure = null;
		this.gone = false;
		this.rejected = false;
		void this.flush();
	}

	private scheduleRetry(): void {
		if (this.disposed) {
			return;
		}
		clearTimeout(this.retryTimer);
		const delay = Math.min(RETRY_DELAY * 2 ** this.attempts, RETRY_MAX_DELAY);
		this.attempts += 1;
		this.retryTimer = setTimeout(() => {
			this.resume();
		}, delay);
	}

	private async round(): Promise<boolean> {
		clearTimeout(this.retryTimer);
		if (this.conflict !== null) {
			return false;
		}
		this.saving = true;
		try {
			let progress = true;
			while (progress && this.dirty.size > 0) {
				progress = false;
				for (const page of [...this.dirty].sort((a, b) => a - b)) {
					const saved = await this.saveOne(page);
					if (saved === 'failed') {
						return false;
					}
					progress = progress || saved === 'saved';
				}
			}
			this.failure = null;
			this.offline = false;
			this.gone = false;
			this.rejected = false;
			this.attempts = 0;
			return true;
		} finally {
			this.saving = false;
		}
	}

	private async saveOne(page: number): Promise<'saved' | 'skipped' | 'failed'> {
		const state = this.pages.get(page);
		if (state === undefined) {
			this.dirty.delete(page);
			return 'skipped';
		}
		if (state.id === null && !hasText(state.content)) {
			this.dirty.delete(page);
			this.forget(page);
			return 'skipped';
		}
		const baseline = this.lastSaved.get(page);
		if (state.id !== null && baseline !== undefined && sameContent(baseline, state.content)) {
			this.dirty.delete(page);
			this.forget(page);
			return 'skipped';
		}
		const content = state.content;
		try {
			const saved =
				state.id === null
					? await createPage(this.api, this.diaryId, content)
					: await updatePage(this.api, state.id, content, state.version);
			this.lastSaved.set(page, content);
			const current = this.pages.get(page);
			if (current !== undefined) {
				this.pages.set(page, {
					id: saved.id,
					content: current.content,
					version: saved.version
				});
				if (current.content === content) {
					this.dirty.delete(page);
					this.forget(page);
				}
			}
			if (state.id === null) {
				this.total = Math.max(this.total, saved.pageNumber);
				this.tags.seed(saved.id, saved.tag);
			}
			return 'saved';
		} catch (cause) {
			if (this.discarded) {
				return 'skipped';
			}
			if (cause instanceof ConflictError) {
				this.conflict = { page };
				return 'failed';
			}
			this.offline = isNetworkError(cause);
			this.gone = cause instanceof MissingPageError;
			this.rejected = cause instanceof RejectedPageError;
			this.failure = { page, message: failureMessage(cause, this.offline) };
			if (!this.gone && !this.rejected) {
				this.scheduleRetry();
			}
			return 'failed';
		}
	}

	async resolveConflict(choice: ConflictChoice): Promise<boolean> {
		const conflict = this.conflict;
		if (conflict === null) {
			return true;
		}
		const id = this.pages.get(conflict.page)?.id ?? null;
		let scan = id === null ? null : await this.scan(new SvelteSet([id]), conflict.page);
		if (scan === null) {
			this.notice.show('Non riesco a leggere la versione salvata. Riprova.', true);
			return false;
		}
		let fresh = id === null ? undefined : scan.found.get(id);
		if (fresh === undefined) {
			this.conflict = null;
			this.gone = true;
			this.failure = { page: conflict.page, message: MISSING_PAGE_MESSAGE };
			return false;
		}
		if (fresh.pageNumber !== conflict.page) {
			const ids = new SvelteSet<string>();
			let top = conflict.page;
			for (const page of this.dirty) {
				const stateId = this.pages.get(page)?.id;
				if (stateId !== undefined && stateId !== null) {
					ids.add(stateId);
					top = Math.max(top, page);
				}
			}
			scan = await this.scan(ids, top);
			fresh = scan?.found.get(id ?? '');
			if (scan === null || fresh === undefined) {
				this.notice.show('Non riesco a leggere la versione salvata. Riprova.', true);
				return false;
			}
			await this.rebase(scan);
		}
		const page = fresh.pageNumber;
		const mine = this.pages.get(page);
		this.lastSaved.set(page, fresh.content);
		this.conflict = null;
		if (choice === 'mine' && mine !== undefined) {
			this.pages.set(page, { id: mine.id, content: mine.content, version: fresh.version });
			return this.flush();
		}
		this.pages.set(page, { id: fresh.id, content: fresh.content, version: fresh.version });
		this.dirty.delete(page);
		this.forget(page);
		this.editors.get(page)?.editor.commands.setContent(fresh.content, { emitUpdate: false });
		this.touch();
		this.notice.show('Versione salvata ripristinata');
		if (this.dirty.size > 0) {
			this.schedule();
		}
		return true;
	}

	private async rebase(scan: {
		found: SvelteMap<string, DiaryPageData>;
		total: number;
	}): Promise<void> {
		const delta = Math.max(this.total - scan.total, 0);
		const moved = new SvelteMap<number, { state: PageState; baseline: JSONContent | undefined }>();
		const gone: [number, PageState][] = [];
		for (const page of [...this.dirty]) {
			const state = this.pages.get(page);
			if (state === undefined) {
				continue;
			}
			const baseline = this.lastSaved.get(page);
			const server = state.id === null ? undefined : scan.found.get(state.id);
			if (state.id === null) {
				moved.set(Math.max(page - delta, 1), { state, baseline });
			} else if (server === undefined) {
				gone.push([page, state]);
			} else {
				const unchanged = baseline !== undefined && sameContent(baseline, server.content);
				moved.set(server.pageNumber, {
					state: {
						id: state.id,
						content: state.content,
						version: unchanged ? server.version : state.version
					},
					baseline: baseline ?? server.content
				});
			}
		}
		for (const [page, state] of gone) {
			const taken = moved.get(page);
			moved.set(page, {
				state:
					taken === undefined
						? state
						: { ...taken.state, content: prependDoc(taken.state.content, state.content) },
				baseline: taken?.baseline ?? this.lastSaved.get(page)
			});
		}
		for (const page of [...this.dirty]) {
			this.forget(page);
		}
		const held = [...this.spilled];
		this.spilled.clear();
		for (const [page, rests] of held) {
			const target = Math.max(page - delta, 1);
			this.spilled.set(target, [...rests, ...(this.spilled.get(target) ?? [])]);
		}
		this.pages.clear();
		this.lastSaved.clear();
		this.editors.clear();
		this.filled.clear();
		this.dirty.clear();
		this.total = scan.total;
		for (const [page, { state, baseline }] of moved) {
			this.pages.set(page, state);
			if (baseline !== undefined) {
				this.lastSaved.set(page, baseline);
			}
			this.dirty.add(page);
			this.keepDraft(page);
		}
		this.focus = this.landing(this.focus - delta);
		this.activePage = null;
		this.generation += 1;
		try {
			await this.ensure(this.focus);
		} catch (cause) {
			this.notice.show(loadMessage(cause), true);
		}
	}

	private async settle(): Promise<number[] | null> {
		if (!(await this.flush())) {
			return null;
		}
		return this.prune();
	}

	private async prune(): Promise<number[]> {
		if (this.discarded) {
			return [];
		}
		const blank = [...this.pages]
			.filter(
				([page, state]) => state.id !== null && !this.dirty.has(page) && !hasText(state.content)
			)
			.sort(([a], [b]) => a - b);
		const deleted: number[] = [];
		try {
			for (const [page, state] of blank) {
				if (state.id !== null) {
					await deletePage(this.api, state.id);
					this.tags.forget(state.id);
					deleted.push(page);
				}
			}
		} catch (cause) {
			this.notice.show(
				cause instanceof Error ? cause.message : 'Non riesco a eliminare la pagina.',
				true
			);
		}
		if (deleted.length > 0) {
			this.renumbered(deleted);
			try {
				await this.ensure(this.focus);
			} catch (cause) {
				this.notice.show(loadMessage(cause), true);
			}
			this.generation += 1;
		}
		return deleted;
	}

	deletable(page: number): boolean {
		return this.written(page);
	}

	pageId(page: number): string | null {
		return this.pages.get(page)?.id ?? null;
	}

	markable(page: number): boolean {
		return this.written(page);
	}

	marked(page: number): boolean {
		const id = this.pages.get(page)?.id ?? null;
		return id !== null && id === this.bookmark?.pageId;
	}

	async toggleBookmark(page: number): Promise<void> {
		if (this.marking || this.busy || this.discarded || !this.markable(page)) {
			return;
		}
		this.marking = true;
		try {
			if (this.marked(page)) {
				await removeBookmark(this.api, this.diaryId);
				this.bookmark = null;
				return;
			}
			if (this.pages.get(page)?.id === null) {
				await this.flush();
			}
			const id = this.pages.get(page)?.id ?? null;
			if (id === null) {
				throw new Error(UNSAVED_BOOKMARK_MESSAGE);
			}
			this.bookmark = await setBookmark(this.api, this.diaryId, id);
		} catch (cause) {
			this.notice.show(bookmarkMessage(cause), true);
		} finally {
			this.marking = false;
		}
	}

	private written(page: number): boolean {
		const state = this.pages.get(page);
		return state !== undefined && (state.id !== null || hasText(state.content));
	}

	async removePage(page: number): Promise<void> {
		if (this.busy) {
			throw new Error('Aspetta un momento e riprova.');
		}
		if (!this.deletable(page)) {
			return;
		}
		this.busy = true;
		try {
			if (!(await this.flush())) {
				throw new Error('Prima devo salvare le modifiche, ma non ci riesco. Riprova tra poco.');
			}
			const id = this.pages.get(page)?.id ?? null;
			if (id === null) {
				return;
			}
			await deletePage(this.api, id);
			this.tags.forget(id);
			this.renumbered([page]);
			try {
				await this.ensure(this.focus);
			} catch (cause) {
				this.notice.show(loadMessage(cause), true);
			}
			this.generation += 1;
		} finally {
			this.busy = false;
		}
	}

	private renumbered(deleted: number[]): void {
		const first = deleted[0] ?? 1;
		this.moveBookmark(deleted);
		const edited: [number, PageState][] = [];
		for (const page of [...this.dirty]) {
			const state = this.pages.get(page);
			if (page < first) {
				continue;
			}
			if (state !== undefined && !deleted.includes(page)) {
				const moved = this.shifted(page, deleted);
				const version = state.id === null ? state.version : state.version + page - moved;
				edited.push([moved, { id: state.id, content: state.content, version }]);
			}
			this.dirty.delete(page);
			this.forget(page);
		}
		for (const page of [...this.pages.keys()]) {
			if (page >= first) {
				this.pages.delete(page);
				this.lastSaved.delete(page);
				this.editors.delete(page);
				this.filled.delete(page);
			}
		}
		for (const [page, rests] of [...this.spilled]) {
			const moved = this.shifted(page, deleted);
			if (moved !== page) {
				this.spilled.delete(page);
				this.spilled.set(moved, [...rests, ...(this.spilled.get(moved) ?? [])]);
			}
		}
		for (const [page, state] of edited) {
			this.pages.set(page, state);
			this.dirty.add(page);
			this.keepDraft(page);
		}
		this.total = Math.max(this.total - deleted.length, 0);
		this.focus = this.landing(this.shifted(this.focus, deleted));
		this.activePage = null;
		if (edited.length > 0) {
			this.schedule();
		}
	}

	private moveBookmark(deleted: number[]): void {
		const bookmark = this.bookmark;
		if (bookmark === null) {
			return;
		}
		this.bookmark = deleted.some((page) => this.pages.get(page)?.id === bookmark.pageId)
			? null
			: { ...bookmark, pageNumber: this.shifted(bookmark.pageNumber, deleted) };
	}

	private landing(page: number): number {
		const clamped = Math.min(Math.max(page, 1), Math.max(this.reach(), 1));
		return this.single ? clamped : leftPageOf(spreadOf(clamped));
	}

	private shifted(page: number, deleted: number[]): number {
		return page - deleted.filter((number) => number < page).length;
	}

	async leave(): Promise<void> {
		if (await this.flush()) {
			await this.prune();
		}
	}

	async ensure(focus: number): Promise<void> {
		const wanted = visiblePages({ single: this.single, focus });
		const missing = wanted.filter((page) => page <= this.total && !this.pages.has(page));
		const spreads = missing
			.map((page) => spreadOf(page))
			.filter((spread, index, all) => all.indexOf(spread) === index);
		for (const spread of spreads) {
			this.store(await this.read(spread));
		}
	}

	private reach(): number {
		let frontier = this.last + (this.ahead ? 1 : 0);
		for (const page of this.pages.keys()) {
			frontier = Math.max(frontier, page);
		}
		return Math.min(frontier, MAX_PAGES);
	}

	private animated(): boolean {
		return this.options.animate();
	}

	completeTurn(): void {
		this.finishTurn?.();
	}

	async turn(direction: TurnDirection, to?: number, saveFirst = true): Promise<boolean> {
		const requested = to ?? nextFocus({ single: this.single, focus: this.focus }, direction);
		const top = this.reach();
		if (this.busy || requested < 1 || requested > top) {
			return false;
		}
		this.busy = true;
		try {
			let destination = requested;
			if (saveFirst) {
				const deleted = await this.settle();
				if (deleted === null || this.disposed) {
					return false;
				}
				if (deleted.length > 0) {
					destination = this.landing(this.shifted(requested, deleted));
					if (destination === this.focus) {
						this.commit(destination);
						return true;
					}
				}
			}
			await this.ensure(destination);
			if (this.disposed) {
				return false;
			}
			if (this.animated()) {
				const snapshot: TurnSnapshot = {
					direction,
					single: this.single,
					from: this.visible.map((page) => this.slot(page)),
					to: visiblePages({ single: this.single, focus: destination }).map((page) =>
						this.slot(page)
					)
				};
				const finished = new Promise<void>((resolve) => {
					const timer = setTimeout(resolve, TURN_FALLBACK);
					this.finishTurn = () => {
						clearTimeout(timer);
						resolve();
					};
				});
				this.turning = snapshot;
				await finished;
				this.finishTurn = null;
				if (this.closed) {
					return false;
				}
			}
			this.commit(destination);
			this.turning = null;
			await tick();
			return true;
		} catch (cause) {
			this.notice.show(loadMessage(cause), true);
			return false;
		} finally {
			this.busy = false;
			this.turning = null;
		}
	}

	private commit(focus: number): void {
		if (this.disposed) {
			return;
		}
		this.focus = focus;
		this.activePage = null;
		this.options.onview(spreadOf(focus));
	}

	async show(spread: number): Promise<void> {
		const focus = leftPageOf(spread);
		if (this.busy || spreadOf(this.focus) === spread) {
			return;
		}
		this.busy = true;
		try {
			const deleted = await this.settle();
			if (deleted !== null && !this.disposed) {
				const target = deleted.length > 0 ? this.landing(this.shifted(focus, deleted)) : focus;
				await this.ensure(target);
				if (this.closed) {
					return;
				}
				this.focus = target;
				this.activePage = null;
				if (target !== focus) {
					this.options.onview(spreadOf(target));
				}
			}
		} catch (cause) {
			this.notice.show(loadMessage(cause), true);
		} finally {
			this.busy = false;
		}
	}

	async previousPage(from: number): Promise<boolean> {
		const target = from - 1;
		if (target < 1) {
			return false;
		}
		let arrived = true;
		this.startCapture();
		if (!this.visible.includes(target)) {
			const focus = this.single ? target : leftPageOf(spreadOf(target));
			arrived = await this.turn('backward', focus);
		}
		const typed = this.takeTyped(true);
		const handle = this.editors.get(arrived ? target : from);
		if (arrived) {
			handle?.focusEnd();
		}
		if (typed.length > 0) {
			handle?.insert(typed);
		}
		return arrived;
	}

	async goTo(page: number): Promise<boolean> {
		if (!Number.isFinite(page)) {
			return false;
		}
		const target = Math.min(Math.max(Math.trunc(page), 1), Math.max(this.last, 1));
		const current = this.visible;
		if (current.includes(target)) {
			this.editors.get(target)?.focus();
			return true;
		}
		const focus = this.single ? target : leftPageOf(spreadOf(target));
		return this.turn(target > (current[0] ?? 1) ? 'forward' : 'backward', focus);
	}

	async spill(from: number, rest: JSONContent, position: number | null): Promise<void> {
		const cursor = position !== null && isBlankRest(rest) ? 1 : position;
		const target = from + 1;
		if (target > MAX_PAGES) {
			return;
		}
		const handle = this.editors.get(target);
		if (handle !== undefined) {
			handle.prepend(rest, cursor);
			return;
		}
		try {
			await this.ensure(target);
		} catch (cause) {
			this.spilled.set(target, [...(this.spilled.get(target) ?? []), rest]);
			this.notice.show(loadMessage(cause), true);
			return;
		}
		if (this.disposed) {
			return;
		}
		const state = this.pages.get(target);
		this.pages.set(target, {
			id: state?.id ?? null,
			content: prependDoc(state?.content ?? emptyDoc(), rest),
			version: state?.version ?? 0
		});
		this.dirty.add(target);
		this.keepDraft(target);
		this.pending.set(target, { cursor });
		this.startCapture();
		this.schedule();
		if (!this.visible.includes(target)) {
			const focus = this.single ? target : leftPageOf(spreadOf(target));
			await this.turn('forward', focus, false);
		}
	}

	private get closed(): boolean {
		return this.disposed;
	}

	get working(): boolean {
		return this.busy;
	}

	get unsaved(): boolean {
		return (
			this.dirty.size > 0 ||
			this.failure !== null ||
			this.saving ||
			this.conflict !== null ||
			this.spilled.size > 0 ||
			this.tags.unsaved
		);
	}

	discard(): void {
		this.discarded = true;
		const pages = new SvelteSet<number>([
			...this.dirty,
			...this.leftovers.keys(),
			...this.draftTimers.keys()
		]);
		for (const page of pages) {
			this.forget(page);
		}
		this.dirty.clear();
		this.leftovers.clear();
		this.spilled.clear();
		this.tags.discard();
		this.failure = null;
		this.conflict = null;
		this.offline = false;
		this.gone = false;
		this.rejected = false;
		this.dispose();
	}

	dispose(): void {
		this.disposed = true;
		this.finishTurn?.();
		clearTimeout(this.saveTimer);
		clearTimeout(this.retryTimer);
		clearTimeout(this.captureTimer);
		for (const timer of this.draftTimers.values()) {
			clearTimeout(timer);
		}
		this.draftTimers.clear();
		this.tags.dispose();
	}
}

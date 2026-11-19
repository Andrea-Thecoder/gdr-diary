import type { JSONContent } from '@tiptap/core';
import type { ApiClient } from '#lib/api/client.js';
import { AUTOSAVE_MAX_WAIT, retryDelay } from '#lib/pdf/autosave.js';
import type { SaveState } from '#lib/book/book.svelte.js';
import type { Draft, DraftStore } from '#lib/book/draft.js';
import { ConflictError, RejectedPageError, sameContent } from '#lib/book/pages.js';
import { fetchBackground, saveBackground, type BackgroundData } from './api.js';
import { textLength } from './text.js';

export const DRAFT_DELAY = 500;
export const DRAFT_PAGE = 1;

export type BackgroundChoice = 'mine' | 'theirs';

export interface BackgroundOptions {
	autoSave: () => boolean;
	saveDelay: () => number;
	drafts: DraftStore;
	maxChars: number;
}

export function draftKey(characterId: string): string {
	return `background:${characterId}`;
}

function isNetworkError(cause: unknown): boolean {
	return cause instanceof TypeError;
}

function failureMessage(cause: unknown, offline: boolean): string {
	if (offline) {
		return 'Non riesco a raggiungere il server.';
	}
	return cause instanceof Error ? cause.message : 'Non riesco a salvare la storia.';
}

export class CharacterBackground {
	content = $state.raw<JSONContent>({});
	version = $state(0);
	generation = $state(0);
	saving = $state(false);
	dirty = $state(false);
	failure = $state<string | null>(null);
	offline = $state(false);
	rejected = $state(false);
	conflict = $state(false);
	restored = $state(false);

	readonly length = $derived(textLength(this.content));
	readonly full = $derived.by(() => this.length >= this.options.maxChars);
	readonly state = $derived.by<SaveState>(() => {
		if (this.conflict) {
			return 'conflict';
		}
		if (this.failure !== null) {
			return this.offline ? 'offline' : 'unsaved';
		}
		if (this.saving) {
			return 'saving';
		}
		if (this.dirty) {
			return this.options.autoSave() ? 'pending' : 'unsaved';
		}
		return 'saved';
	});
	readonly unsaved = $derived(this.dirty || this.saving || this.failure !== null || this.conflict);

	private saved: JSONContent;
	private saveTimer: ReturnType<typeof setTimeout> | undefined;
	private draftTimer: ReturnType<typeof setTimeout> | undefined;
	private retryTimer: ReturnType<typeof setTimeout> | undefined;
	private pendingSince: number | null = null;
	private attempts = 0;
	private chain: Promise<boolean> = Promise.resolve(true);
	private disposed = false;

	constructor(
		private readonly api: ApiClient,
		private readonly characterId: string,
		initial: BackgroundData,
		private readonly options: BackgroundOptions
	) {
		this.content = initial.content;
		this.version = initial.version;
		this.saved = initial.content;
	}

	update(content: JSONContent): void {
		this.content = content;
		this.dirty = !sameContent(content, this.saved);
		this.keepDraft();
		if (this.dirty) {
			this.schedule();
		} else {
			clearTimeout(this.saveTimer);
			this.pendingSince = null;
		}
	}

	restore(drafts: Draft[]): void {
		const found = drafts.find((draft) => draft.page === DRAFT_PAGE);
		if (found === undefined) {
			return;
		}
		if (found.baseVersion !== this.version || sameContent(found.content, this.saved)) {
			void this.options.drafts.remove(draftKey(this.characterId), DRAFT_PAGE).catch(() => {
				return;
			});
			return;
		}
		this.content = found.content;
		this.dirty = true;
		this.restored = true;
		this.generation += 1;
		this.schedule();
	}

	flush(): Promise<boolean> {
		if (this.disposed) {
			return Promise.resolve(true);
		}
		clearTimeout(this.saveTimer);
		this.pendingSince = null;
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
	}

	dispose(): void {
		this.disposed = true;
		clearTimeout(this.saveTimer);
		clearTimeout(this.draftTimer);
		clearTimeout(this.retryTimer);
	}

	async resolveConflict(choice: BackgroundChoice): Promise<boolean> {
		if (!this.conflict) {
			return true;
		}
		let server: BackgroundData;
		try {
			server = await fetchBackground(this.api, this.characterId);
		} catch {
			return false;
		}
		this.version = server.version;
		this.conflict = false;
		if (choice === 'theirs') {
			this.content = server.content;
			this.saved = server.content;
			this.dirty = false;
			this.generation += 1;
			this.dropDraft();
			return true;
		}
		this.saved = server.content;
		this.dirty = !sameContent(this.content, this.saved);
		return this.flush();
	}

	private schedule(): void {
		clearTimeout(this.saveTimer);
		if (!this.options.autoSave() || this.conflict || this.disposed) {
			return;
		}
		const now = Date.now();
		this.pendingSince ??= now;
		const delay = this.options.saveDelay();
		const remaining = this.pendingSince + Math.max(AUTOSAVE_MAX_WAIT, delay) - now;
		this.saveTimer = setTimeout(
			() => {
				void this.flush();
			},
			Math.max(0, Math.min(delay, remaining))
		);
	}

	private keepDraft(): void {
		clearTimeout(this.draftTimer);
		if (this.disposed) {
			return;
		}
		if (!this.dirty) {
			this.dropDraft();
			return;
		}
		this.draftTimer = setTimeout(() => {
			void this.options.drafts
				.put({
					diaryId: draftKey(this.characterId),
					page: DRAFT_PAGE,
					pageId: null,
					baseVersion: this.version,
					content: this.content,
					savedAt: Date.now()
				})
				.catch(() => {
					return;
				});
		}, DRAFT_DELAY);
	}

	private dropDraft(): void {
		clearTimeout(this.draftTimer);
		void this.options.drafts.remove(draftKey(this.characterId), DRAFT_PAGE).catch(() => {
			return;
		});
	}

	private resume(): void {
		this.failure = null;
		this.rejected = false;
		void this.flush();
	}

	private scheduleRetry(): void {
		if (this.disposed) {
			return;
		}
		clearTimeout(this.retryTimer);
		const delay = retryDelay(this.attempts);
		this.attempts += 1;
		this.retryTimer = setTimeout(() => {
			this.resume();
		}, delay);
	}

	private async round(): Promise<boolean> {
		clearTimeout(this.retryTimer);
		if (this.conflict) {
			return false;
		}
		this.saving = true;
		try {
			while (this.dirty) {
				const content = this.content;
				if (!(await this.saveOnce(content))) {
					return false;
				}
			}
			this.failure = null;
			this.offline = false;
			this.rejected = false;
			this.attempts = 0;
			return true;
		} finally {
			this.saving = false;
		}
	}

	private async saveOnce(content: JSONContent): Promise<boolean> {
		try {
			const saved = await saveBackground(this.api, this.characterId, content, this.version);
			this.version = saved.version;
			this.saved = content;
			if (this.content === content) {
				this.dirty = false;
				this.dropDraft();
			}
			return true;
		} catch (cause) {
			if (this.disposed) {
				return false;
			}
			if (cause instanceof ConflictError) {
				this.conflict = true;
				return false;
			}
			this.offline = isNetworkError(cause);
			this.rejected = cause instanceof RejectedPageError;
			this.failure = failureMessage(cause, this.offline);
			if (!this.rejected) {
				this.scheduleRetry();
			}
			return false;
		}
	}
}

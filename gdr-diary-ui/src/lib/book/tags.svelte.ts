import { SvelteMap } from 'svelte/reactivity';
import type { ApiClient } from '#lib/api/client.js';
import { PagedList, type PageResult } from '#lib/list/paged-list.svelte.js';
import { MissingPageError } from './pages.js';
import {
	SUGGESTION_SIZE,
	TAG_LIST_SIZE,
	checkTag,
	cleanTag,
	fetchDiaryTags,
	updatePageTag,
	type DiaryTag
} from './tags.js';

export const SUGGEST_DELAY = 250;

interface TagEntry {
	tag: string | null;
	saved: string | null;
	pending: boolean;
	saving: boolean;
	gone: boolean;
	error: string | null;
}

function failureMessage(cause: unknown): string {
	if (cause instanceof TypeError) {
		return 'Non riesco a raggiungere il server.';
	}
	return cause instanceof Error ? cause.message : 'Non riesco a salvare il tag.';
}

export class PageTagStore {
	suggestions = $state<string[]>([]);
	revision = $state(0);

	readonly entries = new SvelteMap<string, TagEntry>();

	private suggestTimer: ReturnType<typeof setTimeout> | undefined;
	private chain: Promise<boolean> = Promise.resolve(true);
	private ticket = 0;

	constructor(
		private readonly api: ApiClient,
		private readonly diaryId: string
	) {}

	get unsaved(): boolean {
		return [...this.entries.values()].some((entry) => entry.pending && !entry.gone);
	}

	tagOf(id: string): string | null {
		return this.entries.get(id)?.tag ?? null;
	}

	errorOf(id: string): string | null {
		return this.entries.get(id)?.error ?? null;
	}

	savingOf(id: string): boolean {
		return this.entries.get(id)?.saving ?? false;
	}

	pendingOf(id: string): boolean {
		const entry = this.entries.get(id);
		return entry !== undefined && entry.pending && !entry.gone;
	}

	goneOf(id: string): boolean {
		return this.entries.get(id)?.gone ?? false;
	}

	seed(id: string, tag: string | null): void {
		const entry = this.entries.get(id);
		if (entry !== undefined && (entry.pending || entry.saving)) {
			return;
		}
		this.entries.set(id, {
			tag,
			saved: tag,
			pending: false,
			saving: false,
			gone: entry?.gone ?? false,
			error: entry?.gone === true ? entry.error : null
		});
	}

	set(id: string, text: string): boolean {
		const entry = this.entries.get(id);
		if (entry === undefined || entry.gone) {
			return false;
		}
		const problem = checkTag(text);
		if (problem !== null) {
			this.entries.set(id, { ...entry, error: problem });
			return false;
		}
		const tag = cleanTag(text);
		if (tag !== entry.tag) {
			this.entries.set(id, { ...entry, tag, pending: true, error: null });
			void this.flush();
		}
		return true;
	}

	clear(id: string): void {
		const entry = this.entries.get(id);
		if (entry === undefined || entry.gone || entry.tag === null) {
			return;
		}
		this.entries.set(id, { ...entry, tag: null, pending: true, error: null });
		void this.flush();
	}

	forget(id: string): void {
		this.entries.delete(id);
	}

	flush(): Promise<boolean> {
		this.chain = this.chain.then(() => this.round());
		return this.chain;
	}

	suggest(text: string, taken: readonly string[]): void {
		clearTimeout(this.suggestTimer);
		this.ticket += 1;
		if (text.trim() === '') {
			this.suggestions = [];
			return;
		}
		this.suggestTimer = setTimeout(() => {
			void this.lookup(text, taken);
		}, SUGGEST_DELAY);
	}

	async lookup(text: string, taken: readonly string[]): Promise<void> {
		const ticket = ++this.ticket;
		try {
			const result = await fetchDiaryTags(this.api, this.diaryId, {
				name: text,
				page: 1,
				size: SUGGESTION_SIZE
			});
			if (ticket === this.ticket) {
				this.suggestions = result.items
					.map((tag) => tag.name)
					.filter((name) => !taken.some((tag) => tag.toLowerCase() === name.toLowerCase()));
			}
		} catch {
			if (ticket === this.ticket) {
				this.suggestions = [];
			}
		}
	}

	clearSuggestions(): void {
		clearTimeout(this.suggestTimer);
		this.ticket += 1;
		this.suggestions = [];
	}

	discard(): void {
		this.dispose();
		this.entries.clear();
	}

	dispose(): void {
		clearTimeout(this.suggestTimer);
	}

	private async round(): Promise<boolean> {
		let ok = true;
		for (const [id, entry] of [...this.entries]) {
			if (entry.pending && !entry.gone && !entry.saving) {
				ok = (await this.saveOne(id)) && ok;
			}
		}
		return ok;
	}

	private async saveOne(id: string): Promise<boolean> {
		const entry = this.entries.get(id);
		if (entry === undefined) {
			return true;
		}
		const sent = entry.tag;
		this.entries.set(id, { ...entry, saving: true, error: null });
		try {
			const saved = await updatePageTag(this.api, id, sent);
			const current = this.entries.get(id);
			if (current === undefined) {
				return true;
			}
			const unchanged = current.tag === sent;
			this.entries.set(id, {
				...current,
				tag: unchanged ? saved : current.tag,
				saved,
				pending: !unchanged,
				saving: false,
				error: null
			});
			this.revision += 1;
			return true;
		} catch (cause) {
			const current = this.entries.get(id);
			if (current !== undefined) {
				const gone = cause instanceof MissingPageError;
				this.entries.set(id, {
					...current,
					saving: false,
					gone,
					pending: !gone,
					error: failureMessage(cause)
				});
			}
			return false;
		}
	}
}

export class DiaryTagList extends PagedList<DiaryTag> {
	protected readonly failure = 'Non riesco a caricare i tag del diario.';

	constructor(
		private readonly api: ApiClient,
		private readonly diaryId: string
	) {
		super();
	}

	protected load(page: number): Promise<PageResult<DiaryTag>> {
		return fetchDiaryTags(this.api, this.diaryId, { name: '', page, size: TAG_LIST_SIZE });
	}
}

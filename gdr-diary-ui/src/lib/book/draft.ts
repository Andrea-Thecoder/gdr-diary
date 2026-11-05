import type { JSONContent } from '@tiptap/core';
import { hasText } from './pages.js';

export interface Draft {
	diaryId: string;
	page: number;
	pageId: string | null;
	baseVersion: number;
	content: JSONContent;
	savedAt: number;
}

export interface DraftStore {
	list: (diaryId: string) => Promise<Draft[]>;
	put: (draft: Draft) => Promise<void>;
	remove: (diaryId: string, page: number) => Promise<void>;
}

export interface ServerPage {
	id: string | null;
	content: JSONContent;
}

export type DraftVerdict = 'apply' | 'drop' | 'wait' | 'search';

export function judgeDraft(
	draft: Draft,
	server: ServerPage | undefined,
	total: number
): DraftVerdict {
	if (server !== undefined) {
		if (server.id !== draft.pageId) {
			return draft.pageId === null ? 'drop' : 'search';
		}
		return 'drop';
	}
	if (draft.pageId === null) {
		return draft.page === total + 1 && hasText(draft.content) ? 'apply' : 'drop';
	}
	return draft.page <= total ? 'wait' : 'search';
}

function keyOf(diaryId: string, page: number): string {
	return `${diaryId}:${String(page)}`;
}

export function createMemoryDraftStore(): DraftStore {
	const rows = new Map<string, Draft>();
	return {
		list: (diaryId) =>
			Promise.resolve([...rows.values()].filter((draft) => draft.diaryId === diaryId)),
		put: (draft) => {
			rows.set(keyOf(draft.diaryId, draft.page), draft);
			return Promise.resolve();
		},
		remove: (diaryId, page) => {
			rows.delete(keyOf(diaryId, page));
			return Promise.resolve();
		}
	};
}

const DATABASE = 'gdr-diary-drafts';
const STORE = 'drafts';
const BY_DIARY = 'diaryId';

let connection: Promise<IDBDatabase> | undefined;

function connect(): Promise<IDBDatabase> {
	connection ??= new Promise<IDBDatabase>((resolve, reject) => {
		const request = indexedDB.open(DATABASE, 1);
		request.onupgradeneeded = () => {
			const store = request.result.createObjectStore(STORE, { keyPath: 'key' });
			store.createIndex(BY_DIARY, 'diaryId');
		};
		request.onsuccess = () => {
			const database = request.result;
			database.onclose = () => {
				connection = undefined;
			};
			database.onversionchange = () => {
				database.close();
				connection = undefined;
			};
			resolve(database);
		};
		request.onerror = () => {
			connection = undefined;
			reject(request.error ?? new Error('IndexedDB non disponibile'));
		};
	});
	return connection;
}

function requested<T>(request: IDBRequest<T>): Promise<T> {
	return new Promise<T>((resolve, reject) => {
		request.onsuccess = () => {
			resolve(request.result);
		};
		request.onerror = () => {
			reject(request.error ?? new Error('Lettura della bozza non riuscita'));
		};
	});
}

function completed(transaction: IDBTransaction): Promise<void> {
	return new Promise<void>((resolve, reject) => {
		transaction.oncomplete = () => {
			resolve();
		};
		transaction.onerror = () => {
			reject(transaction.error ?? new Error('Salvataggio della bozza non riuscito'));
		};
		transaction.onabort = () => {
			reject(transaction.error ?? new Error('Salvataggio della bozza annullato'));
		};
	});
}

function isDraft(value: unknown): value is Draft {
	if (typeof value !== 'object' || value === null) {
		return false;
	}
	const row = value as Record<string, unknown>;
	return (
		typeof row['diaryId'] === 'string' &&
		typeof row['page'] === 'number' &&
		(row['pageId'] === null || typeof row['pageId'] === 'string') &&
		typeof row['baseVersion'] === 'number' &&
		typeof row['content'] === 'object' &&
		row['content'] !== null &&
		typeof row['savedAt'] === 'number'
	);
}

function withoutKey(row: Draft): Draft {
	return {
		diaryId: row.diaryId,
		page: row.page,
		pageId: row.pageId,
		baseVersion: row.baseVersion,
		content: row.content,
		savedAt: row.savedAt
	};
}

export function createDraftStore(): DraftStore {
	if (typeof indexedDB === 'undefined') {
		return createMemoryDraftStore();
	}
	return {
		list: async (diaryId) => {
			const database = await connect();
			const rows: unknown[] = await requested(
				database.transaction(STORE).objectStore(STORE).index(BY_DIARY).getAll(diaryId)
			);
			return rows.filter(isDraft).map(withoutKey);
		},
		put: async (draft) => {
			const database = await connect();
			const transaction = database.transaction(STORE, 'readwrite');
			transaction.objectStore(STORE).put({ ...draft, key: keyOf(draft.diaryId, draft.page) });
			await completed(transaction);
		},
		remove: async (diaryId, page) => {
			const database = await connect();
			const transaction = database.transaction(STORE, 'readwrite');
			transaction.objectStore(STORE).delete(keyOf(diaryId, page));
			await completed(transaction);
		}
	};
}

export async function loadDrafts(
	diaryId: string,
	store: DraftStore = createDraftStore()
): Promise<Draft[]> {
	try {
		return await store.list(diaryId);
	} catch {
		return [];
	}
}

export async function clearAllDrafts(): Promise<void> {
	if (typeof indexedDB === 'undefined') {
		return;
	}
	try {
		const database = await connect();
		const transaction = database.transaction(STORE, 'readwrite');
		transaction.objectStore(STORE).clear();
		await completed(transaction);
	} catch {
		return;
	}
}

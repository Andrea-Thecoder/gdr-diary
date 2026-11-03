export type ListStatus = 'loading' | 'ready' | 'error';

export const FILTER_MAX_LENGTH = 255;

export interface PageResult<T> {
	items: T[];
	totalRows: number;
	totalPages: number;
	page: number;
}

export abstract class PagedList<T> {
	items = $state<T[]>([]);
	totalRows = $state(0);
	status = $state<ListStatus>('loading');
	loadingMore = $state(false);
	error = $state<string | null>(null);
	descending = $state(false);

	private page = $state(1);
	private totalPages = $state(1);
	private ticket = 0;

	readonly hasMore = $derived(this.page < this.totalPages);

	protected abstract readonly failure: string;

	protected abstract load(page: number): Promise<PageResult<T>>;

	async reload(): Promise<void> {
		const ticket = ++this.ticket;
		this.loadingMore = false;
		this.status = 'loading';
		this.error = null;
		try {
			const result = await this.load(1);
			if (ticket !== this.ticket) {
				return;
			}
			this.items = result.items;
			this.totalRows = result.totalRows;
			this.page = 1;
			this.totalPages = result.totalPages;
			this.status = 'ready';
		} catch (cause) {
			if (ticket !== this.ticket) {
				return;
			}
			this.error = cause instanceof Error ? cause.message : this.failure;
			this.status = 'error';
		}
	}

	async loadMore(): Promise<void> {
		if (!this.hasMore || this.loadingMore) {
			return;
		}
		const ticket = ++this.ticket;
		this.loadingMore = true;
		this.error = null;
		try {
			const result = await this.load(this.page + 1);
			if (ticket !== this.ticket) {
				return;
			}
			this.items = [...this.items, ...result.items];
			this.totalRows = result.totalRows;
			this.page = result.page;
			this.totalPages = result.totalPages;
		} catch (cause) {
			if (ticket !== this.ticket) {
				return;
			}
			this.error = cause instanceof Error ? cause.message : this.failure;
		} finally {
			if (ticket === this.ticket) {
				this.loadingMore = false;
			}
		}
	}

	toggleOrder(): void {
		this.descending = !this.descending;
	}
}

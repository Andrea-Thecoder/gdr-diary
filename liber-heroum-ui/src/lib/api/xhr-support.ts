/** Fake `XMLHttpRequest` for the tests of the uploads with progress. */
type Listener = (() => void) | null;

let waiting: ((xhr: FakeXhr) => void) | undefined;

export class FakeXhr {
	method = '';
	url = '';
	responseType = '';
	headers: Record<string, string> = {};
	sent: unknown = undefined;
	status = 0;
	statusText = '';
	response: Blob | null = null;
	responseHeaders = '';
	upload: {
		onprogress:
			((event: { lengthComputable: boolean; loaded: number; total: number }) => void) | null;
	} = { onprogress: null };
	onload: Listener = null;
	onerror: Listener = null;
	ontimeout: Listener = null;
	onabort: Listener = null;

	/** Resolves with the next request that is sent. Call it before starting the upload. */
	static nextSent(): Promise<FakeXhr> {
		return new Promise((resolve) => {
			waiting = resolve;
		});
	}

	open(method: string, url: string): void {
		this.method = method;
		this.url = url;
	}

	setRequestHeader(name: string, value: string): void {
		this.headers[name.toLowerCase()] = value;
	}

	send(body: unknown): void {
		this.sent = body;
		waiting?.(this);
		waiting = undefined;
	}

	abort(): void {
		this.onabort?.();
	}

	getAllResponseHeaders(): string {
		return this.responseHeaders;
	}

	progress(loaded: number, total: number): void {
		this.upload.onprogress?.({ lengthComputable: true, loaded, total });
	}

	finish(status: number, body: string | null, headers = 'Content-Type: application/json'): void {
		this.status = status;
		this.statusText = status === 200 || status === 201 ? 'OK' : 'Error';
		this.response = body === null ? null : new Blob([body]);
		this.responseHeaders = headers;
		this.onload?.();
	}

	fail(): void {
		this.onerror?.();
	}
}

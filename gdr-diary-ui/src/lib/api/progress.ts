import { NetworkError } from '#lib/api/client.js';

export type UploadProgress = (fraction: number) => void;

const EMPTY_BODY = new Set([101, 204, 205, 304]);

function readHeaders(raw: string): Headers {
	const headers = new Headers();
	for (const line of raw.trim().split(/[\r\n]+/)) {
		const colon = line.indexOf(':');
		if (colon > 0) {
			headers.append(line.slice(0, colon).trim(), line.slice(colon + 1).trim());
		}
	}
	return headers;
}

/**
 * `fetch` cannot tell how much of a request body has been sent, `XMLHttpRequest` can. This is
 * the `fetch` to hand to a single upload call (openapi-fetch accepts one per call) when the
 * screen wants a progress bar. `onProgress` receives a number from 0 to 1.
 */
export function fetchWithProgress(
	onProgress: UploadProgress,
	create: () => XMLHttpRequest = () => new XMLHttpRequest()
): (request: Request) => Promise<Response> {
	return async (request) => {
		const body = await request.blob();
		return new Promise<Response>((resolve, reject) => {
			const xhr = create();
			xhr.open(request.method, request.url);
			xhr.responseType = 'blob';
			request.headers.forEach((value, name) => {
				xhr.setRequestHeader(name, value);
			});
			xhr.upload.onprogress = (event) => {
				onProgress(event.lengthComputable && event.total > 0 ? event.loaded / event.total : 0);
			};
			xhr.onload = () => {
				onProgress(1);
				resolve(
					new Response(EMPTY_BODY.has(xhr.status) ? null : (xhr.response as Blob), {
						status: xhr.status,
						statusText: xhr.statusText,
						headers: readHeaders(xhr.getAllResponseHeaders())
					})
				);
			};
			const failed = (): void => {
				reject(new NetworkError(new TypeError('Network request failed')));
			};
			xhr.onerror = failed;
			xhr.ontimeout = failed;
			xhr.onabort = () => {
				reject(new DOMException('The request was aborted', 'AbortError'));
			};
			request.signal.addEventListener(
				'abort',
				() => {
					xhr.abort();
				},
				{ once: true }
			);
			xhr.send(body);
		});
	};
}

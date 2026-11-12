import { describe, expect, it, vi, type Mock } from 'vitest';
import { NetworkError } from '#lib/api/client.js';
import { fetchWithProgress } from '#lib/api/progress.js';
import { FakeXhr } from '#lib/api/xhr-support.js';

interface Started {
	xhr: FakeXhr;
	sent: Promise<FakeXhr>;
	response: Promise<Response>;
	onProgress: Mock<(fraction: number) => void>;
}

function start(onProgress = vi.fn<(fraction: number) => void>(), signal?: AbortSignal): Started {
	const xhr = new FakeXhr();
	const send = fetchWithProgress(onProgress, () => xhr as unknown as XMLHttpRequest);
	const sent = FakeXhr.nextSent();
	const response = send(
		new Request('http://api.test/base/upload', {
			method: 'POST',
			body: 'contenuto',
			headers: { Authorization: 'Bearer abc', 'Content-Type': 'text/plain' },
			...(signal === undefined ? {} : { signal })
		})
	);
	return { xhr, sent, response, onProgress };
}

describe('fetchWithProgress', () => {
	it('sends the request as it is and reports how much has been sent', async () => {
		const { xhr, sent, response, onProgress } = start();

		await sent;
		xhr.progress(25, 100);
		xhr.progress(100, 100);
		xhr.finish(201, '{"id":"s1"}');

		expect(xhr.method).toBe('POST');
		expect(xhr.url).toBe('http://api.test/base/upload');
		expect(xhr.headers['authorization']).toBe('Bearer abc');
		expect(await (xhr.sent as Blob).text()).toBe('contenuto');
		expect(onProgress.mock.calls.map((call) => call[0])).toEqual([0.25, 1, 1]);
		const result = await response;
		expect(result.status).toBe(201);
		expect(result.headers.get('Content-Type')).toBe('application/json');
		expect(await result.json()).toEqual({ id: 's1' });
	});

	it('answers without a body when the server sends none', async () => {
		const { xhr, sent, response } = start();

		await sent;
		xhr.finish(204, null, '');

		const result = await response;
		expect(result.status).toBe(204);
		expect(await result.text()).toBe('');
	});

	it('reports a network error when the request cannot reach the server', async () => {
		const { xhr, sent, response } = start();

		await sent;
		xhr.fail();

		await expect(response).rejects.toBeInstanceOf(NetworkError);
	});

	it('stops the upload when the request is aborted', async () => {
		const controller = new AbortController();
		const { sent, response } = start(undefined, controller.signal);

		await sent;
		controller.abort();

		await expect(response).rejects.toMatchObject({ name: 'AbortError' });
	});
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { api } = vi.hoisted(() => ({
	api: { POST: vi.fn<(...args: unknown[]) => Promise<unknown>>() }
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/env.js', () => ({ getEnv: () => ({ VITE_API_BASE_URL: 'http://api.test/api' }) }));

import { ensureAppUser, syncAppUser } from '#lib/api/me.js';

async function freshEnsureAppUser(): Promise<() => Promise<string>> {
	vi.resetModules();
	return (await import('#lib/api/me.js')).ensureAppUser;
}

describe('app user', () => {
	beforeEach(() => {
		api.POST.mockReset();
	});

	afterEach(() => {
		vi.unstubAllGlobals();
		vi.restoreAllMocks();
	});

	it('syncs again with POST /me even after the user was ensured', async () => {
		api.POST.mockResolvedValue({ data: { payload: 'id-1' } });
		await ensureAppUser();
		expect(api.POST).toHaveBeenCalledTimes(1);

		await expect(syncAppUser()).resolves.toBe('id-1');

		expect(api.POST).toHaveBeenCalledTimes(2);
		expect(api.POST).toHaveBeenLastCalledWith('/me');
	});

	it('throws the server message when the sync fails', async () => {
		api.POST.mockResolvedValue({
			error: { title: 'Down', status: 400 },
			response: { status: 400 }
		});

		await expect(syncAppUser()).rejects.toThrow('Down (errore 400)');
	});

	it('says which status the server answered when it sends no message', async () => {
		api.POST.mockResolvedValue({ response: { status: 401 } });

		await expect(syncAppUser()).rejects.toThrow(
			'Impossibile registrare l’utente sul server. (errore 401)'
		);
	});

	it('says nobody answers when the network fails and the server cannot be reached', async () => {
		api.POST.mockRejectedValue(new TypeError('Failed to fetch'));
		const probe = vi.fn(() => Promise.reject(new TypeError('Failed to fetch')));
		vi.stubGlobal('fetch', probe);

		await expect(syncAppUser()).rejects.toThrow(
			'Non riesco a raggiungere il server. Riprova tra poco.'
		);
		expect(probe).toHaveBeenCalledWith('http://api.test/api', {
			mode: 'no-cors',
			cache: 'no-store'
		});
	});

	it('shows no developer hint to the user when the server is there', async () => {
		api.POST.mockRejectedValue(new TypeError('Failed to fetch'));
		vi.stubGlobal(
			'fetch',
			vi.fn(() => Promise.resolve(new Response(null)))
		);
		vi.spyOn(console, 'warn').mockImplementation(() => undefined);

		const failure = await syncAppUser().catch((cause: unknown) => cause);

		expect(failure).toHaveProperty(
			'message',
			'Non riesco a raggiungere il server. Riprova tra poco.'
		);
	});

	it('keeps the CORS hint for developers in the console', async () => {
		api.POST.mockRejectedValue(new TypeError('Failed to fetch'));
		vi.stubGlobal(
			'fetch',
			vi.fn(() => Promise.resolve(new Response(null)))
		);
		const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

		await syncAppUser().catch(() => undefined);

		expect(warn).toHaveBeenCalledWith(expect.stringContaining('app.cors.allowed-origins'));
	});

	it('lets other failures through untouched', async () => {
		api.POST.mockRejectedValue(new Error('Session expired'));

		await expect(syncAppUser()).rejects.toThrow('Session expired');
	});
});

describe('ensureAppUser', () => {
	beforeEach(() => {
		api.POST.mockReset();
	});

	it('posts to /me once however many callers ask', async () => {
		api.POST.mockResolvedValue({ data: { payload: 'id-1' } });
		const ensure = await freshEnsureAppUser();

		await Promise.all([ensure(), ensure(), ensure()]);

		expect(api.POST).toHaveBeenCalledOnce();
	});

	it('gives every caller the same id', async () => {
		api.POST.mockResolvedValue({ data: { payload: 'id-1' } });
		const ensure = await freshEnsureAppUser();

		const ids = await Promise.all([ensure(), ensure()]);

		expect(ids).toEqual(['id-1', 'id-1']);
	});

	it('tries again on the next call after a failure', async () => {
		api.POST.mockRejectedValueOnce(new Error('Session expired'));
		api.POST.mockResolvedValue({ data: { payload: 'id-2' } });
		const ensure = await freshEnsureAppUser();
		await ensure().catch(() => undefined);

		const id = await ensure();

		expect(id).toBe('id-2');
		expect(api.POST).toHaveBeenCalledTimes(2);
	});
});

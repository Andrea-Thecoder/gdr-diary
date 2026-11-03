import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { api } = vi.hoisted(() => ({
	api: { GET: vi.fn<(...args: unknown[]) => Promise<unknown>>() }
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));

function storedTheme(): string | null {
	return /(?:^|; )gdr-theme=([^;]*)/.exec(document.cookie)?.[1] ?? null;
}

import { profileAvatar } from '#lib/profile/avatar.svelte.js';
import { AccountSync } from '#lib/settings/account.js';

function freshSync(): () => Promise<void> {
	const sync = new AccountSync();
	return () => sync.run();
}

describe('AccountSync', () => {
	beforeEach(() => {
		api.GET.mockReset();
		profileAvatar.url = null;
		document.cookie = 'gdr-theme=; Path=/; Max-Age=0';
	});

	afterEach(() => {
		document.cookie = 'gdr-theme=; Path=/; Max-Age=0';
		delete document.documentElement.dataset['theme'];
	});

	it('applies the theme saved on the account', async () => {
		api.GET.mockResolvedValue({ data: { settings: { theme: 'viola' } } });
		const sync = freshSync();

		await sync();

		expect(storedTheme()).toBe('viola');
		expect(document.documentElement.dataset['theme']).toBe('viola');
	});

	it('keeps the local theme when the account has none', async () => {
		document.cookie = 'gdr-theme=pergamena; Path=/';
		api.GET.mockResolvedValue({ data: { settings: null } });
		const sync = freshSync();

		await sync();

		expect(storedTheme()).toBe('pergamena');
	});

	it('asks the server only once after a success', async () => {
		api.GET.mockResolvedValue({ data: { settings: { theme: 'viola' } } });
		const sync = freshSync();

		await sync();
		await sync();

		expect(api.GET).toHaveBeenCalledTimes(1);
	});

	it('does not fail and tries again later when the server is down', async () => {
		api.GET.mockResolvedValueOnce({ error: { title: 'Down', status: 500 } });
		api.GET.mockResolvedValueOnce({ data: { settings: { theme: 'viola' } } });
		const sync = freshSync();

		await expect(sync()).resolves.toBeUndefined();
		expect(storedTheme()).toBeNull();

		await sync();
		expect(storedTheme()).toBe('viola');
	});
	it('loads the profile image of the account', async () => {
		api.GET.mockResolvedValue({ data: { settings: {}, avatarUrl: 'http://img.test/a.png' } });
		const sync = freshSync();

		await sync();

		expect(profileAvatar.url).toBe('http://img.test/a.png');
	});

	it('has no profile image when the account has none', async () => {
		profileAvatar.url = 'http://img.test/old.png';
		api.GET.mockResolvedValue({ data: { settings: {}, avatarUrl: null } });
		const sync = freshSync();

		await sync();

		expect(profileAvatar.url).toBeNull();
	});
});

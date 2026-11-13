import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { auth, rememberNotice, assign } = vi.hoisted(() => ({
	auth: { forgetDeletedAccount: vi.fn<() => Promise<void>>() },
	rememberNotice: vi.fn<(message: string) => void>(),
	assign: vi.fn<(url: string) => void>()
}));

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('#lib/ui/flash-error.js', () => ({ rememberNotice }));

import { ACCOUNT_DELETED, closeAccount } from './account.js';

describe('account closing', () => {
	beforeEach(() => {
		auth.forgetDeletedAccount.mockReset();
		auth.forgetDeletedAccount.mockResolvedValue();
		rememberNotice.mockReset();
		assign.mockReset();
		vi.stubGlobal('window', { location: { origin: 'http://app.test', assign } });
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('forgets the session of the deleted account', async () => {
		await closeAccount();

		expect(auth.forgetDeletedAccount).toHaveBeenCalledOnce();
	});

	it('leaves a message for the home page', async () => {
		await closeAccount();

		expect(rememberNotice).toHaveBeenCalledWith(ACCOUNT_DELETED);
	});

	it('goes back to the home without passing through Keycloak', async () => {
		await closeAccount();

		expect(assign).toHaveBeenCalledWith('http://app.test');
	});

	it('goes home only after the session was forgotten', async () => {
		const order: string[] = [];
		auth.forgetDeletedAccount.mockImplementation(() => {
			order.push('forget');
			return Promise.resolve();
		});
		assign.mockImplementation(() => {
			order.push('home');
		});

		await closeAccount();

		expect(order).toEqual(['forget', 'home']);
	});
});

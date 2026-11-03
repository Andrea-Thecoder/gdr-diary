import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { auth } = vi.hoisted(() => ({
	auth: {
		closeAccount: vi.fn<(returnTo: string) => Promise<void>>(),
		takeActionResult: vi.fn<(action?: string) => Promise<unknown>>()
	}
}));

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));

import { closeAccount, closingWasInterrupted } from './account.js';

describe('account closing', () => {
	beforeEach(() => {
		auth.closeAccount.mockReset();
		auth.closeAccount.mockResolvedValue();
		auth.takeActionResult.mockReset();
		vi.stubGlobal('window', { location: { origin: 'http://app.test' } });
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('comes back to the home after the Keycloak page', async () => {
		await closeAccount();

		expect(auth.closeAccount).toHaveBeenCalledWith('http://app.test');
	});

	it('is interrupted when Keycloak sends the user back with a result for the deletion', async () => {
		auth.takeActionResult.mockResolvedValue({ status: 'cancelled', action: 'delete_account' });

		expect(await closingWasInterrupted()).toBe(true);
		expect(auth.takeActionResult).toHaveBeenCalledWith('delete_account');
	});

	it('is not interrupted when there is nothing to tell', async () => {
		auth.takeActionResult.mockResolvedValue(undefined);

		expect(await closingWasInterrupted()).toBe(false);
	});
});

import { beforeEach, describe, expect, it, vi } from 'vitest';

const { auth, ensureAppUser, run, remember, recall } = vi.hoisted(() => ({
	auth: {
		status: 'authenticated',
		user: null as { roles: string[] } | null,
		init: () => Promise.resolve(),
		login: vi.fn(() => Promise.resolve()),
		logout: vi.fn<(options: { keepDrafts?: boolean }) => Promise<void>>(() => Promise.resolve())
	},
	ensureAppUser: vi.fn(() => Promise.resolve('id')),
	run: vi.fn(() => Promise.resolve()),
	remember: vi.fn<(message: string) => void>(),
	recall: vi.fn<() => string | null>(() => null)
}));

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('#lib/api/me.js', () => ({ ensureAppUser }));
vi.mock('#lib/settings/account.js', () => ({ accountSync: { run } }));
vi.mock('#lib/ui/flash-error.js', () => ({ rememberError: remember, recallError: recall }));
vi.mock('$app/paths', () => ({ resolve: (path: string) => path }));

import { extractRoles } from '#lib/auth/roles.js';
import { load } from './+layout.js';

const runLoad = (): Promise<unknown> => (load as unknown as () => Promise<unknown>)();

const PENDING = Symbol('pending');

function settled(): Promise<unknown> {
	return Promise.race([
		runLoad(),
		new Promise((resolve) => {
			setTimeout(() => {
				resolve(PENDING);
			}, 30);
		})
	]);
}

describe('app layout load', () => {
	beforeEach(() => {
		auth.status = 'authenticated';
		auth.user = { roles: ['user'] };
		auth.login.mockClear();
		auth.logout.mockReset();
		auth.logout.mockResolvedValue();
		ensureAppUser.mockReset();
		ensureAppUser.mockResolvedValue('id');
		run.mockClear();
		remember.mockClear();
		recall.mockClear();
	});

	it('lets a registered user in', async () => {
		await expect(runLoad()).resolves.toEqual({});
		expect(auth.logout).not.toHaveBeenCalled();
		expect(remember).not.toHaveBeenCalled();
		expect(run).toHaveBeenCalledOnce();
	});

	it('signs the user out with the message of the server when the server does not answer', async () => {
		ensureAppUser.mockRejectedValue(new Error('Il server non risponde.'));

		await expect(settled()).resolves.toBe(PENDING);

		expect(remember).toHaveBeenCalledWith('Il server non risponde. Sei stato disconnesso.');
		expect(auth.logout).toHaveBeenCalledWith({ keepDrafts: true });
		expect(run).not.toHaveBeenCalled();
	});

	it('keeps the code of the server error in the message', async () => {
		ensureAppUser.mockRejectedValue(new Error('Authentication required (errore 401)'));

		await expect(settled()).resolves.toBe(PENDING);

		expect(remember).toHaveBeenCalledWith(
			'Authentication required (errore 401). Sei stato disconnesso.'
		);
	});

	it('signs the user out when the account has no permissions', async () => {
		auth.user = { roles: [] };

		await expect(settled()).resolves.toBe(PENDING);

		expect(remember).toHaveBeenCalledWith(expect.stringContaining('permessi'));
		expect(auth.logout).toHaveBeenCalledWith({ keepDrafts: true });
		expect(ensureAppUser).not.toHaveBeenCalled();
	});

	it('shows the error page, without a message left behind, if the sign out itself fails', async () => {
		ensureAppUser.mockRejectedValue(new Error('Il server non risponde.'));
		auth.logout.mockRejectedValue(new Error('Keycloak not initialised'));

		await expect(runLoad()).rejects.toMatchObject({
			status: 502,
			body: { message: 'Il server non risponde. Sei stato disconnesso.' }
		});
		expect(recall).toHaveBeenCalledOnce();
	});

	it('sends the visitor back to the home when the login service is down', async () => {
		auth.status = 'error';

		await expect(runLoad()).rejects.toMatchObject({ status: 307, location: '/' });
		expect(auth.logout).not.toHaveBeenCalled();
		expect(remember).not.toHaveBeenCalled();
	});

	it('sends an anonymous visitor to the login and never lets the load finish', async () => {
		auth.status = 'anonymous';
		auth.user = null;

		await expect(settled()).resolves.toBe(PENDING);

		expect(auth.login).toHaveBeenCalledOnce();
		expect(ensureAppUser).not.toHaveBeenCalled();
	});

	it('sends the visitor to the login while the status is still loading', async () => {
		auth.status = 'loading';
		auth.user = null;

		await expect(settled()).resolves.toBe(PENDING);

		expect(auth.login).toHaveBeenCalledOnce();
	});

	it('lets an administrator in even without the user role', async () => {
		auth.user = { roles: ['admin'] };

		await expect(runLoad()).resolves.toEqual({});
		expect(auth.logout).not.toHaveBeenCalled();
	});

	it('signs the user out when the token has only roles the app does not know', async () => {
		auth.user = { roles: extractRoles(['offline_access', 'uma_authorization']) };

		await expect(settled()).resolves.toBe(PENDING);

		expect(remember).toHaveBeenCalledWith(expect.stringContaining('permessi'));
		expect(auth.logout).toHaveBeenCalledWith({ keepDrafts: true });
	});

	it('signs the user out with a generic message when the failure is not an error', async () => {
		ensureAppUser.mockRejectedValue('boom');

		await expect(settled()).resolves.toBe(PENDING);

		expect(remember).toHaveBeenCalledWith('Errore sconosciuto. Sei stato disconnesso.');
	});
});

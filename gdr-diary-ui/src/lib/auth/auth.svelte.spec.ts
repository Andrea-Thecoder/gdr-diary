import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthState } from './auth.svelte.js';

const { instance, clearAllDrafts, constructed } = vi.hoisted(() => ({
	instance: {
		init: vi.fn<(options: Record<string, unknown>) => Promise<boolean>>(),
		tokenParsed: undefined as Record<string, unknown> | undefined,
		idTokenParsed: undefined as Record<string, unknown> | undefined,
		login: vi.fn<(options: Record<string, unknown>) => Promise<void>>(),
		logout: vi.fn<(options: Record<string, unknown>) => Promise<void>>(),
		register: vi.fn<(options: Record<string, unknown>) => Promise<void>>(),
		updateToken: vi.fn<(minValidity: number) => Promise<boolean>>(),
		token: undefined as string | undefined,
		onActionUpdate: undefined as ((status: string, action?: string) => void) | undefined
	},
	clearAllDrafts: vi.fn<() => Promise<void>>(),
	constructed: vi.fn<(config: unknown) => void>()
}));

vi.mock('keycloak-js', () => ({
	default: function Keycloak(config: unknown): typeof instance {
		constructed(config);
		return instance;
	}
}));

vi.mock('#lib/book/draft.js', () => ({ clearAllDrafts }));

vi.mock('#lib/env.js', () => ({
	getEnv: () => ({
		VITE_KEYCLOAK_URL: 'http://kc.test',
		VITE_KEYCLOAK_REALM: 'realm',
		VITE_KEYCLOAK_CLIENT_ID: 'client',
		VITE_API_BASE_URL: 'http://api.test'
	})
}));

function freshAuth(): AuthState {
	return new AuthState();
}

beforeEach(() => {
	instance.init.mockReset();
	instance.tokenParsed = undefined;
	instance.idTokenParsed = undefined;
	instance.login.mockReset();
	instance.login.mockResolvedValue();
	instance.logout.mockReset();
	instance.logout.mockResolvedValue();
	instance.register.mockReset();
	instance.register.mockResolvedValue();
	instance.updateToken.mockReset();
	instance.updateToken.mockResolvedValue(true);
	instance.token = undefined;
	instance.onActionUpdate = undefined;
	clearAllDrafts.mockReset();
	clearAllDrafts.mockResolvedValue();
	constructed.mockReset();
});

describe('auth startup', () => {
	it('asks Keycloak for an existing session without showing a login page', async () => {
		instance.init.mockResolvedValue(false);
		const auth = freshAuth();

		await auth.init();

		const options = instance.init.mock.calls[0]?.[0];
		expect(options).toMatchObject({
			onLoad: 'check-sso',
			pkceMethod: 'S256',
			silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`
		});
	});

	it('keeps no token or refresh token in the browser storage', async () => {
		instance.init.mockResolvedValue(false);
		const auth = freshAuth();

		await auth.init();

		const options = instance.init.mock.calls[0]?.[0] ?? {};
		expect(options).not.toHaveProperty('token');
		expect(options).not.toHaveProperty('refreshToken');
		expect(options).not.toHaveProperty('idToken');
	});

	it('is anonymous when Keycloak has no session', async () => {
		instance.init.mockResolvedValue(false);
		const auth = freshAuth();

		await auth.init();

		expect(auth.status).toBe('anonymous');
		expect(auth.user).toBeNull();
	});

	it('is authenticated, with the user, when Keycloak recognises the browser', async () => {
		instance.init.mockResolvedValue(true);
		instance.tokenParsed = {
			sub: 'u1',
			email: 'a@b.it',
			preferred_username: 'alira',
			realm_access: { roles: ['user'] }
		};
		const auth = freshAuth();

		await auth.init();

		expect(auth.status).toBe('authenticated');
		expect(auth.user?.sub).toBe('u1');
		expect(auth.user?.roles).toEqual(['user']);
	});

	it('knows the address that waits for its confirmation, if there is one', async () => {
		instance.init.mockResolvedValue(true);
		instance.tokenParsed = {
			sub: 'u1',
			email: 'a@b.it',
			email_pending: 'n@b.it'
		};
		const waiting = freshAuth();
		await waiting.init();
		instance.tokenParsed = { sub: 'u1', email: 'a@b.it' };
		const settled = freshAuth();
		await settled.init();

		expect(waiting.user?.pendingEmail).toBe('n@b.it');
		expect(settled.user?.pendingEmail).toBeNull();
	});

	it('reports an error when Keycloak cannot be reached', async () => {
		instance.init.mockRejectedValue(new Error('Timeout when waiting for 3rd party check iframe'));
		const auth = freshAuth();

		await auth.init();

		expect(auth.status).toBe('error');
		expect(auth.error).toContain('Timeout');
	});

	it('starts only once however many callers ask', async () => {
		instance.init.mockResolvedValue(false);
		const auth = freshAuth();

		await Promise.all([auth.init(), auth.init(), auth.init()]);

		expect(instance.init).toHaveBeenCalledTimes(1);
	});
});

describe('recent login', () => {
	const now = 1_800_000_000;

	beforeEach(() => {
		vi.useFakeTimers();
		vi.setSystemTime(now * 1000);
		instance.init.mockResolvedValue(true);
		instance.tokenParsed = { sub: 'u1' };
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('is true when the password was typed in the last five minutes', async () => {
		instance.idTokenParsed = { auth_time: now - 120 };
		const auth = freshAuth();
		await auth.init();

		expect(auth.recentlyAuthenticated()).toBe(true);
	});

	it('is false when the login is older, or its time is unknown', async () => {
		instance.idTokenParsed = { auth_time: now - 900 };
		const auth = freshAuth();
		await auth.init();
		expect(auth.recentlyAuthenticated()).toBe(false);

		instance.idTokenParsed = {};
		expect(auth.recentlyAuthenticated()).toBe(false);
	});

	it('asks for the password again, whatever the session', async () => {
		const auth = freshAuth();

		await auth.reauthenticate('http://app.test/profile?elimina=1');

		expect(instance.login).toHaveBeenCalledWith({
			prompt: 'login',
			redirectUri: 'http://app.test/profile?elimina=1'
		});
	});
});

describe('email change', () => {
	it('sends the user to the Keycloak page that changes only the email', async () => {
		const auth = freshAuth();

		await auth.changeEmail('http://app.test/profile');

		expect(instance.login).toHaveBeenCalledWith({
			action: 'UPDATE_EMAIL',
			redirectUri: 'http://app.test/profile'
		});
	});

	it('tells how the trip to Keycloak ended, once', async () => {
		instance.init.mockImplementation(() => {
			instance.onActionUpdate?.('success', 'UPDATE_EMAIL');
			return Promise.resolve(true);
		});
		const auth = freshAuth();

		expect(await auth.takeActionResult()).toEqual({
			status: 'success',
			action: 'UPDATE_EMAIL'
		});
		expect(await auth.takeActionResult()).toBeUndefined();
	});

	it('has nothing to tell when the user did not come back from a Keycloak action', async () => {
		instance.init.mockResolvedValue(true);

		expect(await freshAuth().takeActionResult()).toBeUndefined();
	});
});

describe('account closing', () => {
	it('forgets the drafts and sends the user to the Keycloak page that deletes the account', async () => {
		const auth = freshAuth();

		await auth.closeAccount('http://app.test');

		expect(clearAllDrafts).toHaveBeenCalledOnce();
		expect(instance.login).toHaveBeenCalledWith({
			action: 'delete_account',
			redirectUri: 'http://app.test'
		});
	});

	it('keeps the result of another Keycloak action when asked for a different one', async () => {
		instance.init.mockImplementation(() => {
			instance.onActionUpdate?.('cancelled', 'delete_account');
			return Promise.resolve(true);
		});
		const auth = freshAuth();

		expect(await auth.takeActionResult('UPDATE_EMAIL')).toBeUndefined();
		expect(await auth.takeActionResult('delete_account')).toEqual({
			status: 'cancelled',
			action: 'delete_account'
		});
		expect(await auth.takeActionResult('delete_account')).toBeUndefined();
	});
});

describe('auth logout', () => {
	it('ends the Keycloak session, goes back to the home and forgets the unsaved drafts', async () => {
		instance.init.mockResolvedValue(false);
		const auth = freshAuth();

		await auth.logout();

		expect(clearAllDrafts).toHaveBeenCalledOnce();
		expect(instance.logout).toHaveBeenCalledWith({
			redirectUri: window.location.origin
		});
	});

	it('can keep the unsaved drafts when the sign out is not the choice of the user', async () => {
		instance.init.mockResolvedValue(false);
		const auth = freshAuth();

		await auth.logout({ keepDrafts: true });

		expect(clearAllDrafts).not.toHaveBeenCalled();
		expect(instance.logout).toHaveBeenCalledOnce();
	});
});

describe('auth token', () => {
	it('asks Keycloak to keep the token valid for 30 seconds and returns it', async () => {
		instance.init.mockResolvedValue(true);
		instance.token = 'fresh-token';
		const auth = freshAuth();

		const token = await auth.getToken();

		expect(instance.updateToken).toHaveBeenCalledWith(30);
		expect(token).toBe('fresh-token');
	});

	it('becomes anonymous when the token cannot be renewed', async () => {
		instance.init.mockResolvedValue(true);
		instance.tokenParsed = { sub: 'u1' };
		instance.updateToken.mockRejectedValue(new Error('expired'));
		const auth = freshAuth();
		await auth.init();

		await auth.getToken().catch(() => undefined);

		expect(auth.status).toBe('anonymous');
		expect(auth.user).toBeNull();
	});

	it('sends the user to the login of the current page when the token cannot be renewed', async () => {
		instance.init.mockResolvedValue(true);
		instance.updateToken.mockRejectedValue(new Error('expired'));
		const auth = freshAuth();

		await auth.getToken().catch(() => undefined);

		expect(instance.login).toHaveBeenCalledWith({
			redirectUri: window.location.href
		});
	});

	it('rejects with a session expired error when the token cannot be renewed', async () => {
		instance.init.mockResolvedValue(true);
		instance.updateToken.mockRejectedValue(new Error('expired'));
		const auth = freshAuth();

		await expect(auth.getToken()).rejects.toThrow('Session expired');
	});

	it('rejects when Keycloak has no token after the refresh', async () => {
		instance.init.mockResolvedValue(true);
		instance.token = undefined;
		const auth = freshAuth();

		await expect(auth.getToken()).rejects.toThrow('Not authenticated');
	});
});

describe('user refresh', () => {
	it('forces Keycloak to issue a new token', async () => {
		instance.init.mockResolvedValue(true);
		instance.tokenParsed = { sub: 'u1' };
		const auth = freshAuth();

		await auth.refreshUser();

		expect(instance.updateToken).toHaveBeenCalledWith(-1);
	});

	it('applies the email and the roles of the new token', async () => {
		instance.init.mockResolvedValue(true);
		instance.tokenParsed = {
			sub: 'u1',
			email: 'old@b.it',
			realm_access: { roles: ['user'] }
		};
		const auth = freshAuth();
		await auth.init();
		instance.tokenParsed = {
			sub: 'u1',
			email: 'new@b.it',
			realm_access: { roles: ['admin'] }
		};

		await auth.refreshUser();

		expect(auth.user).toMatchObject({ email: 'new@b.it', roles: ['admin'] });
	});
});

describe('auth registration', () => {
	it('sends the user to the Keycloak registration and back to the given address', async () => {
		const auth = freshAuth();

		await auth.register('http://app.test/characters');

		expect(instance.register).toHaveBeenCalledWith({
			redirectUri: 'http://app.test/characters'
		});
	});
});

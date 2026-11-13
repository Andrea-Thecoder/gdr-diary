import Keycloak from 'keycloak-js';
import { clearAllDrafts } from '#lib/book/draft.js';
import { getEnv } from '#lib/env.js';
import { extractRoles, type AppRole } from '#lib/auth/roles.js';

export interface AuthUser {
	sub: string;
	email: string | null;
	pendingEmail: string | null;
	name: string | null;
	nickname: string | null;
	roles: AppRole[];
}

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated' | 'error';

export interface ActionResult {
	status: 'success' | 'cancelled' | 'error';
	action: string | undefined;
}

const MIN_TOKEN_VALIDITY_SECONDS = 30;
const SILENT_CHECK_PATH = '/silent-check-sso.html';
const RECENT_LOGIN_SECONDS = 300;

export class AuthState {
	status = $state<AuthStatus>('loading');
	user = $state<AuthUser | null>(null);
	error = $state<string | null>(null);

	private keycloak: Keycloak | undefined;
	private actionResult: ActionResult | undefined;
	private initPromise: Promise<void> | undefined;

	init(): Promise<void> {
		this.initPromise ??= this.doInit();
		return this.initPromise;
	}

	async login(returnTo: string = window.location.href): Promise<void> {
		await this.init();
		await this.requireKeycloak().login({ redirectUri: returnTo });
	}

	async register(returnTo: string = window.location.href): Promise<void> {
		await this.init();
		await this.requireKeycloak().register({ redirectUri: returnTo });
	}

	recentlyAuthenticated(maxAgeSeconds: number = RECENT_LOGIN_SECONDS): boolean {
		const authTime = this.keycloak?.idTokenParsed?.auth_time;
		return typeof authTime === 'number' && Date.now() / 1000 - authTime <= maxAgeSeconds;
	}

	async reauthenticate(returnTo: string): Promise<void> {
		await this.init();
		await this.requireKeycloak().login({ prompt: 'login', redirectUri: returnTo });
	}

	async changeEmail(returnTo: string): Promise<void> {
		await this.init();
		await this.requireKeycloak().login({ action: 'UPDATE_EMAIL', redirectUri: returnTo });
	}

	async forgetDeletedAccount(): Promise<void> {
		await this.init();
		await clearAllDrafts();
		this.requireKeycloak().clearToken();
		this.applyAnonymous();
	}

	async takeActionResult(action?: string): Promise<ActionResult | undefined> {
		await this.init();
		const result = this.actionResult;
		if (result === undefined || (action !== undefined && result.action !== action)) {
			return undefined;
		}
		this.actionResult = undefined;
		return result;
	}

	async logout(options: { keepDrafts?: boolean } = {}): Promise<void> {
		await this.init();
		if (options.keepDrafts !== true) {
			await clearAllDrafts();
		}
		await this.requireKeycloak().logout({ redirectUri: window.location.origin });
	}

	async getToken(): Promise<string> {
		await this.init();
		const keycloak = this.requireKeycloak();
		try {
			await keycloak.updateToken(MIN_TOKEN_VALIDITY_SECONDS);
		} catch {
			this.applyAnonymous();
			await keycloak.login({ redirectUri: window.location.href });
			throw new Error('Session expired');
		}
		if (keycloak.token === undefined) {
			throw new Error('Not authenticated');
		}
		return keycloak.token;
	}

	async refreshUser(): Promise<void> {
		await this.init();
		const keycloak = this.requireKeycloak();
		await keycloak.updateToken(-1);
		this.applyAuthenticated(keycloak);
	}

	private async doInit(): Promise<void> {
		const env = getEnv();
		const keycloak = new Keycloak({
			url: env.VITE_KEYCLOAK_URL,
			realm: env.VITE_KEYCLOAK_REALM,
			clientId: env.VITE_KEYCLOAK_CLIENT_ID
		});
		this.keycloak = keycloak;
		keycloak.onActionUpdate = (status, action) => {
			this.actionResult = { status, action };
		};
		try {
			const authenticated = await keycloak.init({
				onLoad: 'check-sso',
				silentCheckSsoRedirectUri: `${window.location.origin}${SILENT_CHECK_PATH}`,
				pkceMethod: 'S256',
				checkLoginIframe: false
			});
			if (authenticated) {
				this.applyAuthenticated(keycloak);
			} else {
				this.applyAnonymous();
			}
		} catch (cause) {
			this.status = 'error';
			this.error = cause instanceof Error ? cause.message : 'Keycloak initialisation failed';
		}
	}

	private applyAuthenticated(keycloak: Keycloak): void {
		const parsed = keycloak.tokenParsed;
		this.user = {
			sub: parsed?.sub ?? '',
			email: typeof parsed?.['email'] === 'string' ? parsed['email'] : null,
			pendingEmail: typeof parsed?.['email_pending'] === 'string' ? parsed['email_pending'] : null,
			name: typeof parsed?.['name'] === 'string' ? parsed['name'] : null,
			nickname:
				typeof parsed?.['preferred_username'] === 'string' ? parsed['preferred_username'] : null,
			roles: extractRoles(parsed?.realm_access?.roles)
		};
		this.status = 'authenticated';
	}

	private applyAnonymous(): void {
		this.user = null;
		this.status = 'anonymous';
	}

	private requireKeycloak(): Keycloak {
		if (this.keycloak === undefined) {
			throw new Error('Keycloak not initialised');
		}
		return this.keycloak;
	}
}

export const auth = new AuthState();

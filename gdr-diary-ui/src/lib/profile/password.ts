import { auth } from '#lib/auth/auth.svelte.js';
import { getEnv } from '#lib/env.js';

const REQUEST_PATH = 'gdr-password/request';
const TOO_SOON = 429;

export const PASSWORD_PARAM = 'password';
export const PASSWORD_DONE = 'cambiata';

export type PasswordRequestOutcome = 'sent' | 'too-soon' | 'failed';

export function passwordReturnUrl(profileUrl: string): string {
	const url = new URL(profileUrl);
	url.searchParams.set(PASSWORD_PARAM, PASSWORD_DONE);
	return url.href;
}

export function passwordWasChanged(href: string): boolean {
	return new URL(href).searchParams.get(PASSWORD_PARAM) === PASSWORD_DONE;
}

export async function requestPasswordChange(returnTo: string): Promise<PasswordRequestOutcome> {
	const env = getEnv();
	const base = env.VITE_KEYCLOAK_URL.replace(/\/+$/, '');
	try {
		const token = await auth.getToken();
		const url = new URL(`${base}/realms/${env.VITE_KEYCLOAK_REALM}/${REQUEST_PATH}`);
		url.searchParams.set('redirect_uri', passwordReturnUrl(returnTo));
		const response = await fetch(url, {
			method: 'POST',
			headers: { Authorization: `Bearer ${token}` }
		});
		if (response.ok) {
			return 'sent';
		}
		return response.status === TOO_SOON ? 'too-soon' : 'failed';
	} catch {
		return 'failed';
	}
}

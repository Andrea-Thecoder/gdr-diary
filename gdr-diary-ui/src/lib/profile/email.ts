import { syncAppUser } from '#lib/api/me.js';
import { auth } from '#lib/auth/auth.svelte.js';
import { getEnv } from '#lib/env.js';

const EMAIL_ACTION = 'UPDATE_EMAIL';
const RESEND_PATH = 'gdr-email/resend';
const TOO_SOON = 429;
const NO_PENDING = 409;

export type EmailOutcome =
	| { status: 'none' }
	| { status: 'changed'; email: string }
	| { status: 'pending' }
	| { status: 'failed'; message: string };

const KEYCLOAK_FAILED = 'Keycloak non ha cambiato l’indirizzo. Riprova.';
const SESSION_FAILED =
	'L’indirizzo è cambiato, ma non riesco a rinnovare la sessione. Esci e rientra per aggiornarlo.';
const SERVICE_FAILED =
	'L’indirizzo è cambiato su Keycloak, ma il servizio non è stato aggiornato. Si allinea al prossimo accesso.';

export type ResendOutcome = 'sent' | 'too-soon' | 'nothing-pending' | 'failed';

export function startEmailChange(returnTo: string): Promise<void> {
	return auth.changeEmail(returnTo);
}

export async function finishEmailChange(shown: string): Promise<EmailOutcome> {
	const result = await auth.takeActionResult(EMAIL_ACTION);
	if (result?.action !== EMAIL_ACTION || result.status === 'cancelled') {
		return { status: 'none' };
	}
	if (result.status === 'error') {
		return { status: 'failed', message: KEYCLOAK_FAILED };
	}
	try {
		await auth.refreshUser();
	} catch {
		return { status: 'failed', message: SESSION_FAILED };
	}
	const current = auth.user?.email;
	if (current === undefined || current === null || current.toLowerCase() === shown.toLowerCase()) {
		return { status: 'pending' };
	}
	try {
		await syncAppUser();
	} catch {
		return { status: 'failed', message: SERVICE_FAILED };
	}
	return { status: 'changed', email: current };
}

export async function resendEmailChange(returnTo: string): Promise<ResendOutcome> {
	const env = getEnv();
	const base = env.VITE_KEYCLOAK_URL.replace(/\/+$/, '');
	try {
		const token = await auth.getToken();
		const url = new URL(`${base}/realms/${env.VITE_KEYCLOAK_REALM}/${RESEND_PATH}`);
		url.searchParams.set('redirect_uri', returnTo);
		const response = await fetch(url, {
			method: 'POST',
			headers: { Authorization: `Bearer ${token}` }
		});
		if (response.ok) {
			return 'sent';
		}
		if (response.status === TOO_SOON) {
			return 'too-soon';
		}
		if (response.status === NO_PENDING) {
			await auth.refreshUser();
			return 'nothing-pending';
		}
		return 'failed';
	} catch {
		return 'failed';
	}
}

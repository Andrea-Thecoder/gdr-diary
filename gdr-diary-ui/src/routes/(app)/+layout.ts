import { error, redirect } from '@sveltejs/kit';
import { resolve } from '$app/paths';
import { ensureAppUser } from '#lib/api/me.js';
import { accountSync } from '#lib/settings/account.js';
import { auth } from '#lib/auth/auth.svelte.js';
import { decideAccess } from '#lib/auth/roles.js';
import { recallError, rememberError } from '#lib/ui/flash-error.js';
import type { LayoutLoad } from './$types';

async function signOutWith(reason: string): Promise<never> {
	const message = `${reason.replace(/\.$/, '')}. Sei stato disconnesso.`;
	rememberError(message);
	try {
		await auth.logout({ keepDrafts: true });
	} catch {
		recallError();
		error(502, message);
	}
	return new Promise<never>(() => undefined);
}

export const load: LayoutLoad = async () => {
	await auth.init();

	if (auth.status === 'error') {
		redirect(307, resolve('/'));
	}

	const decision = decideAccess(auth.status, auth.user?.roles ?? []);
	if (decision === 'login' || decision === 'wait') {
		await auth.login();
		return new Promise<never>(() => undefined);
	}
	if (decision === 'forbidden') {
		return signOutWith('Il tuo account non ha ancora i permessi per usare Diario GDR.');
	}

	let failure: string | null = null;
	try {
		await ensureAppUser();
	} catch (cause) {
		failure = cause instanceof Error ? cause.message : 'Errore sconosciuto.';
	}
	if (failure !== null) {
		return signOutWith(failure);
	}

	await accountSync.run();

	return {};
};

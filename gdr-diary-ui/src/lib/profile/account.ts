import { auth } from '#lib/auth/auth.svelte.js';
import { rememberNotice } from '#lib/ui/flash-error.js';

export const ACCOUNT_DELETED = 'Il tuo account è stato eliminato.';

export async function closeAccount(): Promise<void> {
	await auth.forgetDeletedAccount();
	rememberNotice(ACCOUNT_DELETED);
	window.location.assign(window.location.origin);
}

import { auth } from '#lib/auth/auth.svelte.js';

const CLOSE_ACTION = 'delete_account';

export const CLOSE_INCOMPLETE =
	'I tuoi dati sono stati cancellati, ma l’accesso è ancora aperto. Dal profilo premi di nuovo “Elimina account” per chiuderlo.';

export function closeAccount(): Promise<void> {
	return auth.closeAccount(window.location.origin);
}

export async function closingWasInterrupted(): Promise<boolean> {
	return (await auth.takeActionResult(CLOSE_ACTION)) !== undefined;
}

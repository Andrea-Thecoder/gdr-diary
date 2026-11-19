const KEY = 'liber-heroum:pending-action';

export const PENDING_ACTION_MAX_AGE_MS = 10 * 60 * 1000;

export type PendingAction = 'export-data' | 'delete-account';

interface Stored {
	action: PendingAction;
	at: number;
}

function read(): Stored | null {
	try {
		const raw = sessionStorage.getItem(KEY);
		return raw === null ? null : (JSON.parse(raw) as Stored);
	} catch {
		return null;
	}
}

export function rememberPendingAction(action: PendingAction, now: number = Date.now()): void {
	try {
		sessionStorage.setItem(KEY, JSON.stringify({ action, at: now }));
	} catch {
		return;
	}
}

export function takePendingAction(action: PendingAction, now: number = Date.now()): boolean {
	const stored = read();
	if (stored?.action !== action) {
		return false;
	}
	try {
		sessionStorage.removeItem(KEY);
	} catch {
		return false;
	}
	return now - stored.at <= PENDING_ACTION_MAX_AGE_MS;
}

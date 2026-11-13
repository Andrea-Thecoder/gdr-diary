const KEY = 'diario-gdr:flash-error';
const NOTICE_KEY = 'diario-gdr:flash-notice';

export function rememberError(message: string): void {
	try {
		sessionStorage.setItem(KEY, message);
	} catch {
		return;
	}
}

export function recallError(): string | null {
	try {
		const message = sessionStorage.getItem(KEY);
		sessionStorage.removeItem(KEY);
		return message;
	} catch {
		return null;
	}
}

export function rememberNotice(message: string): void {
	try {
		sessionStorage.setItem(NOTICE_KEY, message);
	} catch {
		return;
	}
}

export function recallNotice(): string | null {
	try {
		const message = sessionStorage.getItem(NOTICE_KEY);
		sessionStorage.removeItem(NOTICE_KEY);
		return message;
	} catch {
		return null;
	}
}

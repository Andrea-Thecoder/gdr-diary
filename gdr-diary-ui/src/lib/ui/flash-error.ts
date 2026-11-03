const KEY = 'diario-gdr:flash-error';

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

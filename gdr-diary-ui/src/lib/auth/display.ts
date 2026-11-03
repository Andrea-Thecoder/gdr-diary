interface Named {
	nickname: string | null;
	name: string | null;
	email: string | null;
}

function filled(value: string | null): value is string {
	return value !== null && value.trim() !== '';
}

export function accountName(user: Named | null | undefined): string {
	if (user === null || user === undefined) {
		return '';
	}
	const found = [user.nickname, user.name, user.email].find(filled);
	return found?.trim() ?? '';
}

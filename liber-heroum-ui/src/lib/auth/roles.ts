export const APP_ROLES = ['user', 'admin'] as const;
export type AppRole = (typeof APP_ROLES)[number];

function isAppRole(value: string): value is AppRole {
	return (APP_ROLES as readonly string[]).includes(value);
}

export function extractRoles(realmRoles: readonly string[] | undefined): AppRole[] {
	return (realmRoles ?? []).map((role) => role.toLowerCase()).filter(isAppRole);
}

export type AccessDecision = 'wait' | 'login' | 'forbidden' | 'allow';

export function decideAccess(
	status: 'loading' | 'anonymous' | 'authenticated' | 'error',
	roles: readonly AppRole[]
): AccessDecision {
	if (status === 'loading') {
		return 'wait';
	}
	if (status !== 'authenticated') {
		return 'login';
	}
	return roles.length > 0 ? 'allow' : 'forbidden';
}

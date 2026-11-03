export const THEMES = ['pergamena', 'viola'] as const;

export type ThemeName = (typeof THEMES)[number];

export type ThemePreference = 'system' | ThemeName;

export const PREFERENCE_NAMES: Record<ThemePreference, string> = {
	system: 'sistema',
	pergamena: 'chiaro',
	viola: 'scuro'
};

export const THEME_COLORS: Record<ThemeName, string> = {
	pergamena: '#F1E6CC',
	viola: '#12031F'
};

export const THEME_COOKIE_NAME = 'gdr-theme';

export const THEME_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

export function isThemeName(value: unknown): value is ThemeName {
	return THEMES.some((theme) => theme === value);
}

export function nextPreference(current: ThemePreference): ThemePreference {
	if (current === 'system') {
		return 'pergamena';
	}
	return current === 'pergamena' ? 'viola' : 'system';
}

export function resolveTheme(preference: ThemePreference, systemPrefersDark: boolean): ThemeName {
	if (preference === 'system') {
		return systemPrefersDark ? 'viola' : 'pergamena';
	}
	return preference;
}

export function parseThemeCookie(cookies: string): ThemePreference {
	const entry = cookies.split('; ').find((part) => part.startsWith(`${THEME_COOKIE_NAME}=`));
	const value = entry?.slice(THEME_COOKIE_NAME.length + 1);
	return isThemeName(value) ? value : 'system';
}

export function readStoredPreference(): ThemePreference {
	return parseThemeCookie(document.cookie);
}

export interface ThemeCookieOptions {
	domain?: string | undefined;
	secure: boolean;
}

export function buildThemeCookie(
	preference: ThemePreference,
	{ domain, secure }: ThemeCookieOptions
): string {
	const chosen = preference !== 'system';
	const parts = [
		`${THEME_COOKIE_NAME}=${chosen ? preference : ''}`,
		'Path=/',
		'SameSite=Lax',
		`Max-Age=${chosen ? THEME_COOKIE_MAX_AGE : 0}`
	];
	if (domain !== undefined) {
		parts.push(`Domain=${domain}`);
	}
	if (secure) {
		parts.push('Secure');
	}
	return parts.join('; ');
}

export function storePreference(preference: ThemePreference, domain?: string): void {
	document.cookie = buildThemeCookie(preference, {
		domain,
		secure: window.location.protocol === 'https:'
	});
}

export function applyPreference(preference: ThemePreference): void {
	const systemPrefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
	const theme = resolveTheme(preference, systemPrefersDark);
	document.documentElement.setAttribute('data-theme', theme);
	document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
}

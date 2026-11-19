import { isThemeName, type ThemePreference } from '#lib/theme/theme.js';

export const THEME_SETTING = 'theme';

export type AccountSettings = Record<string, unknown>;

export function themeFromSettings(
	settings: AccountSettings | null | undefined
): ThemePreference | null {
	const value = settings?.[THEME_SETTING];
	if (value === 'system') {
		return 'system';
	}
	return isThemeName(value) ? value : null;
}

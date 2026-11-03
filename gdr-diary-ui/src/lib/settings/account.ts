import { getApi } from '#lib/api/index.js';
import { profileAvatar } from '#lib/profile/avatar.svelte.js';
import { theme } from '#lib/theme/theme.svelte.js';
import type { ThemePreference } from '#lib/theme/theme.js';
import { fetchAccount, saveSettings } from './api.js';
import { preferences } from './preferences.svelte.js';
import { THEME_SETTING, themeFromSettings, type AccountSettings } from './settings.js';

export class AccountSync {
	private synced = false;

	async run(): Promise<void> {
		if (this.synced) {
			return;
		}
		try {
			const account = await fetchAccount(getApi());
			const preference = themeFromSettings(account.settings);
			this.synced = true;
			profileAvatar.url = account.avatarUrl;
			preferences.apply(account.settings);
			if (preference !== null) {
				theme.select(preference);
			}
		} catch {
			this.synced = false;
		}
	}
}

export const accountSync = new AccountSync();

export async function saveThemeToAccount(preference: ThemePreference): Promise<void> {
	await saveSettings(getApi(), { [THEME_SETTING]: preference });
}

export async function savePreferencesToAccount(changes: AccountSettings): Promise<void> {
	preferences.apply(await saveSettings(getApi(), changes));
}

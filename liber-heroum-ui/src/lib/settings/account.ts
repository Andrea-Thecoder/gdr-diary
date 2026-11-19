import { getApi } from '#lib/api/index.js';
import { profileAvatar } from '#lib/profile/avatar.svelte.js';
import { theme } from '#lib/theme/theme.svelte.js';
import { fetchAccount, saveSettings } from './api.js';
import { preferences } from './preferences.svelte.js';
import { themeFromSettings, type AccountSettings } from './settings.js';

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

export async function savePreferencesToAccount(changes: AccountSettings): Promise<void> {
	preferences.apply(await saveSettings(getApi(), changes));
}

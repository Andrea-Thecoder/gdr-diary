import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import type { AccountSettings } from './settings.js';

export interface Account {
	settings: AccountSettings;
	avatarUrl: string | null;
}

export async function fetchAccount(api: ApiClient): Promise<Account> {
	const { data, error } = await api.GET('/me');
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a leggere le tue impostazioni.'));
	}
	return { settings: data.settings ?? {}, avatarUrl: data.avatarUrl ?? null };
}

export async function saveSettings(
	api: ApiClient,
	changes: AccountSettings
): Promise<AccountSettings> {
	const { data, error } = await api.PATCH('/me/settings', { body: { settings: changes } });
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a salvare le impostazioni.'));
	}
	return data.payload ?? {};
}

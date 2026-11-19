import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import { fileForm, type ImageChange } from '#lib/api/multipart.js';

export interface Profile {
	email: string;
	premium: boolean;
}

export async function fetchProfile(api: ApiClient): Promise<Profile> {
	const { data, error } = await api.GET('/me');
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a leggere il tuo profilo.'));
	}
	return { email: data.email ?? '', premium: data.premium === true };
}

export async function fetchAvatarUrl(api: ApiClient): Promise<string | null> {
	const { data, error } = await api.GET('/me');
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a leggere il tuo profilo.'));
	}
	return data.avatarUrl ?? null;
}

export async function changeAvatar(api: ApiClient, change: ImageChange): Promise<string | null> {
	if (change.type === 'replace') {
		const { data, error } = await api.PUT('/me/avatar', {
			body: { file: '' },
			bodySerializer: fileForm(change.file)
		});
		if (data === undefined) {
			throw new Error(describeApiError(error, 'Non riesco a caricare l’immagine.'));
		}
		return data.payload ?? null;
	}
	if (change.type === 'remove') {
		const { error, response } = await api.DELETE('/me/avatar');
		if (!response.ok) {
			throw new Error(describeApiError(error, 'Non riesco a togliere l’immagine.'));
		}
	}
	return null;
}

export class RecentLoginRequiredError extends Error {
	constructor() {
		super('Serve un accesso più recente.');
		this.name = 'RecentLoginRequiredError';
	}
}

const UNAUTHORIZED = 401;

export async function deleteAccount(api: ApiClient): Promise<void> {
	const { error, response } = await api.DELETE('/me');
	if (response.status === UNAUTHORIZED) {
		throw new RecentLoginRequiredError();
	}
	if (!response.ok) {
		throw new Error(
			describeApiError(error, 'Non riesco a eliminare l’account. Riprova più tardi.')
		);
	}
}

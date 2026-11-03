import type { ApiClient } from '#lib/api/client.js';
import { describeApiError } from '#lib/api/client.js';
import type { components } from '#lib/api/schema.js';
import { fileForm, type ImageChange } from '#lib/api/multipart.js';
import type { CharacterKind } from '#lib/characters/kind.js';

export type CharacterSummary = components['schemas']['BaseGameCharacterDTO'];
export type CharacterDetail = components['schemas']['DetailGameCharacterDTO'];

export const PAGE_SIZE = 12;
const NOT_FOUND = 404;

export interface CharacterQuery {
	name: string;
	campaignName: string;
	descending: boolean;
	page: number;
}

export interface CharacterPage {
	items: CharacterSummary[];
	totalRows: number;
	totalPages: number;
	page: number;
}

export interface NewCharacter {
	kind: CharacterKind;
	name: string;
	description: string;
	campaignName: string;
	backgroundColor: string;
}

export interface EditedCharacter {
	name: string;
	description: string;
	campaignName: string;
	backgroundColor: string;
}

export type AvatarChange = ImageChange;

export interface CreatedCharacter {
	character: CharacterDetail;
	avatarError: string | null;
}

export async function fetchCharacters(
	api: ApiClient,
	query: CharacterQuery
): Promise<CharacterPage> {
	const name = query.name.trim();
	const campaignName = query.campaignName.trim();
	const { data, error } = await api.GET('/characters', {
		params: {
			query: {
				page: query.page,
				size: PAGE_SIZE,
				sort: 'name',
				descending: query.descending,
				...(name === '' ? {} : { name }),
				...(campaignName === '' ? {} : { campaignName })
			}
		}
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare i personaggi.'));
	}
	return {
		items: data.list ?? [],
		totalRows: data.totalRows ?? 0,
		totalPages: data.totalPages ?? 0,
		page: data.page ?? query.page
	};
}

export async function createCharacter(
	api: ApiClient,
	input: NewCharacter,
	avatar: File | null
): Promise<CreatedCharacter> {
	const description = input.description.trim();
	const campaignName = input.campaignName.trim();
	const { data, error } = await api.POST('/characters', {
		body: {
			name: input.name.trim(),
			kind: input.kind,
			backgroundColor: input.backgroundColor,
			...(description === '' ? {} : { description }),
			...(campaignName === '' ? {} : { campaignName })
		}
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a creare il personaggio.'));
	}
	if (avatar === null || data.id === undefined) {
		return { character: data, avatarError: null };
	}
	return { character: data, avatarError: await uploadAvatar(api, data.id, avatar) };
}

export async function fetchCharacter(api: ApiClient, id: string): Promise<CharacterDetail> {
	const { data, error } = await api.GET('/characters/{id}', { params: { path: { id } } });
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a caricare il personaggio.'));
	}
	return data;
}

export async function updateCharacter(
	api: ApiClient,
	id: string,
	input: EditedCharacter,
	avatar: AvatarChange
): Promise<CreatedCharacter> {
	const { data, error } = await api.PUT('/characters/{id}', {
		params: { path: { id } },
		body: {
			name: input.name.trim(),
			description: input.description.trim(),
			campaignName: input.campaignName.trim(),
			backgroundColor: input.backgroundColor
		}
	});
	if (data === undefined) {
		throw new Error(describeApiError(error, 'Non riesco a salvare il personaggio.'));
	}
	if (avatar.type === 'replace') {
		return { character: data, avatarError: await uploadAvatar(api, id, avatar.file) };
	}
	if (avatar.type === 'remove') {
		return { character: data, avatarError: await removeAvatar(api, id) };
	}
	return { character: data, avatarError: null };
}

async function removeAvatar(api: ApiClient, id: string): Promise<string | null> {
	const { error, response } = await api.DELETE('/characters/{id}/avatar', {
		params: { path: { id } }
	});
	if (response.ok) {
		return null;
	}
	return describeApiError(error, 'Non riesco a togliere l’immagine.');
}

async function uploadAvatar(api: ApiClient, id: string, avatar: File): Promise<string | null> {
	const { error, response } = await api.PUT('/characters/{id}/avatar', {
		params: { path: { id } },
		body: { file: '' },
		bodySerializer: fileForm(avatar)
	});
	if (response.ok) {
		return null;
	}
	return describeApiError(error, 'Non riesco a caricare l’immagine.');
}

export async function deleteCharacter(api: ApiClient, id: string): Promise<void> {
	const { error, response } = await api.DELETE('/characters/{id}', { params: { path: { id } } });
	if (!response.ok && response.status !== NOT_FOUND) {
		throw new Error(
			describeApiError(error, 'Non riesco a eliminare il personaggio. Riprova più tardi.')
		);
	}
}

import type { components } from '#lib/api/schema.js';

export type CharacterKind = components['schemas']['InsertGameCharacterDTO']['kind'];

export const DEFAULT_KIND: CharacterKind = 'PLAYER';

export const KIND_OPTIONS = [
	{
		value: 'PLAYER',
		title: 'Personaggio',
		hint: "L'eroe che interpreti al tavolo, con i suoi diari."
	},
	{
		value: 'GAME_MASTER',
		title: 'Master',
		hint: 'Il tuo profilo da Game Master, per guidare le sessioni.'
	}
] as const satisfies readonly { value: CharacterKind; title: string; hint: string }[];

export function isMaster(kind: CharacterKind | null | undefined): boolean {
	return kind === 'GAME_MASTER';
}

export interface KindCopy {
	title: string;
	label: string;
	namePlaceholder: string;
	campaignLabel: string;
	descriptionPlaceholder: string;
	submit: string;
}

const KIND_COPY: Record<CharacterKind, KindCopy> = {
	PLAYER: {
		title: 'Nuovo personaggio',
		label: 'Personaggio',
		namePlaceholder: 'Alira dei Salici',
		campaignLabel: 'Campagna',
		descriptionPlaceholder: 'Chi è, da dove viene, cosa vuole.',
		submit: 'Forgia il personaggio'
	},
	GAME_MASTER: {
		title: 'Nuovo Master',
		label: 'Master',
		namePlaceholder: 'Il Master della Torre',
		campaignLabel: 'Campagna che guidi',
		descriptionPlaceholder: 'Che master sei e che tono dai alle sessioni.',
		submit: 'Crea il Master'
	}
};

export function kindCopy(kind: CharacterKind): KindCopy {
	return KIND_COPY[kind];
}

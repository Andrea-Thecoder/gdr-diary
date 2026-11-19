import { resolve } from '$app/paths';

export type CharacterTab = 'diaries' | 'sheets' | 'other';

export interface CharacterTabEntry {
	id: CharacterTab;
	label: string;
	suffix?: string;
	href: string;
	countLabel?: (count: number) => string;
}

export function characterTabs(characterId: string): readonly CharacterTabEntry[] {
	return [
		{
			id: 'diaries',
			label: 'Diari',
			href: resolve('/(app)/characters/[characterId]', { characterId }),
			countLabel: (count) => (count === 1 ? '1 diario' : `${String(count)} diari`)
		},
		{
			id: 'sheets',
			label: 'Schede',
			suffix: ' del personaggio',
			href: resolve('/(app)/characters/[characterId]/sheets', { characterId }),
			countLabel: (count) => (count === 1 ? '1 scheda' : `${String(count)} schede`)
		},
		{
			id: 'other',
			label: 'Altro',
			href: resolve('/(app)/characters/[characterId]/other', { characterId })
		}
	];
}

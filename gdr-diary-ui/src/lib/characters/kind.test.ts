import { describe, expect, it } from 'vitest';
import { DEFAULT_KIND, isMaster, KIND_OPTIONS, kindCopy } from '#lib/characters/kind.js';

describe('isMaster', () => {
	it('is true only for the game master kind', () => {
		expect(isMaster('GAME_MASTER')).toBe(true);
		expect(isMaster('PLAYER')).toBe(false);
		expect(isMaster(undefined)).toBe(false);
		expect(isMaster(null)).toBe(false);
	});
});

describe('KIND_OPTIONS', () => {
	it('offers the player first and makes it the default', () => {
		expect(KIND_OPTIONS.map((option) => option.value)).toEqual(['PLAYER', 'GAME_MASTER']);
		expect(DEFAULT_KIND).toBe('PLAYER');
	});
});

describe('kindCopy', () => {
	it('words the form for a player', () => {
		expect(kindCopy('PLAYER')).toMatchObject({
			title: 'Nuovo personaggio',
			campaignLabel: 'Campagna',
			submit: 'Forgia il personaggio'
		});
	});

	it('words the form for a game master', () => {
		expect(kindCopy('GAME_MASTER')).toMatchObject({
			title: 'Nuovo Master',
			campaignLabel: 'Campagna che guidi',
			submit: 'Crea il Master'
		});
	});

	it.each(KIND_OPTIONS)('has a title for the offered kind $value', (option) => {
		expect(kindCopy(option.value).label).toBe(option.title);
	});
});

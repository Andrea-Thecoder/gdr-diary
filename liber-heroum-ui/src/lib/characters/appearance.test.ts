import { describe, expect, it } from 'vitest';
import {
	isAvatarType,
	isHexColor,
	photoBackground,
	silhouetteFor,
	userInitial,
	glowColor,
	DEFAULT_COLOR
} from '#lib/characters/appearance.js';

describe('isHexColor', () => {
	it('accepts #RRGGBB in any case', () => {
		expect(isHexColor('#3B2A6B')).toBe(true);
		expect(isHexColor('#3b2a6b')).toBe(true);
	});

	it('rejects short, long or unprefixed values', () => {
		expect(isHexColor('#fff')).toBe(false);
		expect(isHexColor('3B2A6B')).toBe(false);
		expect(isHexColor('#3B2A6B0')).toBe(false);
		expect(isHexColor('#GGGGGG')).toBe(false);
	});
});

describe('silhouetteFor', () => {
	it('is stable for the same id', () => {
		const id = '0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10';
		expect(silhouetteFor(id)).toBe(silhouetteFor(id));
	});

	it('uses both silhouettes across ids', () => {
		const kinds = new Set(
			['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'].map((id) => silhouetteFor(`${id}-id`))
		);
		expect(kinds).toEqual(new Set(['woman', 'man']));
	});
});

describe('silhouetteFor with kind', () => {
	it.each(['a-id', 'b-id', 'c-id'])('uses the master silhouette for the game master %s', (id) => {
		expect(silhouetteFor(id, 'GAME_MASTER')).toBe('master');
	});

	it('keeps the id based silhouette for a player', () => {
		expect(silhouetteFor('a-id', 'PLAYER')).toBe(silhouetteFor('a-id'));
		expect(silhouetteFor('a-id', null)).toBe(silhouetteFor('a-id'));
	});
});

describe('glowColor', () => {
	it('is the card color, softened', () => {
		expect(glowColor('#2F4B43')).toBe('color-mix(in srgb, #2F4B43 80%, transparent)');
	});

	it('falls back to the default color when the value is missing or wrong', () => {
		const fallback = `color-mix(in srgb, ${DEFAULT_COLOR} 80%, transparent)`;
		expect(glowColor(null)).toBe(fallback);
		expect(glowColor(undefined)).toBe(fallback);
		expect(glowColor('verde')).toBe(fallback);
	});
});

describe('photoBackground', () => {
	it('uses the character color', () => {
		expect(photoBackground('#7C2A22')).toContain('#7C2A22');
	});

	it('falls back to the default color when missing or malformed', () => {
		expect(photoBackground(null)).toContain(DEFAULT_COLOR);
		expect(photoBackground('rosso')).toContain(DEFAULT_COLOR);
	});
});

describe('isAvatarType', () => {
	it('allows png, jpeg and webp only', () => {
		expect(isAvatarType('image/png')).toBe(true);
		expect(isAvatarType('image/jpeg')).toBe(true);
		expect(isAvatarType('image/webp')).toBe(true);
		expect(isAvatarType('image/gif')).toBe(false);
		expect(isAvatarType('application/pdf')).toBe(false);
	});
});

describe('userInitial', () => {
	it('returns the uppercase first letter', () => {
		expect(userInitial('nome@esempio.it')).toBe('N');
	});

	it('returns a placeholder for empty values', () => {
		expect(userInitial('')).toBe('?');
		expect(userInitial(null)).toBe('?');
	});
});

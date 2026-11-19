import type { CropFrame } from '#lib/ui/crop.js';
import { isMaster, type CharacterKind } from '#lib/characters/kind.js';

export const PORTRAIT_FRAME: CropFrame = { shape: 'rect', aspect: 3 / 2 };

export const COLOR_PRESETS = [
	{ name: 'Viola', hex: '#3B2A6B' },
	{ name: 'Verde', hex: '#2F4B43' },
	{ name: 'Rosso', hex: '#7C2A22' },
	{ name: 'Blu', hex: '#3C3F6B' },
	{ name: 'Bronzo', hex: '#5B4A2A' }
] as const;

export const DEFAULT_COLOR = COLOR_PRESETS[0].hex;

export const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

export const AVATAR_TYPES = ['image/png', 'image/jpeg', 'image/webp'] as const;

export type Silhouette = 'woman' | 'man' | 'master';

export function isHexColor(value: string): boolean {
	return HEX_COLOR_PATTERN.test(value);
}

export function silhouetteFor(id: string, kind?: CharacterKind | null): Silhouette {
	if (isMaster(kind)) {
		return 'master';
	}
	let hash = 0;
	for (const char of id) {
		hash += char.charCodeAt(0);
	}
	return hash % 2 === 0 ? 'woman' : 'man';
}

export function photoBackground(color: string | null | undefined): string {
	const base = color !== null && color !== undefined && isHexColor(color) ? color : DEFAULT_COLOR;
	return `radial-gradient(ellipse 70% 55% at 50% 30%, rgba(255,255,255,.22), transparent 70%), linear-gradient(180deg, ${base}, #0b0616)`;
}

export function glowColor(color: string | null | undefined): string {
	const base = color !== null && color !== undefined && isHexColor(color) ? color : DEFAULT_COLOR;
	return `color-mix(in srgb, ${base} 80%, transparent)`;
}

export function isAvatarType(type: string): boolean {
	return (AVATAR_TYPES as readonly string[]).includes(type);
}

export function userInitial(label: string | null | undefined): string {
	const first = label?.trim().charAt(0);
	return first === undefined || first === '' ? '?' : first.toUpperCase();
}

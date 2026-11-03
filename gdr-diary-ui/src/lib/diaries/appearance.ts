import { DEFAULT_COLOR, isHexColor } from '#lib/characters/appearance.js';
import type { CropFrame } from '#lib/ui/crop.js';

export const COVER_WIDTH = 184;
export const COVER_HEIGHT = 262;

export const COVER_FRAME: CropFrame = { shape: 'rect', aspect: COVER_WIDTH / COVER_HEIGHT };

function channels(hex: string): [number, number, number] {
	return [
		Number.parseInt(hex.slice(1, 3), 16),
		Number.parseInt(hex.slice(3, 5), 16),
		Number.parseInt(hex.slice(5, 7), 16)
	];
}

export function mixHex(hex: string, target: 'white' | 'black', amount: number): string {
	const end = target === 'white' ? 255 : 0;
	return `#${channels(hex)
		.map((channel) =>
			Math.round(channel + (end - channel) * amount)
				.toString(16)
				.padStart(2, '0')
		)
		.join('')}`;
}

export interface CoverTints {
	light: string;
	dark: string;
}

export function coverTints(color: string | null | undefined): CoverTints {
	const base = color !== null && color !== undefined && isHexColor(color) ? color : DEFAULT_COLOR;
	return { light: mixHex(base, 'white', 0.14), dark: mixHex(base, 'black', 0.3) };
}

export function titleSize(title: string): number {
	const length = title.trim().length;
	if (length <= 22) {
		return 25;
	}
	return length <= 40 ? 19 : 14;
}

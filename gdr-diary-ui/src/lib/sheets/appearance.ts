import { COLOR_PRESETS, DEFAULT_COLOR } from '#lib/characters/appearance.js';

const FNV_OFFSET = 0x811c9dc5;
const FNV_PRIME = 0x01000193;

/**
 * Background colour of a sheet card, one of the presets. The backend has no colour field, so it
 * comes from the id: a sheet gets its id (random) when it is created, which makes the colour a
 * random pick at that moment, that repeats when the id is the same and may repeat between sheets.
 */
export function sheetColorFor(id: string): string {
	let hash = FNV_OFFSET;
	for (const char of id) {
		hash = Math.imul(hash ^ char.charCodeAt(0), FNV_PRIME) >>> 0;
	}
	return COLOR_PRESETS[hash % COLOR_PRESETS.length]?.hex ?? DEFAULT_COLOR;
}

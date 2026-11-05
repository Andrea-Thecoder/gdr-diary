import { COLOR_PRESETS, DEFAULT_COLOR } from '#lib/characters/appearance.js';

export function sheetColorFor(id: string): string {
	let hash = 0;
	for (const char of id) {
		hash += char.charCodeAt(0);
	}
	return COLOR_PRESETS[hash % COLOR_PRESETS.length]?.hex ?? DEFAULT_COLOR;
}

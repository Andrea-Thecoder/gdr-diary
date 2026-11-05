import type { AccountSettings } from './settings.js';

export const LINE_SPACINGS = ['compact', 'normal', 'wide'] as const;

export type LineSpacing = (typeof LINE_SPACINGS)[number];

export interface Preferences {
	font: string;
	fontSize: number;
	lineSpacing: LineSpacing;
	autoSave: boolean;
	autoSaveDelay: number;
	pageTurn: boolean;
	reduceMotion: boolean;
}

export const FONT_SIZE_MIN = 12;
export const FONT_SIZE_MAX = 24;
export const AUTO_SAVE_DELAY_MIN = 5;
export const AUTO_SAVE_DELAY_MAX = 30;

export const DEFAULT_PREFERENCES: Preferences = {
	font: 'Caveat',
	fontSize: 16,
	lineSpacing: 'normal',
	autoSave: true,
	autoSaveDelay: 5,
	pageTurn: true,
	reduceMotion: false
};

export const LINE_SPACING_LABELS: Record<LineSpacing, string> = {
	compact: 'Compatta',
	normal: 'Normale',
	wide: 'Ampia'
};

function isLineSpacing(value: unknown): value is LineSpacing {
	return LINE_SPACINGS.some((spacing) => spacing === value);
}

export function preferencesFromSettings(settings: AccountSettings | null | undefined): Preferences {
	const font = settings?.['font'];
	const fontSize = settings?.['fontSize'];
	const lineSpacing = settings?.['lineSpacing'];
	const autoSave = settings?.['autoSave'];
	const autoSaveDelay = settings?.['autoSaveDelay'];
	const pageTurn = settings?.['pageTurn'];
	const reduceMotion = settings?.['reduceMotion'];
	return {
		font: typeof font === 'string' && font.trim() !== '' ? font : DEFAULT_PREFERENCES.font,
		fontSize:
			typeof fontSize === 'number' &&
			Number.isInteger(fontSize) &&
			fontSize >= FONT_SIZE_MIN &&
			fontSize <= FONT_SIZE_MAX
				? fontSize
				: DEFAULT_PREFERENCES.fontSize,
		lineSpacing: isLineSpacing(lineSpacing) ? lineSpacing : DEFAULT_PREFERENCES.lineSpacing,
		autoSave: typeof autoSave === 'boolean' ? autoSave : DEFAULT_PREFERENCES.autoSave,
		autoSaveDelay:
			typeof autoSaveDelay === 'number' &&
			Number.isInteger(autoSaveDelay) &&
			autoSaveDelay >= AUTO_SAVE_DELAY_MIN &&
			autoSaveDelay <= AUTO_SAVE_DELAY_MAX
				? autoSaveDelay
				: DEFAULT_PREFERENCES.autoSaveDelay,
		pageTurn: typeof pageTurn === 'boolean' ? pageTurn : DEFAULT_PREFERENCES.pageTurn,
		reduceMotion:
			typeof reduceMotion === 'boolean' ? reduceMotion : DEFAULT_PREFERENCES.reduceMotion
	};
}

export function changedPreferences(saved: Preferences, draft: Preferences): AccountSettings {
	const changes: AccountSettings = {};
	for (const key of Object.keys(draft) as (keyof Preferences)[]) {
		if (draft[key] !== saved[key]) {
			changes[key] = draft[key];
		}
	}
	return changes;
}

import type { LineSpacing } from '#lib/settings/preferences.js';

export interface Typography {
	font: string;
	size: number;
	lineHeight: number;
}

export const PAGE_WIDTH = 474;
export const PAGE_HEIGHT = 648;
export const PAGE_TOP = 36;
export const PAGE_SIDE = 48;
export const BODY_HEIGHT = 540;
export const SINGLE_BREAKPOINT = 760;
export const MIN_SCALE = 0.5;

const RIBBON_OFFSET = 12;
const RIBBON_INNER_EDGE = 32;
const RIBBON_GAP = 4;

const LINE_HEIGHTS: Record<LineSpacing, number> = { compact: 30, normal: 36, wide: 45 };

const FONT_SCALES: Record<string, number> = { Caveat: 1.25 };

export function lineHeightFor(spacing: LineSpacing): number {
	return LINE_HEIGHTS[spacing];
}

export function linesPerPage(spacing: LineSpacing): number {
	return BODY_HEIGHT / LINE_HEIGHTS[spacing];
}

export function fontPixels(font: string, points: number): number {
	return Math.round(((points * 4) / 3) * (FONT_SCALES[font] ?? 1) * 10) / 10;
}

export function bookScale(available: number, single: boolean): number {
	const wanted = single ? PAGE_WIDTH : PAGE_WIDTH * 2;
	if (available <= 0) {
		return 1;
	}
	return Math.max(MIN_SCALE, Math.floor(Math.min(1, available / wanted) * 1000) / 1000);
}

export function ribbonOffset(scale: number): number {
	return Math.min(RIBBON_OFFSET, PAGE_SIDE * scale - RIBBON_GAP - RIBBON_INNER_EDGE);
}

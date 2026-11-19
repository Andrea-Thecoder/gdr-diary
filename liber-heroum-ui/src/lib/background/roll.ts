export const ROLL_START = 30;
export const ROLL_GROWTH = 1.9;
export const ROLL_MAX = 100;
export const ROLL_START_SMALL = 26;
export const ROLL_MAX_SMALL = 66;
export const ROLL_STILL = 48;
export const SMALL_SCREEN = 600;

export interface RollSizes {
	top: number;
	bottom: number;
}

export interface Scroll {
	scrolled: number;
	height: number;
	viewport: number;
	width: number;
	still: boolean;
}

export function rollDiameter(distance: number, start: number, max: number): number {
	const grown = Math.sqrt(start * start + ROLL_GROWTH * Math.max(0, distance));
	return Math.round(Math.min(max, grown) * 10) / 10;
}

export function rollSizes(scroll: Scroll): RollSizes {
	if (scroll.still) {
		return { top: ROLL_STILL, bottom: ROLL_STILL };
	}
	const small = scroll.width < SMALL_SCREEN;
	const start = small ? ROLL_START_SMALL : ROLL_START;
	const max = small ? ROLL_MAX_SMALL : ROLL_MAX;
	const remaining = Math.max(0, scroll.height - scroll.viewport - scroll.scrolled);
	return {
		top: rollDiameter(scroll.scrolled, start, max),
		bottom: rollDiameter(remaining, start, max)
	};
}

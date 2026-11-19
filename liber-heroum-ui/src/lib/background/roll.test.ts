import { describe, expect, it } from 'vitest';
import {
	ROLL_MAX,
	ROLL_MAX_SMALL,
	ROLL_START,
	ROLL_START_SMALL,
	ROLL_STILL,
	rollDiameter,
	rollSizes,
	type Scroll
} from './roll.js';

function scroll(overrides: Partial<Scroll> = {}): Scroll {
	return { scrolled: 0, height: 5000, viewport: 800, width: 1280, still: false, ...overrides };
}

describe('rollDiameter', () => {
	it('starts at the smallest size when nothing is rolled', () => {
		expect(rollDiameter(0, ROLL_START, ROLL_MAX)).toBe(ROLL_START);
	});

	it('grows as the distance grows', () => {
		expect(rollDiameter(1000, ROLL_START, ROLL_MAX)).toBeGreaterThan(
			rollDiameter(100, ROLL_START, ROLL_MAX)
		);
	});

	it('never goes above the largest size', () => {
		expect(rollDiameter(1_000_000, ROLL_START, ROLL_MAX)).toBe(ROLL_MAX);
	});

	it('treats a negative distance as none', () => {
		expect(rollDiameter(-50, ROLL_START, ROLL_MAX)).toBe(ROLL_START);
	});
});

describe('rollSizes', () => {
	it('shows the small top roll and the big bottom roll at the start of a long page', () => {
		const sizes = rollSizes(scroll({ scrolled: 0, height: 20000 }));
		expect(sizes).toEqual({ top: ROLL_START, bottom: ROLL_MAX });
	});

	it('shows the big top roll and the small bottom roll at the end of a long page', () => {
		const sizes = rollSizes(scroll({ scrolled: 19200, height: 20000 }));
		expect(sizes).toEqual({ top: ROLL_MAX, bottom: ROLL_START });
	});

	it('shows two small rolls on a page that fits the screen', () => {
		expect(rollSizes(scroll({ height: 700 }))).toEqual({ top: ROLL_START, bottom: ROLL_START });
	});

	it('uses smaller rolls under 600 px', () => {
		const sizes = rollSizes(scroll({ scrolled: 0, height: 20000, width: 390 }));
		expect(sizes).toEqual({ top: ROLL_START_SMALL, bottom: ROLL_MAX_SMALL });
	});

	it('keeps both rolls the same fixed size when motion is reduced', () => {
		expect(rollSizes(scroll({ scrolled: 900, still: true }))).toEqual({
			top: ROLL_STILL,
			bottom: ROLL_STILL
		});
	});
});

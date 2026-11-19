import { describe, expect, it } from 'vitest';
import {
	MAX_ZOOM,
	MIN_ZOOM,
	baseWindow,
	clampView,
	frameAspect,
	imagePlacement,
	initialView,
	outputSize,
	panView,
	sourceRect,
	windowAt,
	zoomView
} from '#lib/ui/crop.js';

const wide = { width: 2000, height: 1000 };
const tall = { width: 1000, height: 2000 };

describe('frameAspect', () => {
	it('is square for the round frame', () => {
		expect(frameAspect({ shape: 'round' })).toBe(1);
	});

	it('is the given one for a rectangle', () => {
		expect(frameAspect({ shape: 'rect', aspect: 1.5 })).toBe(1.5);
	});
});

describe('baseWindow', () => {
	it('uses the full height of a wide image', () => {
		expect(baseWindow(wide, 1)).toEqual({ width: 1000, height: 1000 });
	});

	it('uses the full width of a tall image', () => {
		expect(baseWindow(tall, 1)).toEqual({ width: 1000, height: 1000 });
	});

	it('follows a rectangular frame', () => {
		expect(baseWindow(wide, 2)).toEqual({ width: 2000, height: 1000 });
		expect(baseWindow(wide, 4)).toEqual({ width: 2000, height: 500 });
	});
});

describe('initialView', () => {
	it('starts centered and not zoomed', () => {
		expect(initialView(wide)).toEqual({ zoom: MIN_ZOOM, centerX: 1000, centerY: 500 });
	});

	it('shows the largest possible part of the image', () => {
		const rect = sourceRect(initialView(wide), wide, 1);
		expect(rect).toEqual({ x: 500, y: 0, width: 1000, height: 1000 });
	});
});

describe('clampView', () => {
	it('keeps the frame inside the image', () => {
		const view = clampView({ zoom: 1, centerX: -50, centerY: 9999 }, wide, 1);
		expect(sourceRect(view, wide, 1)).toEqual({ x: 0, y: 0, width: 1000, height: 1000 });
	});

	it('limits the zoom', () => {
		expect(clampView({ zoom: 0.2, centerX: 1000, centerY: 500 }, wide, 1).zoom).toBe(MIN_ZOOM);
		expect(clampView({ zoom: 99, centerX: 1000, centerY: 500 }, wide, 1).zoom).toBe(MAX_ZOOM);
	});

	it('cannot move along the axis that is already fully shown', () => {
		const view = clampView({ zoom: 1, centerX: 1000, centerY: 123 }, wide, 1);
		expect(view.centerY).toBe(500);
	});
});

describe('panView', () => {
	it('moves the picture with the finger', () => {
		const start = initialView(wide);
		const moved = panView(start, wide, 1, { x: 0.1, y: 0 });
		expect(moved.centerX).toBe(900);
	});

	it('stops at the edge of the image', () => {
		const moved = panView(initialView(wide), wide, 1, { x: 5, y: 5 });
		expect(sourceRect(moved, wide, 1).x).toBe(0);
		expect(sourceRect(moved, wide, 1).y).toBe(0);
	});

	it('moves less when zoomed in, covering the same part of the frame', () => {
		const zoomed = zoomView(initialView(wide), wide, 1, 2);
		const moved = panView(zoomed, wide, 1, { x: 0.1, y: 0 });
		expect(zoomed.centerX - moved.centerX).toBeCloseTo(50);
	});
});

describe('zoomView', () => {
	it('keeps the center when zooming without an anchor', () => {
		const view = zoomView(initialView(wide), wide, 1, 2);
		expect(view).toEqual({ zoom: 2, centerX: 1000, centerY: 500 });
		expect(windowAt(view, wide, 1)).toEqual({ width: 500, height: 500 });
	});

	it('keeps the point under the anchor still', () => {
		const start = { zoom: 1, centerX: 1000, centerY: 500 };
		const anchor = { x: 0.25, y: 0 };
		const before = sourceRect(start, wide, 1);
		const pointBefore = before.x + (0.5 + anchor.x) * before.width;

		const zoomed = zoomView(start, wide, 1, 2, anchor);
		const after = sourceRect(zoomed, wide, 1);
		const pointAfter = after.x + (0.5 + anchor.x) * after.width;

		expect(pointAfter).toBeCloseTo(pointBefore);
	});

	it('does not leave the image when zooming out near an edge', () => {
		const corner = { zoom: 3, centerX: 0, centerY: 0 };
		const view = zoomView(corner, wide, 1, 1.2, { x: -0.5, y: -0.5 });
		const rect = sourceRect(view, wide, 1);
		expect(rect.x).toBeGreaterThanOrEqual(0);
		expect(rect.y).toBeGreaterThanOrEqual(0);
		expect(rect.x + rect.width).toBeLessThanOrEqual(wide.width);
		expect(rect.y + rect.height).toBeLessThanOrEqual(wide.height);
	});

	it('stays within the zoom limits', () => {
		expect(zoomView(initialView(wide), wide, 1, 100).zoom).toBe(MAX_ZOOM);
		expect(zoomView(initialView(wide), wide, 1, 0).zoom).toBe(MIN_ZOOM);
	});
});

describe('sourceRect', () => {
	it('has the aspect of the frame', () => {
		const rect = sourceRect(zoomView(initialView(wide), wide, 1.5, 2.5), wide, 1.5);
		expect(rect.width / rect.height).toBeCloseTo(1.5);
	});

	it('works for a portrait frame on a landscape image', () => {
		const aspect = 184 / 262;
		const rect = sourceRect(initialView(wide), wide, aspect);
		expect(rect.height).toBe(1000);
		expect(rect.width / rect.height).toBeCloseTo(aspect);
	});
});

describe('outputSize', () => {
	it('keeps the size of a small crop', () => {
		expect(outputSize({ width: 300, height: 200 }, 1200)).toEqual({ width: 300, height: 200 });
	});

	it('shrinks a large crop keeping its shape', () => {
		expect(outputSize({ width: 3000, height: 2000 }, 1200)).toEqual({ width: 1200, height: 800 });
		expect(outputSize({ width: 2000, height: 3000 }, 1200)).toEqual({ width: 800, height: 1200 });
	});

	it('never gives a zero side', () => {
		expect(outputSize({ width: 0.2, height: 0.2 }, 1200)).toEqual({ width: 1, height: 1 });
	});
});

describe('imagePlacement', () => {
	it('fills the frame along the constrained side when not zoomed', () => {
		const placement = imagePlacement(initialView(wide), wide, 1);
		expect(placement.width).toBeCloseTo(200);
		expect(placement.left).toBeCloseTo(-50);
		expect(placement.top).toBeCloseTo(0);
	});

	it('scales with the zoom', () => {
		const placement = imagePlacement(zoomView(initialView(wide), wide, 1, 2), wide, 1);
		expect(placement.width).toBeCloseTo(400);
	});

	it('puts the visible part exactly on the frame', () => {
		const view = panView(zoomView(initialView(tall), tall, 1.5, 2), tall, 1.5, { x: 0.2, y: -0.1 });
		const placement = imagePlacement(view, tall, 1.5);
		const rect = sourceRect(view, tall, 1.5);
		expect(placement.left).toBeCloseTo(-(rect.x / rect.width) * 100);
		expect(placement.top).toBeCloseTo(-(rect.y / rect.height) * 100);
	});
});

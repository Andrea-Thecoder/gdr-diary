export type CropFrame = { shape: 'round' } | { shape: 'rect'; aspect: number };

export interface Size {
	width: number;
	height: number;
}

export interface Point {
	x: number;
	y: number;
}

export interface Rect extends Point, Size {}

export interface CropView {
	zoom: number;
	centerX: number;
	centerY: number;
}

export interface ImagePlacement {
	width: number;
	left: number;
	top: number;
}

export const MIN_ZOOM = 1;
export const MAX_ZOOM = 4;
export const MAX_OUTPUT_SIDE = 1200;

function clamp(value: number, min: number, max: number): number {
	return Math.min(Math.max(value, min), max);
}

export function frameAspect(frame: CropFrame): number {
	return frame.shape === 'round' ? 1 : frame.aspect;
}

export function baseWindow(image: Size, aspect: number): Size {
	return image.width / image.height > aspect
		? { width: image.height * aspect, height: image.height }
		: { width: image.width, height: image.width / aspect };
}

export function windowAt(view: CropView, image: Size, aspect: number): Size {
	const base = baseWindow(image, aspect);
	return { width: base.width / view.zoom, height: base.height / view.zoom };
}

export function initialView(image: Size): CropView {
	return { zoom: MIN_ZOOM, centerX: image.width / 2, centerY: image.height / 2 };
}

export function clampView(view: CropView, image: Size, aspect: number): CropView {
	const zoom = clamp(view.zoom, MIN_ZOOM, MAX_ZOOM);
	const visible = windowAt({ ...view, zoom }, image, aspect);
	const halfWidth = visible.width / 2;
	const halfHeight = visible.height / 2;
	return {
		zoom,
		centerX: clamp(view.centerX, halfWidth, image.width - halfWidth),
		centerY: clamp(view.centerY, halfHeight, image.height - halfHeight)
	};
}

export function panView(view: CropView, image: Size, aspect: number, drag: Point): CropView {
	const visible = windowAt(view, image, aspect);
	return clampView(
		{
			...view,
			centerX: view.centerX - drag.x * visible.width,
			centerY: view.centerY - drag.y * visible.height
		},
		image,
		aspect
	);
}

export function zoomView(
	view: CropView,
	image: Size,
	aspect: number,
	zoom: number,
	anchor: Point = { x: 0, y: 0 }
): CropView {
	const next = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
	const before = windowAt(view, image, aspect);
	const after = windowAt({ ...view, zoom: next }, image, aspect);
	const pointX = view.centerX + anchor.x * before.width;
	const pointY = view.centerY + anchor.y * before.height;
	return clampView(
		{
			zoom: next,
			centerX: pointX - anchor.x * after.width,
			centerY: pointY - anchor.y * after.height
		},
		image,
		aspect
	);
}

export function sourceRect(view: CropView, image: Size, aspect: number): Rect {
	const visible = windowAt(view, image, aspect);
	return {
		x: view.centerX - visible.width / 2,
		y: view.centerY - visible.height / 2,
		width: visible.width,
		height: visible.height
	};
}

export function outputSize(rect: Size, maxSide: number): Size {
	const scale = Math.min(1, maxSide / Math.max(rect.width, rect.height));
	return {
		width: Math.max(1, Math.round(rect.width * scale)),
		height: Math.max(1, Math.round(rect.height * scale))
	};
}

export function imagePlacement(view: CropView, image: Size, aspect: number): ImagePlacement {
	const visible = windowAt(view, image, aspect);
	return {
		width: (image.width / visible.width) * 100,
		left: 50 - (view.centerX / visible.width) * 100,
		top: 50 - (view.centerY / visible.height) * 100
	};
}

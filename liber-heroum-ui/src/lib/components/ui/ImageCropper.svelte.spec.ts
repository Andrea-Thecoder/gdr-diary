import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { CropFrame } from '#lib/ui/crop.js';
import { initialView } from '#lib/ui/crop.js';
import { required } from '#lib/test-support.js';
import { samplePng } from '#lib/ui/test-image.js';
import ImageCropper from './ImageCropper.svelte';

const SIZE = { width: 120, height: 80 };

async function show(frame: CropFrame = { shape: 'rect', aspect: 1 }): Promise<HTMLElement> {
	const url = URL.createObjectURL(await samplePng('foto.png', SIZE.width, SIZE.height));
	render(ImageCropper, {
		src: url,
		size: SIZE,
		frame,
		view: initialView(SIZE),
		label: 'Inquadratura di prova'
	});
	await expect.element(page.getByRole('button', { name: 'Inquadratura di prova' })).toBeVisible();
	return page.getByRole('button', { name: 'Inquadratura di prova' }).element() as HTMLElement;
}

function image(frame: HTMLElement): HTMLImageElement {
	return required(frame.querySelector('img'), 'Immagine');
}

function percent(value: string): number {
	return Number.parseFloat(value);
}

function zoomOutput(): string {
	return (
		page
			.getByRole('slider', { name: 'Ingrandimento' })
			.element()
			.parentElement?.querySelector('output')?.textContent ?? ''
	);
}

describe('ImageCropper', () => {
	beforeEach(async () => {
		await page.viewport(390, 844);
	});

	it('starts with the picture centered and not zoomed', async () => {
		const frame = await show();

		expect(zoomOutput()).toBe('100 %');
		expect(percent(image(frame).style.width)).toBeCloseTo(150);
		expect(percent(image(frame).style.left)).toBeCloseTo(-25);
		expect(percent(image(frame).style.top)).toBeCloseTo(0);
	});

	it('gives the frame the requested shape', async () => {
		const frame = await show({ shape: 'rect', aspect: 3 / 2 });

		const box = frame.getBoundingClientRect();
		expect(box.width / box.height).toBeCloseTo(1.5, 1);
	});

	it('draws a round frame as a circle', async () => {
		const frame = await show({ shape: 'round' });

		const mask = frame.querySelector('[aria-hidden="true"]');
		expect(mask?.className).toContain('rounded-full');
		const box = frame.getBoundingClientRect();
		expect(box.width).toBeCloseTo(box.height, 0);
	});

	it('zooms with the slider', async () => {
		await show();

		await page.getByRole('slider', { name: 'Ingrandimento' }).fill('250');

		expect(zoomOutput()).toBe('250 %');
	});

	it('enlarges the picture beyond the frame even when the page caps image width', async () => {
		const style = document.createElement('style');
		style.textContent = 'img { max-width: 100%; height: auto; }';
		document.head.append(style);
		try {
			const frame = await show();
			await expect
				.element(page.getByRole('button', { name: 'Inquadratura di prova' }))
				.toBeVisible();
			await page.getByRole('slider', { name: 'Ingrandimento' }).fill('250');

			const box = frame.getBoundingClientRect();
			const picture = image(frame).getBoundingClientRect();
			expect(picture.width).toBeGreaterThan(box.width * 2.4);
			expect(picture.left).toBeLessThanOrEqual(box.left);
			expect(picture.right).toBeGreaterThanOrEqual(box.right);
		} finally {
			style.remove();
		}
	});

	it('zooms with the keyboard and moves with the arrows', async () => {
		const frame = await show();
		frame.focus();

		await userEvent.keyboard('+');
		expect(zoomOutput()).toBe('110 %');
		const before = percent(image(frame).style.left);
		await userEvent.keyboard('{ArrowRight}');
		expect(percent(image(frame).style.left)).toBeLessThan(before);
		await userEvent.keyboard('-');
		expect(zoomOutput()).toBe('100 %');
	});

	it('does not move past the edge of the picture', async () => {
		const frame = await show();
		frame.focus();

		await userEvent.keyboard('{ArrowLeft}'.repeat(40));

		expect(percent(image(frame).style.left)).toBeCloseTo(0);
	});

	it('zooms with the wheel without scrolling the page', async () => {
		const frame = await show();
		const box = frame.getBoundingClientRect();
		const event = new WheelEvent('wheel', {
			deltaY: -300,
			clientX: box.left + box.width / 2,
			clientY: box.top + box.height / 2,
			bubbles: true,
			cancelable: true
		});

		frame.dispatchEvent(event);

		expect(event.defaultPrevented).toBe(true);
		await expect.poll(zoomOutput).not.toBe('100 %');
	});

	it('moves the picture when it is dragged', async () => {
		const frame = await show();
		const before = percent(image(frame).style.left);

		await userEvent.dragAndDrop(page.elementLocator(frame), page.elementLocator(frame), {
			sourcePosition: { x: 80, y: 40 },
			targetPosition: { x: 20, y: 40 }
		});

		expect(percent(image(frame).style.left)).toBeLessThan(before);
	});

	it('keeps slider and frame reachable with a thumb on a narrow phone', async () => {
		await page.viewport(320, 640);
		const frame = await show();

		const slider = page.getByRole('slider', { name: 'Ingrandimento' }).element();
		expect(slider.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
		expect(frame.getBoundingClientRect().width).toBeGreaterThan(200);
		expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(320);
	});
});

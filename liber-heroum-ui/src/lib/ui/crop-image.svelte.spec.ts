import { describe, expect, it } from 'vitest';
import { loadImage, renderCrop } from '#lib/ui/crop-image.js';

async function splitImage(): Promise<string> {
	const canvas = document.createElement('canvas');
	canvas.width = 100;
	canvas.height = 50;
	const context = canvas.getContext('2d');
	if (context === null) {
		throw new Error('Canvas non disponibile.');
	}
	context.fillStyle = '#ff0000';
	context.fillRect(0, 0, 50, 50);
	context.fillStyle = '#0000ff';
	context.fillRect(50, 0, 50, 50);
	const blob = await new Promise<Blob | null>((resolve) => {
		canvas.toBlob(resolve, 'image/png');
	});
	if (blob === null) {
		throw new Error('Impossibile creare l’immagine di prova.');
	}
	return URL.createObjectURL(blob);
}

async function centerPixel(file: File): Promise<Uint8ClampedArray> {
	const bitmap = await createImageBitmap(file);
	const canvas = document.createElement('canvas');
	canvas.width = bitmap.width;
	canvas.height = bitmap.height;
	const context = canvas.getContext('2d');
	if (context === null) {
		throw new Error('Canvas non disponibile.');
	}
	context.drawImage(bitmap, 0, 0);
	return context.getImageData(Math.floor(bitmap.width / 2), Math.floor(bitmap.height / 2), 1, 1)
		.data;
}

describe('loadImage', () => {
	it('gives the decoded image with its real size', async () => {
		const url = await splitImage();

		const image = await loadImage(url);

		expect(image.naturalWidth).toBe(100);
		expect(image.naturalHeight).toBe(50);
		URL.revokeObjectURL(url);
	});

	it('rejects a file that is not an image', async () => {
		const url = URL.createObjectURL(new Blob(['non sono un’immagine'], { type: 'image/png' }));

		await expect(loadImage(url)).rejects.toThrow('Immagine non leggibile.');
		URL.revokeObjectURL(url);
	});
});

describe('renderCrop', () => {
	it('keeps only the chosen part, as a WebP with the requested size', async () => {
		const url = await splitImage();
		const image = await loadImage(url);

		const file = await renderCrop(
			image,
			{ x: 50, y: 0, width: 50, height: 50 },
			{ width: 40, height: 40 },
			'foto.png'
		);

		expect(file.name).toBe('foto.webp');
		expect(file.type).toBe('image/webp');
		const bitmap = await createImageBitmap(file);
		expect([bitmap.width, bitmap.height]).toEqual([40, 40]);
		const [red = 255, , blue = 0] = await centerPixel(file);
		expect(blue).toBeGreaterThan(200);
		expect(red).toBeLessThan(60);
		URL.revokeObjectURL(url);
	});

	it('takes the other part when the rectangle moves', async () => {
		const url = await splitImage();
		const image = await loadImage(url);

		const file = await renderCrop(
			image,
			{ x: 0, y: 0, width: 50, height: 50 },
			{ width: 20, height: 20 },
			'foto.png'
		);

		const [red = 0, , blue = 255] = await centerPixel(file);
		expect(red).toBeGreaterThan(200);
		expect(blue).toBeLessThan(60);
		URL.revokeObjectURL(url);
	});
});

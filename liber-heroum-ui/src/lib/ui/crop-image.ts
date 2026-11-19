import type { Rect, Size } from './crop.js';

const OUTPUT_QUALITY = 0.9;

const EXTENSIONS: Record<string, string> = {
	'image/webp': 'webp',
	'image/png': 'png'
};

export function loadImage(url: string): Promise<HTMLImageElement> {
	return new Promise((resolve, reject) => {
		const image = new Image();
		image.onload = () => {
			if (image.naturalWidth > 0 && image.naturalHeight > 0) {
				resolve(image);
			} else {
				reject(new Error('Immagine vuota.'));
			}
		};
		image.onerror = () => {
			reject(new Error('Immagine non leggibile.'));
		};
		image.src = url;
	});
}

export function croppedName(original: string, extension: string): string {
	const base = original.replace(/\.[^./\\]+$/, '').trim();
	return `${base === '' ? 'immagine' : base}.${extension}`;
}

export async function renderCrop(
	image: HTMLImageElement,
	source: Rect,
	output: Size,
	originalName: string
): Promise<File> {
	const canvas = document.createElement('canvas');
	canvas.width = output.width;
	canvas.height = output.height;
	const context = canvas.getContext('2d');
	if (context === null) {
		throw new Error('Non riesco a preparare l’immagine.');
	}
	context.imageSmoothingQuality = 'high';
	context.drawImage(
		image,
		source.x,
		source.y,
		source.width,
		source.height,
		0,
		0,
		output.width,
		output.height
	);
	const blob = await new Promise<Blob | null>((resolve) => {
		canvas.toBlob(resolve, 'image/webp', OUTPUT_QUALITY);
	});
	const extension = blob === null ? undefined : EXTENSIONS[blob.type];
	if (blob === null || extension === undefined) {
		throw new Error('Non riesco a salvare l’immagine ritagliata.');
	}
	return new File([blob], croppedName(originalName, extension), { type: blob.type });
}

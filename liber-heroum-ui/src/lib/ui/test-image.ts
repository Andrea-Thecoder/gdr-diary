import { page } from 'vitest/browser';
import { expect } from 'vitest';

export async function samplePng(name = 'foto.png', width = 120, height = 80): Promise<File> {
	const canvas = document.createElement('canvas');
	canvas.width = width;
	canvas.height = height;
	const context = canvas.getContext('2d');
	if (context === null) {
		throw new Error('Canvas non disponibile.');
	}
	const gradient = context.createLinearGradient(0, 0, width, height);
	gradient.addColorStop(0, '#c0392b');
	gradient.addColorStop(1, '#2980b9');
	context.fillStyle = gradient;
	context.fillRect(0, 0, width, height);
	const blob = await new Promise<Blob | null>((resolve) => {
		canvas.toBlob(resolve, 'image/png');
	});
	if (blob === null) {
		throw new Error('Impossibile creare l’immagine di prova.');
	}
	return new File([blob], name, { type: 'image/png' });
}

export async function chooseAndCrop(file: File): Promise<void> {
	await page.getByLabelText('File dell’immagine').upload(file);
	await page.getByRole('button', { name: 'Ritaglia' }).click();
	await expect.element(page.getByRole('button', { name: 'Ritaglia' })).not.toBeInTheDocument();
}

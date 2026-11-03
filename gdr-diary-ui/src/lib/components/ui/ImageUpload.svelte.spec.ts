import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createRawSnippet } from 'svelte';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import { AVATAR_TYPES } from '#lib/characters/appearance.js';
import type { CropFrame } from '#lib/ui/crop.js';
import { samplePng } from '#lib/ui/test-image.js';
import ImageUpload from './ImageUpload.svelte';

function open(crop?: CropFrame): { onfile: ReturnType<typeof vi.fn<(file: File) => void>> } {
	const onfile = vi.fn<(file: File) => void>();
	render(ImageUpload, {
		preview: createRawSnippet(() => ({ render: () => '<span>anteprima</span>' })),
		hasImage: false,
		accept: AVATAR_TYPES,
		chooseLabel: 'Carica un’immagine',
		changeLabel: 'Cambia immagine',
		hint: 'Facoltativa.',
		rejectMessage: 'Formato non supportato',
		crop,
		onfile,
		onremove: vi.fn()
	});
	return { onfile };
}

function handedOver(onfile: { mock: { calls: unknown[][] } }): File {
	const file = onfile.mock.calls[0]?.[0];
	if (!(file instanceof File)) {
		throw new Error('Nessun file consegnato.');
	}
	return file;
}

async function sizeOf(file: File): Promise<{ width: number; height: number }> {
	const bitmap = await createImageBitmap(file);
	return { width: bitmap.width, height: bitmap.height };
}

const upload = page.getByLabelText('File dell’immagine');
const dialogTitle = page.getByRole('heading', { name: 'Inquadra l’immagine' });
const confirm = page.getByRole('button', { name: 'Ritaglia' });
const frame = page.getByRole('button', { name: 'Inquadratura dell’immagine' });

describe('ImageUpload with a crop frame', () => {
	beforeEach(async () => {
		await page.viewport(390, 844);
	});

	it('hands the file over untouched when there is no frame', async () => {
		const { onfile } = open();
		const file = await samplePng('foto.png');

		await upload.upload(file);

		expect(onfile).toHaveBeenCalledWith(file);
		expect(dialogTitle.elements()).toHaveLength(0);
	});

	it('asks for the framing before handing over the file', async () => {
		const { onfile } = open({ shape: 'round' });

		await upload.upload(await samplePng('foto.png'));

		await expect.element(dialogTitle).toBeVisible();
		expect(onfile).not.toHaveBeenCalled();
	});

	it('hands over a square WebP for a round frame', async () => {
		const { onfile } = open({ shape: 'round' });
		await upload.upload(await samplePng('foto.png', 300, 200));

		await confirm.click();

		await expect.poll(() => onfile.mock.calls.length).toBe(1);
		const file = handedOver(onfile);
		expect(file.name).toBe('foto.webp');
		expect(file.type).toBe('image/webp');
		expect(await sizeOf(file)).toEqual({ width: 200, height: 200 });
		await expect.element(dialogTitle).not.toBeInTheDocument();
	});

	it('follows the shape of a rectangular frame', async () => {
		const { onfile } = open({ shape: 'rect', aspect: 184 / 262 });
		await upload.upload(await samplePng('copertina.png', 400, 300));

		await confirm.click();

		await expect.poll(() => onfile.mock.calls.length).toBe(1);
		const { width, height } = await sizeOf(handedOver(onfile));
		expect(width / height).toBeCloseTo(184 / 262, 1);
	});

	it('never hands over more pixels than needed', async () => {
		const { onfile } = open({ shape: 'rect', aspect: 3 / 2 });
		await upload.upload(await samplePng('grande.png', 3000, 2000));

		await confirm.click();

		await expect.poll(() => onfile.mock.calls.length).toBe(1);
		expect(await sizeOf(handedOver(onfile))).toEqual({ width: 1200, height: 800 });
	});

	it('hands over nothing when the framing is cancelled', async () => {
		const { onfile } = open({ shape: 'round' });
		await upload.upload(await samplePng('foto.png'));

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(dialogTitle).not.toBeInTheDocument();
		expect(onfile).not.toHaveBeenCalled();
	});

	it('lets the same file be chosen again after cancelling', async () => {
		open({ shape: 'round' });
		const file = await samplePng('foto.png');
		await upload.upload(file);
		await page.getByRole('button', { name: 'Annulla' }).click();
		await expect.element(dialogTitle).not.toBeInTheDocument();

		await upload.upload(file);

		await expect.element(dialogTitle).toBeVisible();
	});

	it('closes with Escape without handing over a file', async () => {
		const { onfile } = open({ shape: 'round' });
		await upload.upload(await samplePng('foto.png'));
		await expect.element(dialogTitle).toBeVisible();

		await userEvent.keyboard('{Escape}');

		await expect.element(dialogTitle).not.toBeInTheDocument();
		expect(onfile).not.toHaveBeenCalled();
	});

	it('refuses an unsupported file without opening the framing', async () => {
		const { onfile } = open({ shape: 'round' });

		await upload.upload(new File(['x'], 'animata.gif', { type: 'image/gif' }));

		await expect.element(page.getByRole('alert')).toHaveTextContent('Formato non supportato');
		expect(dialogTitle.elements()).toHaveLength(0);
		expect(onfile).not.toHaveBeenCalled();
	});

	it('says when the image cannot be read and does not allow confirming', async () => {
		const { onfile } = open({ shape: 'round' });

		await upload.upload(new File(['x'], 'rotta.png', { type: 'image/png' }));

		await expect.element(page.getByRole('alert')).toHaveTextContent('Non riesco a leggere');
		await expect.element(confirm).toBeDisabled();
		expect(onfile).not.toHaveBeenCalled();
	});

	it.each(['Ritaglia', 'Annulla', 'Chiudi'])(
		'gives the %s button a touch-sized target on a narrow phone',
		async (name) => {
			await page.viewport(320, 640);
			open({ shape: 'rect', aspect: 3 / 2 });
			await upload.upload(await samplePng('foto.png'));
			await expect.element(frame).toBeVisible();

			const box = page.getByRole('button', { name }).element().getBoundingClientRect();

			expect([box.height >= 44, box.width >= 44]).toEqual([true, true]);
		}
	);

	it('does not scroll the page sideways on a narrow phone', async () => {
		await page.viewport(320, 640);
		open({ shape: 'rect', aspect: 3 / 2 });
		await upload.upload(await samplePng('foto.png'));
		await expect.element(frame).toBeVisible();

		expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(320);
	});

	it('fits a short landscape phone by scrolling inside the dialog', async () => {
		await page.viewport(667, 375);
		open({ shape: 'rect', aspect: 184 / 262 });
		await upload.upload(await samplePng('foto.png'));
		await expect.element(frame).toBeVisible();

		const dialog = page.getByRole('dialog').element();
		const box = dialog.getBoundingClientRect();
		expect(box.top).toBeGreaterThanOrEqual(0);
		expect(box.bottom).toBeLessThanOrEqual(375);
		expect(dialog.scrollHeight).toBeGreaterThan(dialog.clientHeight);
		const button = confirm.element();
		button.scrollIntoView();
		const reached = button.getBoundingClientRect();
		expect(reached.top).toBeGreaterThanOrEqual(box.top);
		expect(reached.bottom).toBeLessThanOrEqual(box.bottom);
	});
});

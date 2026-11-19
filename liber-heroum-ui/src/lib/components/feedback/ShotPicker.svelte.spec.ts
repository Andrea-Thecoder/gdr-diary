import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import ShotPicker from './ShotPicker.svelte';

function png(name: string, size = 480 * 1024): File {
	return new File([new Uint8Array(size)], name, { type: 'image/png' });
}

const props = { shots: [] as File[], problem: null, onadd: vi.fn(), onremove: vi.fn() };

describe('ShotPicker', () => {
	it('invites to drop an image or choose a file when there is none', async () => {
		render(ShotPicker, props);

		await expect.element(page.getByText('Trascina qui un’immagine oppure')).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Scegli un file' })).toBeVisible();
	});

	it('states the limits and the warning about the diary texts', async () => {
		render(ShotPicker, props);

		await expect
			.element(
				page.getByText('PNG, JPEG o WebP, al massimo 3 e fino a 5 MB in tutto', { exact: false })
			)
			.toBeVisible();
		await expect.element(page.getByText('lo leggo anch’io', { exact: false })).toBeVisible();
	});

	it('passes the chosen files to the form', async () => {
		const onadd = vi.fn();
		render(ShotPicker, { ...props, onadd });

		await page
			.getByLabelText('Scegli le schermate da allegare')
			.upload(new File([new Uint8Array(10)], 'a.png', { type: 'image/png' }));

		expect(onadd).toHaveBeenCalledOnce();
		expect(onadd.mock.calls[0]?.[0]).toHaveLength(1);
	});

	it('shows the name and the size of every file', async () => {
		render(ShotPicker, { ...props, shots: [png('schermata-diari.png')] });

		await expect.element(page.getByText('schermata-diari.png')).toBeVisible();
		await expect.element(page.getByText('480 KB')).toBeVisible();
	});

	it('shows a small picture of every image, each its own', async () => {
		render(ShotPicker, { ...props, shots: [png('a.png'), png('b.png'), png('c.png')] });

		await vi.waitFor(() => {
			expect(document.querySelectorAll('ul img')).toHaveLength(3);
		});
		const sources = Array.from(document.querySelectorAll('ul img')).map((image) =>
			image.getAttribute('src')
		);

		expect(new Set(sources).size).toBe(3);
	});

	it('removes a file with its own button', async () => {
		const onremove = vi.fn();
		render(ShotPicker, { ...props, shots: [png('a.png'), png('b.png')], onremove });

		await page.getByRole('button', { name: 'Rimuovi b.png' }).click();

		expect(onremove).toHaveBeenCalledWith(1);
	});

	it('offers to add another one while there is room', async () => {
		render(ShotPicker, { ...props, shots: [png('a.png')] });

		await expect.element(page.getByRole('button', { name: 'Aggiungi un’altra' })).toBeVisible();
	});

	it('does not offer another one when there are already three', () => {
		render(ShotPicker, { ...props, shots: [png('a.png'), png('b.png'), png('c.png')] });

		expect(page.getByRole('button', { name: 'Aggiungi un’altra' }).elements()).toHaveLength(0);
	});

	it('shows what was refused', async () => {
		render(ShotPicker, { ...props, problem: '«a.pdf» non è un’immagine.' });

		await expect.element(page.getByRole('alert')).toHaveTextContent('«a.pdf» non è un’immagine.');
	});

	it('keeps the remove buttons 44 px wide for the thumb', () => {
		render(ShotPicker, { ...props, shots: [png('a.png')] });

		const box = page
			.getByRole('button', { name: 'Rimuovi a.png' })
			.element()
			.getBoundingClientRect();

		expect(Math.min(box.width, box.height)).toBeGreaterThanOrEqual(44);
	});
});

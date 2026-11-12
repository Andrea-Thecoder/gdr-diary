import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import { fillablePdfUrl } from '#lib/pdf/test-support.js';
import SheetPile from './SheetPile.svelte';

describe('pile of sheets', () => {
	it('shows the first page of the PDF in miniature on top of the pile', async () => {
		render(SheetPile, { title: 'Scheda livello 5', color: '#7C2A22', pdfUrl: fillablePdfUrl() });

		const thumbnail = page.getByTestId('sheet-thumbnail');
		await expect.element(thumbnail, { timeout: 15_000 }).toBeVisible();
		await expect.element(thumbnail).toHaveAttribute('src', expect.stringMatching(/^blob:/));
	});

	it('keeps the drawing when the PDF cannot be read', async () => {
		render(SheetPile, {
			title: 'Scheda livello 5',
			color: '#7C2A22',
			pdfUrl: 'http://localhost:1/non-esiste.pdf'
		});

		await expect
			.element(page.getByRole('img', { name: 'Anteprima di Scheda livello 5: scheda in PDF' }))
			.toBeVisible();
		expect(page.getByTestId('sheet-thumbnail').elements()).toHaveLength(0);
	});

	it('shows only the drawing when there is no PDF address', async () => {
		render(SheetPile, { title: 'Scheda livello 5', color: '#7C2A22' });

		await expect
			.element(page.getByRole('img', { name: 'Anteprima di Scheda livello 5: scheda in PDF' }))
			.toBeVisible();
		expect(page.getByTestId('sheet-thumbnail').elements()).toHaveLength(0);
	});
});

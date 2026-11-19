import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import ClosedBook from './ClosedBook.svelte';

describe('ClosedBook', () => {
	beforeEach(async () => {
		await page.viewport(1280, 800);
	});

	it('is an image named after the diary', async () => {
		render(ClosedBook, { title: 'Cronache', color: '#3B2A6B', coverUrl: null });

		await expect
			.element(page.getByRole('img', { name: 'Copertina di Cronache: diario chiuso' }))
			.toBeVisible();
	});

	it('writes the title on the cover when there is no image', async () => {
		render(ClosedBook, { title: 'Cronache', color: '#3B2A6B', coverUrl: null });

		await expect.element(page.getByText('Cronache', { exact: true })).toBeVisible();
	});

	it('shows the uploaded cover and not the title', () => {
		render(ClosedBook, { title: 'Cronache', color: '#3B2A6B', coverUrl: 'http://img.test/c.png' });

		const book = page.getByRole('img', { name: /Cronache/ }).element();

		expect(book.querySelector('img')?.getAttribute('src')).toBe('http://img.test/c.png');
		expect(page.getByText('Cronache', { exact: true }).elements()).toHaveLength(0);
	});

	it('is 184 by 262 pixels in the card size', () => {
		render(ClosedBook, { title: 'Cronache', color: null, coverUrl: null });

		const box = page.getByRole('img').element().getBoundingClientRect();

		expect(Math.round(box.width)).toBe(184);
		expect(Math.round(box.height)).toBe(262);
	});

	it('keeps the proportions in the preview size', () => {
		render(ClosedBook, { title: 'Cronache', color: null, coverUrl: null, size: 'preview' });

		const box = page.getByRole('img').element().getBoundingClientRect();

		expect(Math.round(box.width)).toBe(96);
		expect(Math.round(box.height)).toBe(137);
	});

	it('shrinks a long title', () => {
		const long = 'Le lunghissime cronache del Nord e di tutte le Terre Spezzate dell’Ovest';
		render(ClosedBook, { title: long, color: null, coverUrl: null });

		const style = getComputedStyle(page.getByText(long).element());

		expect(style.fontSize).toBe('14px');
	});
});

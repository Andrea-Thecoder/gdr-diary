import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import AppFooter from './AppFooter.svelte';

describe('AppFooter', () => {
	beforeEach(async () => {
		await page.viewport(1280, 900);
	});

	it('shows the name of the app', async () => {
		render(AppFooter);

		await expect.element(page.getByRole('contentinfo')).toHaveTextContent('Diario GDR');
	});

	it('links the five information pages', async () => {
		render(AppFooter);
		const nav = page.getByRole('navigation', { name: 'Pagine informative' });

		await expect
			.element(nav.getByRole('link', { name: 'Piani' }))
			.toHaveAttribute('href', '/piani');
		await expect.element(nav.getByRole('link', { name: 'FAQ' })).toHaveAttribute('href', '/faq');
		await expect
			.element(nav.getByRole('link', { name: 'Feedback' }))
			.toHaveAttribute('href', '/feedback');
		await expect
			.element(nav.getByRole('link', { name: 'Contatti' }))
			.toHaveAttribute('href', '/contatti');
		await expect
			.element(nav.getByRole('link', { name: 'Privacy' }))
			.toHaveAttribute('href', '/privacy');
	});

	it('keeps every link at least 44 px tall for the thumb', () => {
		render(AppFooter);

		const heights = Array.from(document.querySelectorAll('footer a')).map(
			(link) => link.getBoundingClientRect().height
		);

		expect(Math.min(...heights)).toBeGreaterThanOrEqual(44);
	});

	it('puts the links on at most two rows on a phone', async () => {
		await page.viewport(390, 900);
		render(AppFooter);

		const rows = new Set(
			Array.from(document.querySelectorAll('footer nav a')).map((link) =>
				Math.round(link.getBoundingClientRect().top)
			)
		);

		expect(rows.size).toBeLessThanOrEqual(2);
	});

	it('puts the links on one row on a desktop', () => {
		render(AppFooter);

		const rows = new Set(
			Array.from(document.querySelectorAll('footer nav a')).map((link) =>
				Math.round(link.getBoundingClientRect().top)
			)
		);

		expect(rows.size).toBe(1);
	});
});

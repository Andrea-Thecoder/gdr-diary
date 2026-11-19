import { page, userEvent } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../layout.css';

vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: { status: 'anonymous', init: () => Promise.resolve(), login: vi.fn() }
}));
vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/faq') } }));

import Page from './+page.svelte';

describe('FAQ page', () => {
	it('shows the title', async () => {
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Le risposte che cerchi.');
	});

	it('shows the five topics in the index', () => {
		render(Page);
		const links = page.getByRole('navigation', { name: 'Argomenti' }).getByRole('link');

		expect(links.elements()).toHaveLength(5);
	});

	it('narrows the questions while the visitor types', async () => {
		render(Page);

		await page.getByRole('searchbox', { name: 'Cerca nelle domande' }).fill('master');

		await expect.element(page.getByText('Posso usarlo se faccio il master?')).toBeVisible();
		expect(page.getByText('Devo pagare?').elements()).toHaveLength(0);
	});

	it('brings every question back when the search is cleared', async () => {
		render(Page);
		const search = page.getByRole('searchbox', { name: 'Cerca nelle domande' });
		await search.fill('master');

		await userEvent.clear(search);

		await expect.element(page.getByText('Devo pagare?')).toBeVisible();
	});

	it('invites to leave a feedback when the answer is not there', async () => {
		render(Page);

		await expect
			.element(page.getByRole('link', { name: 'Lascia un feedback' }))
			.toHaveAttribute('href', '/feedback');
	});

	it('sends the visitor to the contacts when the answer is not there', async () => {
		render(Page);

		await expect
			.element(page.getByRole('link', { name: 'Contatti', exact: true }).last())
			.toHaveAttribute('href', '/contatti');
	});
});

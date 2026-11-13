import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../layout.css';

vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: {
		status: 'anonymous',
		init: () => Promise.resolve(),
		login: vi.fn(),
		register: vi.fn()
	}
}));
vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/piani') } }));

import Page from './+page.svelte';

describe('plans page', () => {
	it('shows the title', async () => {
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Gratis per scrivere, Premium per di più.');
	});

	it('shows one card for each plan', async () => {
		render(Page);

		expect(page.getByRole('article').elements()).toHaveLength(2);
		await expect.element(page.getByRole('heading', { level: 2, name: 'Gratuito' })).toBeVisible();
		await expect.element(page.getByRole('heading', { level: 2, name: 'Premium' })).toBeVisible();
	});

	it('invites to start for free', async () => {
		render(Page);

		await expect.element(page.getByRole('button', { name: 'Inizia gratis' })).toBeVisible();
	});

	it('says the premium plan is not available yet', async () => {
		render(Page);

		await expect.element(page.getByRole('button', { name: 'In arrivo' })).toBeDisabled();
	});

	it('says the table is not a promise', async () => {
		render(Page);

		await expect
			.element(page.getByText('sono ancora provvisori: non sono una promessa', { exact: false }))
			.toBeVisible();
	});

	it('shows the comparison table', async () => {
		render(Page);

		await expect.element(page.getByRole('table')).toBeVisible();
	});

	it('explains the asterisk under the table', async () => {
		render(Page);

		await expect
			.element(
				page.getByText('* Per personaggi si intende una combinazione di personaggi e/o master.')
			)
			.toBeVisible();
	});

	it('points to the frequently asked questions', async () => {
		render(Page);

		await expect
			.element(page.getByRole('link', { name: 'Domande frequenti' }).last())
			.toHaveAttribute('href', '/faq');
	});
});

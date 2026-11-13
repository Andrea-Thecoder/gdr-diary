import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

const { login, auth } = vi.hoisted(() => {
	const login = vi.fn(() => Promise.resolve());
	return { login, auth: { status: 'anonymous', login } };
});

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));

import InfoFooter from './InfoFooter.svelte';

describe('InfoFooter', () => {
	beforeEach(() => {
		login.mockClear();
		auth.status = 'anonymous';
	});

	it('groups the links under Prodotto, Aiuto and Legale', async () => {
		render(InfoFooter);

		await expect.element(page.getByText('Prodotto')).toBeVisible();
		await expect.element(page.getByText('Aiuto')).toBeVisible();
		await expect.element(page.getByText('Legale')).toBeVisible();
	});

	it('links every information page', async () => {
		render(InfoFooter);
		const footer = page.getByRole('contentinfo');

		await expect
			.element(footer.getByRole('link', { name: 'Piani' }))
			.toHaveAttribute('href', '/piani');
		await expect
			.element(footer.getByRole('link', { name: 'Domande frequenti' }))
			.toHaveAttribute('href', '/faq');
		await expect
			.element(footer.getByRole('link', { name: 'Feedback' }))
			.toHaveAttribute('href', '/feedback');
		await expect
			.element(footer.getByRole('link', { name: 'Contatti' }))
			.toHaveAttribute('href', '/contatti');
		await expect
			.element(footer.getByRole('link', { name: 'Privacy' }))
			.toHaveAttribute('href', '/privacy');
	});

	it('offers the login to an anonymous visitor', async () => {
		render(InfoFooter);

		await page.getByRole('button', { name: 'Accedi' }).click();

		expect(login).toHaveBeenCalledOnce();
	});

	it('offers the profile instead of the login to someone who is signed in', async () => {
		auth.status = 'authenticated';
		render(InfoFooter);

		await expect
			.element(page.getByRole('link', { name: 'Il mio profilo' }))
			.toHaveAttribute('href', '/profile');
		expect(page.getByRole('button', { name: 'Accedi' }).elements()).toHaveLength(0);
	});

	it('disables the login while the session is being checked', async () => {
		auth.status = 'loading';
		render(InfoFooter);

		await expect.element(page.getByRole('button', { name: 'Accedi' })).toBeDisabled();
	});
});

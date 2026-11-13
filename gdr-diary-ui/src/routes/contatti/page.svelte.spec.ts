import { page } from 'vitest/browser';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../layout.css';

vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: { status: 'anonymous', init: () => Promise.resolve(), login: vi.fn() }
}));
vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/contatti') } }));

import Page from './+page.svelte';

describe('contacts page', () => {
	afterEach(() => {
		vi.unstubAllEnvs();
	});

	it('shows the title', async () => {
		render(Page);

		await expect.element(page.getByRole('heading', { level: 1 })).toHaveTextContent('Parliamone.');
	});

	it('says the address will come when no contact is set up', async () => {
		render(Page);

		await expect
			.element(
				page.getByText('L’indirizzo sarà indicato qui prima dell’apertura', { exact: false })
			)
			.toBeVisible();
		expect(page.getByRole('button', { name: 'Copia l’indirizzo' }).elements()).toHaveLength(0);
	});

	it('shows the address as a mail link with a button to copy it', async () => {
		vi.stubEnv('VITE_PRIVACY_CONTROLLER_NAME', 'Andrea Leone');
		vi.stubEnv('VITE_PRIVACY_CONTACT_EMAIL', 'ciao@esempio.it');
		render(Page);

		await expect
			.element(page.getByRole('link', { name: 'ciao@esempio.it' }))
			.toHaveAttribute('href', 'mailto:ciao@esempio.it');
		await expect.element(page.getByRole('button', { name: 'Copia l’indirizzo' })).toBeVisible();
	});

	it('shows who runs the service when the contact is set up', async () => {
		vi.stubEnv('VITE_PRIVACY_CONTROLLER_NAME', 'Andrea Leone');
		vi.stubEnv('VITE_PRIVACY_CONTACT_EMAIL', 'ciao@esempio.it');
		render(Page);

		await expect.element(page.getByText('Andrea Leone')).toBeVisible();
	});

	it('invites to use the feedback form for an idea or a problem', async () => {
		render(Page);

		await expect
			.element(page.getByRole('link', { name: 'Lascia un feedback' }))
			.toHaveAttribute('href', '/feedback');
	});

	it('links the pages to read before writing', async () => {
		render(Page);
		const useful = page.getByRole('navigation', { name: 'Pagine utili' });

		await expect
			.element(useful.getByRole('link', { name: 'Le domande frequenti' }))
			.toHaveAttribute('href', '/faq');
		await expect
			.element(useful.getByRole('link', { name: 'Piani gratuito e Premium' }))
			.toHaveAttribute('href', '/piani');
		await expect
			.element(useful.getByRole('link', { name: 'Privacy e i tuoi diritti sui dati' }))
			.toHaveAttribute('href', '/privacy');
	});
});

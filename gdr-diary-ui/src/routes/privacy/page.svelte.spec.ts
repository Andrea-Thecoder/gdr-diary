import { page } from 'vitest/browser';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../layout.css';

vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: { status: 'anonymous', init: () => Promise.resolve(), login: vi.fn() }
}));
vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/privacy') } }));

import Page from './+page.svelte';

function indexLinks(): HTMLAnchorElement[] {
	return Array.from(
		document.querySelectorAll<HTMLAnchorElement>('nav[aria-label="Indice della pagina"] a')
	);
}

describe('privacy page', () => {
	afterEach(() => {
		vi.unstubAllEnvs();
	});

	it('shows the title and the date of the last update', async () => {
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Come tratto i tuoi dati');
		await expect.element(page.getByText('11 novembre 2026')).toBeVisible();
	});

	it('says that drafts are removed when the page is saved or on logout', async () => {
		render(Page);

		await expect
			.element(
				page.getByText('si cancella quando la pagina è salvata o quando esci con «Esci»', {
					exact: false
				})
			)
			.toBeVisible();
	});

	it('says that drafts are kept after a forced disconnection', async () => {
		render(Page);

		await expect
			.element(
				page.getByText('Se invece vieni disconnesso per un errore, la copia resta', {
					exact: false
				})
			)
			.toBeVisible();
	});

	it('explains what the technical data attached to a feedback are', async () => {
		render(Page);

		await expect
			.element(page.getByText('Dati tecnici allegati al feedback', { exact: true }))
			.toBeVisible();
		await expect
			.element(page.getByText('il nome generico della schermata dell’app', { exact: false }))
			.toBeVisible();
		await expect
			.element(page.getByText('l’indirizzo IP, invece, arriva comunque', { exact: false }))
			.toBeVisible();
	});

	it('opens with a short summary before the full text', () => {
		render(Page);

		const headings = page.getByRole('heading', { level: 2 }).elements();

		expect(headings[0]?.textContent).toBe('In breve');
		expect(headings).toHaveLength(10);
	});

	it('has an index entry for every section and each one points at a real heading', () => {
		render(Page);

		const links = indexLinks();
		const targets = links.map((link) => {
			const heading = document.getElementById(link.getAttribute('href')?.slice(1) ?? '');
			return [heading?.tagName, heading?.textContent];
		});
		const labels = links.map((link) => ['H2', link.textContent.replace(/^\s*\d+\s*/, '').trim()]);

		expect(links).toHaveLength(9);
		expect(targets).toEqual(labels);
	});

	it('keeps every index link at least 44 px tall for the thumb', () => {
		render(Page);

		const heights = indexLinks().map((link) => link.getBoundingClientRect().height);

		expect(Math.min(...heights)).toBeGreaterThanOrEqual(44);
	});

	it('says the controller details will be added when they are not configured', async () => {
		render(Page);

		await expect
			.element(page.getByText('I recapiti del titolare saranno indicati qui', { exact: false }))
			.toBeVisible();
		expect(document.querySelector('a[href^="mailto:"]')).toBeNull();
	});

	it('shows the controller name and a mail link when they are configured', async () => {
		vi.stubEnv('VITE_PRIVACY_CONTROLLER_NAME', 'Andrea');
		vi.stubEnv('VITE_PRIVACY_CONTACT_EMAIL', 'privacy@example.com');
		render(Page);

		await expect.element(page.getByText('Andrea')).toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'privacy@example.com' }))
			.toHaveAttribute('href', 'mailto:privacy@example.com');
	});

	it('opens the data protection authority in a new tab without leaking the opener', async () => {
		render(Page);

		const link = page.getByRole('link', { name: 'Garante per la protezione dei dati personali' });

		await expect.element(link).toHaveAttribute('href', 'https://www.garanteprivacy.it');
		await expect.element(link).toHaveAttribute('rel', 'noopener noreferrer');
	});

	it('lets people go back to the home page', async () => {
		render(Page);

		await expect.element(page.getByRole('link', { name: 'Torna alla home' })).toBeVisible();
	});
});

import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import './layout.css';

const { logout, state, auth } = vi.hoisted(() => {
	const logout = vi.fn(() => Promise.resolve());
	return {
		logout,
		state: { page: { status: 502, error: { message: 'Il server non risponde.' } } },
		auth: { status: 'authenticated', logout }
	};
});

vi.mock('$app/state', () => state);
vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));

import ErrorPage from './+error.svelte';

describe('error page', () => {
	beforeEach(async () => {
		logout.mockClear();
		state.page.status = 502;
		state.page.error = { message: 'Il server non risponde.' };
		auth.status = 'authenticated';
		await page.viewport(390, 844);
	});

	it('shows the status and the real reason', async () => {
		render(ErrorPage);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Qualcosa non risponde');
		await expect.element(page.getByText('Errore 502')).toBeVisible();
		await expect.element(page.getByText('Il server non risponde.')).toBeVisible();
	});

	it('lets a signed-in visitor leave, however broken the page is', async () => {
		render(ErrorPage);

		await page.getByRole('button', { name: 'Esci' }).click();

		expect(logout).toHaveBeenCalledTimes(1);
	});

	it('offers to retry on a server problem and to go back home', async () => {
		render(ErrorPage);

		await expect.element(page.getByRole('button', { name: 'Riprova' })).toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'Torna alla home' }))
			.toHaveAttribute('href', '/');
	});

	it('explains that the account is not allowed to see the page', async () => {
		state.page.status = 403;
		state.page.error = { message: 'Non hai i permessi per vedere questa pagina.' };
		render(ErrorPage);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Accesso non consentito');
		await expect.element(page.getByText('Errore 403')).toBeVisible();
		await expect
			.element(page.getByText('Non hai i permessi per vedere questa pagina.'))
			.toBeVisible();
	});

	it('says in Italian that the page does not exist', async () => {
		state.page.status = 404;
		state.page.error = { message: 'Not Found' };
		render(ErrorPage);

		await expect.element(page.getByText('Questa pagina non esiste.')).toBeVisible();
	});

	it('does not offer to retry a page the account is not allowed to see', async () => {
		state.page.status = 403;
		render(ErrorPage);

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		expect(page.getByRole('button', { name: 'Riprova' }).elements()).toHaveLength(0);
	});

	it('lets someone without permission sign out or go home', async () => {
		state.page.status = 403;
		render(ErrorPage);

		await expect.element(page.getByRole('button', { name: 'Esci' })).toBeVisible();
		await expect.element(page.getByRole('link', { name: 'Torna alla home' })).toBeVisible();
	});

	it('offers the home to a visitor who is not signed in too', async () => {
		auth.status = 'anonymous';
		render(ErrorPage);

		await expect
			.element(page.getByRole('link', { name: 'Torna alla home' }))
			.toHaveAttribute('href', '/');
	});

	it('puts the card in the middle of the screen', async () => {
		await page.viewport(1280, 800);
		render(ErrorPage);
		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();

		const card = page.getByRole('heading', { level: 1 }).element().closest('div');
		const box = (card ?? document.body).getBoundingClientRect();
		expect(Math.abs(box.left + box.width / 2 - window.innerWidth / 2)).toBeLessThan(2);
		expect(box.width).toBeLessThanOrEqual(560);
	});

	it('does not offer to leave to someone who is not signed in', async () => {
		auth.status = 'anonymous';
		render(ErrorPage);

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		expect(page.getByRole('button', { name: 'Esci' }).elements()).toHaveLength(0);
	});

	it('does not offer to retry a page that is not there', async () => {
		state.page.status = 404;
		state.page.error = { message: 'Not Found' };
		render(ErrorPage);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Pagina non trovata');
		expect(page.getByRole('button', { name: 'Riprova' }).elements()).toHaveLength(0);
	});

	it('keeps every control at least 44 px tall on a phone', async () => {
		render(ErrorPage);
		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();

		const heights = [
			...page.getByRole('button').elements(),
			...page.getByRole('link').elements()
		].map((control) => control.getBoundingClientRect().height);

		expect(Math.min(...heights)).toBeGreaterThanOrEqual(43.5);
		expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
	});
});

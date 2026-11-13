import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import './layout.css';

const { login, register, logout, goto, auth } = vi.hoisted(() => {
	const login = vi.fn(() => Promise.resolve());
	const register = vi.fn(() => Promise.resolve());
	const logout = vi.fn(() => Promise.resolve());
	const goto = vi.fn(() => Promise.resolve());
	return {
		login,
		register,
		logout,
		goto,
		auth: {
			status: 'anonymous',
			user: null,
			error: null,
			init: () => Promise.resolve(),
			login,
			register,
			logout
		}
	};
});

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('$app/navigation', () => ({ goto }));

import Page from './+page.svelte';

async function clickAll(name: string): Promise<number> {
	const buttons = page.getByRole('button', { name, exact: true });
	const count = buttons.elements().length;
	for (let index = 0; index < count; index += 1) {
		await buttons.nth(index).click();
	}
	return count;
}

describe('landing page', () => {
	beforeEach(async () => {
		await page.viewport(1280, 900);
		login.mockClear();
		register.mockClear();
		auth.status = 'anonymous';
	});

	it('puts the six footer links on two rows of three on a phone', async () => {
		await page.viewport(390, 900);
		render(Page);

		const tops = Array.from(document.querySelectorAll('footer a, footer button')).map((item) =>
			Math.round(item.getBoundingClientRect().top)
		);
		const rows = new Set(tops);

		expect(tops).toHaveLength(6);
		expect(rows.size).toBe(2);
	});

	it('links the information pages from the footer', async () => {
		render(Page);
		const footer = page.getByRole('contentinfo');

		await expect
			.element(footer.getByRole('link', { name: 'Piani' }))
			.toHaveAttribute('href', '/piani');
		await expect.element(footer.getByRole('link', { name: 'FAQ' })).toHaveAttribute('href', '/faq');
		await expect
			.element(footer.getByRole('link', { name: 'Contatti' }))
			.toHaveAttribute('href', '/contatti');
		await expect
			.element(footer.getByRole('link', { name: 'Privacy' }))
			.toHaveAttribute('href', '/privacy');
	});

	it('shows the hero title', async () => {
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Ogni eroe merita il suo diario.');
	});

	it('shows one section heading for each part of the page', async () => {
		render(Page);

		await expect.element(page.getByRole('heading', { level: 2 }).first()).toBeVisible();
		expect(page.getByRole('heading', { level: 2 }).elements()).toHaveLength(4);
	});

	it('starts the login from every login button', async () => {
		render(Page);
		const count = await clickAll('Accedi');

		expect(count).toBeGreaterThanOrEqual(3);
		expect(login).toHaveBeenCalledTimes(count);
		expect(register).not.toHaveBeenCalled();
	});

	it.each(['Registrati', 'Crea il tuo diario', 'Registrati e inizia a scrivere'])(
		'starts the registration from every "%s" button',
		async (name) => {
			render(Page);

			const count = await clickAll(name);

			expect(count).toBeGreaterThanOrEqual(1);
			expect(register).toHaveBeenCalledTimes(count);
			expect(login).not.toHaveBeenCalled();
		}
	);
});

describe('landing page for someone who is signed in', () => {
	beforeEach(() => {
		login.mockClear();
		register.mockClear();
		logout.mockClear();
		goto.mockClear();
		auth.status = 'authenticated';
	});

	it('takes them straight to their characters', async () => {
		render(Page);

		await vi.waitFor(() => {
			expect(goto).toHaveBeenCalledWith('/characters', { replace: true });
		});
	});

	it('does not redirect an anonymous visitor', async () => {
		auth.status = 'anonymous';
		render(Page);

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		expect(goto).not.toHaveBeenCalled();
	});

	it('offers the way to the characters instead of the login while it stays', async () => {
		render(Page);

		const links = page.getByRole('link', { name: 'I tuoi personaggi' });
		expect(links.elements().length).toBeGreaterThanOrEqual(1);
		await expect.element(links.first()).toHaveAttribute('href', '/characters');
		expect(page.getByRole('button', { name: 'Accedi' }).elements()).toHaveLength(0);
		expect(page.getByRole('button', { name: 'Registrati' }).elements()).toHaveLength(0);
		expect(page.getByRole('button', { name: 'Crea il tuo diario' }).elements()).toHaveLength(0);
		expect(page.getByText('Registrarsi richiede un minuto ed è gratuito.').elements()).toHaveLength(
			0
		);
	});
});

describe('landing page when the login service does not answer', () => {
	beforeEach(() => {
		login.mockClear();
		register.mockClear();
		auth.status = 'error';
	});

	it('says the login service does not answer', async () => {
		render(Page);

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Il servizio di accesso non risponde in questo momento');
	});

	it('disables the login buttons', async () => {
		render(Page);

		await expect
			.element(page.getByRole('button', { name: 'Accedi', exact: true }).first())
			.toBeDisabled();
	});

	it('disables the login and registration links of the footer', async () => {
		render(Page);
		const footer = page.getByRole('contentinfo');

		await expect.element(footer.getByRole('button', { name: 'Accedi' })).toBeDisabled();
		await expect.element(footer.getByRole('button', { name: 'Registrati' })).toBeDisabled();
	});

	it('disables the registration buttons', async () => {
		render(Page);

		await expect
			.element(page.getByRole('button', { name: 'Crea il tuo diario', exact: true }).first())
			.toBeDisabled();
	});

	it('shows no warning when the service answers', async () => {
		auth.status = 'anonymous';
		render(Page);

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		expect(page.getByRole('alert').elements()).toHaveLength(0);
	});
});

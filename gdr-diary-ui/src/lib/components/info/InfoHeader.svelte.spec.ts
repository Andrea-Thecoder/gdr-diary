import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

const { login, auth, state } = vi.hoisted(() => {
	const login = vi.fn(() => Promise.resolve());
	return {
		login,
		auth: { status: 'anonymous', login },
		state: { page: { url: new URL('http://localhost/faq') } }
	};
});

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('$app/state', () => state);

import InfoHeader from './InfoHeader.svelte';

describe('InfoHeader', () => {
	beforeEach(async () => {
		login.mockClear();
		auth.status = 'anonymous';
		state.page.url = new URL('http://localhost/faq');
		await page.viewport(1280, 800);
	});

	it('links the logo to the home page', async () => {
		render(InfoHeader);

		await expect
			.element(page.getByRole('link', { name: 'Diario GDR' }))
			.toHaveAttribute('href', '/');
	});

	it('links the three information pages', async () => {
		render(InfoHeader);
		const nav = page.getByRole('navigation', { name: 'Principale' });

		await expect
			.element(nav.getByRole('link', { name: 'Piani' }))
			.toHaveAttribute('href', '/piani');
		await expect.element(nav.getByRole('link', { name: 'FAQ' })).toHaveAttribute('href', '/faq');
		await expect
			.element(nav.getByRole('link', { name: 'Contatti' }))
			.toHaveAttribute('href', '/contatti');
	});

	it('marks the page the visitor is on', async () => {
		render(InfoHeader);
		const nav = page.getByRole('navigation', { name: 'Principale' });

		await expect
			.element(nav.getByRole('link', { name: 'FAQ' }))
			.toHaveAttribute('aria-current', 'page');
		await expect
			.element(nav.getByRole('link', { name: 'Piani' }))
			.not.toHaveAttribute('aria-current');
	});

	it('does not mark any link on another page', () => {
		state.page.url = new URL('http://localhost/privacy');
		render(InfoHeader);

		const current = document.querySelectorAll('nav[aria-label="Principale"] [aria-current]');

		expect(current).toHaveLength(0);
	});

	it('starts the login from the Accedi button', async () => {
		render(InfoHeader);

		await page.getByRole('button', { name: 'Accedi' }).click();

		expect(login).toHaveBeenCalledOnce();
	});

	it('keeps every link at least 44 px tall for the thumb', () => {
		render(InfoHeader);

		const heights = Array.from(document.querySelectorAll('nav a')).map(
			(link) => link.getBoundingClientRect().height
		);

		expect(Math.min(...heights)).toBeGreaterThanOrEqual(44);
	});
});

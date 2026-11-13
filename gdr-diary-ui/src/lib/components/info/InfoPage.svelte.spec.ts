import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';
import '../../../routes/layout.css';

const { init, auth } = vi.hoisted(() => {
	const init = vi.fn(() => Promise.resolve());
	return {
		init,
		auth: {
			status: 'anonymous',
			user: { sub: 'x', email: 'nome@esempio.it', name: 'Lyra', nickname: 'lyra', roles: [] },
			init,
			login: vi.fn(() => Promise.resolve()),
			logout: vi.fn(() => Promise.resolve())
		}
	};
});

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/faq') } }));

import InfoPage from './InfoPage.svelte';

const children = createRawSnippet(() => ({ render: () => '<p>Corpo della pagina</p>' }));
const extra = createRawSnippet(() => ({ render: () => '<p>Parte in più</p>' }));

const props = { eyebrow: 'Piani', title: 'Un titolo', lead: 'Una frase di apertura.', children };

describe('InfoPage', () => {
	beforeEach(async () => {
		init.mockClear();
		auth.status = 'anonymous';
		await page.viewport(1280, 800);
	});

	it('shows the eyebrow, the title and the lead', async () => {
		render(InfoPage, props);

		await expect.element(page.getByRole('heading', { level: 1 })).toHaveTextContent('Un titolo');
		await expect.element(page.getByText('Piani', { exact: true }).first()).toBeVisible();
		await expect.element(page.getByText('Una frase di apertura.')).toBeVisible();
	});

	it('shows the content inside the main area', async () => {
		render(InfoPage, props);

		await expect.element(page.getByRole('main')).toHaveTextContent('Corpo della pagina');
	});

	it('shows the extra part under the lead when there is one', async () => {
		render(InfoPage, { ...props, extra });

		await expect.element(page.getByText('Parte in più')).toBeVisible();
	});

	it('checks the session when it opens', () => {
		render(InfoPage, props);

		expect(init).toHaveBeenCalledOnce();
	});

	it('shows the public header to an anonymous visitor', async () => {
		render(InfoPage, props);

		await expect.element(page.getByRole('navigation', { name: 'Principale' })).toBeVisible();
		expect(page.getByRole('button', { name: 'Menu dell’account' }).elements()).toHaveLength(0);
	});

	it('shows the header of the app to someone who is signed in', async () => {
		auth.status = 'authenticated';
		render(InfoPage, props);

		await expect.element(page.getByRole('button', { name: 'Menu dell’account' })).toBeVisible();
		expect(page.getByRole('navigation', { name: 'Principale' }).elements()).toHaveLength(0);
	});

	it('ends with the footer', async () => {
		render(InfoPage, props);

		await expect.element(page.getByRole('contentinfo')).toBeVisible();
	});
});

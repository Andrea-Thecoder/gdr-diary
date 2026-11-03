import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

const { logout } = vi.hoisted(() => ({ logout: vi.fn(() => Promise.resolve()) }));

vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: {
		status: 'authenticated',
		user: {
			sub: 'x',
			email: 'nome@esempio.it',
			name: 'Lyra Belacqua',
			nickname: 'lyra',
			roles: []
		},
		logout
	}
}));

import AppHeader from './AppHeader.svelte';

const trigger = (): ReturnType<typeof page.getByRole> =>
	page.getByRole('button', { name: 'Menu dell’account' });

async function openMenu(): Promise<void> {
	render(AppHeader);
	await trigger().click();
	await expect.element(page.getByRole('link', { name: 'Impostazioni' })).toBeVisible();
}

describe('AppHeader', () => {
	beforeEach(async () => {
		logout.mockClear();
		await page.viewport(1280, 800);
	});

	it('links the logo to the characters page', async () => {
		render(AppHeader);

		await expect
			.element(page.getByRole('link', { name: 'Diario GDR' }))
			.toHaveAttribute('href', '/characters');
	});

	it('keeps the menu closed until the account icon is pressed', async () => {
		render(AppHeader);

		await expect.element(trigger()).toHaveAttribute('aria-expanded', 'false');
		expect(page.getByRole('link', { name: 'Impostazioni' }).elements()).toHaveLength(0);
		expect(page.getByText('lyra', { exact: true }).elements()).toHaveLength(0);
	});

	it('shows the nickname, settings and logout when opened', async () => {
		await openMenu();

		await expect.element(trigger()).toHaveAttribute('aria-expanded', 'true');
		await expect.element(page.getByText('lyra', { exact: true })).toBeVisible();
		expect(page.getByText('nome@esempio.it').elements()).toHaveLength(0);
		await expect
			.element(page.getByRole('link', { name: 'Impostazioni' }))
			.toHaveAttribute('href', '/settings');
		await expect.element(page.getByRole('button', { name: 'Esci' })).toBeVisible();
		expect(page.getByRole('button', { name: /^Tema:/ }).elements()).toHaveLength(0);
	});

	it('opens the profile from the block with the account name', async () => {
		await openMenu();

		const link = page.getByRole('link', { name: /Il tuo profilo/ });
		await expect.element(link).toHaveAttribute('href', '/profile');
		await expect.element(link).toHaveTextContent('lyra');
		expect(page.getByRole('link', { name: 'Profilo', exact: true }).elements()).toHaveLength(0);
	});

	it('toggles with a second press', async () => {
		await openMenu();

		await trigger().click();

		await expect.element(page.getByRole('link', { name: 'Impostazioni' })).not.toBeInTheDocument();
	});

	it('closes with Escape and gives the focus back', async () => {
		await openMenu();

		await userEvent.keyboard('{Escape}');

		await expect.element(page.getByRole('link', { name: 'Impostazioni' })).not.toBeInTheDocument();
		expect(document.activeElement).toBe(trigger().element());
	});

	it('stays open when the press started inside and ended outside', async () => {
		await openMenu();

		page
			.getByText('lyra', { exact: true })
			.element()
			.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));

		await expect.element(page.getByRole('link', { name: 'Impostazioni' })).toBeVisible();
	});

	it('closes when pressing outside', async () => {
		await openMenu();

		document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));

		await expect.element(page.getByRole('link', { name: 'Impostazioni' })).not.toBeInTheDocument();
	});

	it('logs out with Esci', async () => {
		await openMenu();

		await page.getByRole('button', { name: 'Esci' }).click();

		expect(logout).toHaveBeenCalledTimes(1);
	});

	for (const width of [280, 320, 390, 768, 1280]) {
		it(`keeps the open menu inside a ${String(width)} px screen`, async () => {
			await page.viewport(width, 700);
			await openMenu();

			const panel = page.getByRole('link', { name: 'Impostazioni' }).element().parentElement;
			const box = panel?.getBoundingClientRect();
			expect(box?.left).toBeGreaterThanOrEqual(0);
			expect(box?.right).toBeLessThanOrEqual(width);
			expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
		});
	}
});

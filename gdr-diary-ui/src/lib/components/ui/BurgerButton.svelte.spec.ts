import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import BurgerButton from './BurgerButton.svelte';

describe('BurgerButton', () => {
	it('offers to open the menu while it is closed', async () => {
		render(BurgerButton, { open: false, controls: 'menu', onclick: vi.fn() });

		const button = page.getByRole('button', { name: 'Apri il menu' });
		await expect.element(button).toHaveAttribute('aria-expanded', 'false');
		await expect.element(button).not.toHaveAttribute('aria-controls');
	});

	it('offers to close the menu while it is open and points to it', async () => {
		render(BurgerButton, { open: true, controls: 'menu', onclick: vi.fn() });

		const button = page.getByRole('button', { name: 'Chiudi il menu' });
		await expect.element(button).toHaveAttribute('aria-expanded', 'true');
		await expect.element(button).toHaveAttribute('aria-controls', 'menu');
	});

	it('calls onclick when it is pressed', async () => {
		const onclick = vi.fn();
		render(BurgerButton, { open: false, controls: 'menu', onclick });

		await page.getByRole('button', { name: 'Apri il menu' }).click();

		expect(onclick).toHaveBeenCalledTimes(1);
	});
});

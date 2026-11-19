import { page } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import CopyEmailButton from './CopyEmailButton.svelte';

const writeText = vi.fn<(text: string) => Promise<void>>();

describe('CopyEmailButton', () => {
	beforeEach(() => {
		writeText.mockReset();
		Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('copies the address to the clipboard', async () => {
		writeText.mockResolvedValue();
		render(CopyEmailButton, { text: 'ciao@esempio.it' });

		await page.getByRole('button', { name: 'Copia l’indirizzo' }).click();

		expect(writeText).toHaveBeenCalledWith('ciao@esempio.it');
	});

	it('says the address was copied', async () => {
		writeText.mockResolvedValue();
		render(CopyEmailButton, { text: 'ciao@esempio.it' });

		await page.getByRole('button', { name: 'Copia l’indirizzo' }).click();

		await expect.element(page.getByRole('button', { name: 'Indirizzo copiato' })).toBeVisible();
		await expect
			.element(page.getByRole('status'))
			.toHaveTextContent('Indirizzo copiato negli appunti');
	});

	it('says so when the browser does not let it copy', async () => {
		writeText.mockRejectedValue(new Error('negato'));
		render(CopyEmailButton, { text: 'ciao@esempio.it' });

		await page.getByRole('button', { name: 'Copia l’indirizzo' }).click();

		await expect.element(page.getByRole('button', { name: 'Non riesco a copiare' })).toBeVisible();
	});

	it('goes back to the first label after a moment', async () => {
		vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
		writeText.mockResolvedValue();
		render(CopyEmailButton, { text: 'ciao@esempio.it' });
		const button = page.getByRole('button', { name: 'Copia l’indirizzo' });

		await button.click();
		await vi.advanceTimersByTimeAsync(2300);

		await expect.element(page.getByRole('button', { name: 'Copia l’indirizzo' })).toBeVisible();
	});
});

import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import ConfirmDeleteDialog from './ConfirmDeleteDialog.svelte';

describe('ConfirmDeleteDialog', () => {
	beforeEach(async () => {
		await page.viewport(390, 844);
	});

	function open(
		onconfirm: () => Promise<void> = () => Promise.resolve(),
		onclose: () => void = vi.fn()
	): void {
		render(ConfirmDeleteDialog, {
			open: true,
			title: 'Eliminare Alira?',
			subtitle: 'Stai per cancellare questo personaggio.',
			consequences: ['il personaggio;', 'tutti i suoi diari.'],
			onconfirm,
			onclose
		});
	}

	it('says what is at risk and that it cannot be undone', async () => {
		open();

		await expect.element(page.getByRole('heading', { name: 'Eliminare Alira?' })).toBeVisible();
		await expect.element(page.getByText('Verranno cancellati per sempre:')).toBeVisible();
		await expect.element(page.getByText('tutti i suoi diari.')).toBeVisible();
		await expect
			.element(page.getByText('Non si può annullare e non c’è modo di recuperare i dati.'))
			.toBeVisible();
	});

	it('does not confirm until asked', async () => {
		const onconfirm = vi.fn(() => Promise.resolve());
		open(onconfirm);

		await page.getByRole('button', { name: 'Annulla' }).click();

		expect(onconfirm).not.toHaveBeenCalled();
	});

	it('confirms once and locks itself while working', async () => {
		let finish: () => void = () => undefined;
		const onconfirm = vi.fn(
			() =>
				new Promise<void>((resolve) => {
					finish = resolve;
				})
		);
		const onclose = vi.fn();
		open(onconfirm, onclose);

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('button', { name: 'Elimino…' })).toBeDisabled();
		await expect.element(page.getByRole('button', { name: 'Annulla' })).toBeDisabled();
		expect(onconfirm).toHaveBeenCalledTimes(1);
		finish();
	});

	it('shows the error and stays open when the deletion fails', async () => {
		const onclose = vi.fn();
		open(() => Promise.reject(new Error('Server non raggiungibile.')), onclose);

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Server non raggiungibile.');
		await expect.element(page.getByRole('button', { name: 'Elimina per sempre' })).toBeEnabled();
		expect(onclose).not.toHaveBeenCalled();
	});
	function pendingDeletion(): { onconfirm: () => Promise<void>; fail: () => void } {
		let reject: (cause: Error) => void = () => undefined;
		return {
			onconfirm: () =>
				new Promise<void>((_, rejectDeletion) => {
					reject = rejectDeletion;
				}),
			fail: () => {
				reject(new Error('Server non raggiungibile.'));
			}
		};
	}

	function backdropEvent(type: 'pointerdown' | 'click'): void {
		const element = document.querySelector('dialog');
		const init = { bubbles: true, clientX: 4, clientY: 4 };
		element?.dispatchEvent(
			type === 'pointerdown' ? new PointerEvent(type, init) : new MouseEvent(type, init)
		);
	}

	it('closes with Escape when idle', async () => {
		const onclose = vi.fn();
		open(() => Promise.resolve(), onclose);

		await userEvent.keyboard('{Escape}');

		await expect.poll(() => onclose.mock.calls.length).toBe(1);
	});

	it('closes with a press and release on the backdrop when idle', () => {
		const onclose = vi.fn();
		open(() => Promise.resolve(), onclose);

		backdropEvent('pointerdown');
		backdropEvent('click');

		expect(onclose).toHaveBeenCalledTimes(1);
	});

	it('ignores Escape while the deletion is running', async () => {
		const deletion = pendingDeletion();
		const onclose = vi.fn();
		open(deletion.onconfirm, onclose);
		await page.getByRole('button', { name: 'Elimina per sempre' }).click();
		await expect.element(page.getByRole('button', { name: 'Elimino…' })).toBeDisabled();

		await userEvent.keyboard('{Escape}');

		await expect.element(page.getByRole('dialog')).toBeVisible();
		expect(onclose).not.toHaveBeenCalled();
		deletion.fail();
	});

	it('ignores the backdrop while the deletion is running', async () => {
		const deletion = pendingDeletion();
		const onclose = vi.fn();
		open(deletion.onconfirm, onclose);
		await page.getByRole('button', { name: 'Elimina per sempre' }).click();
		await expect.element(page.getByRole('button', { name: 'Elimino…' })).toBeDisabled();

		backdropEvent('pointerdown');
		backdropEvent('click');

		await expect.element(page.getByRole('dialog')).toBeVisible();
		expect(onclose).not.toHaveBeenCalled();
		deletion.fail();
	});

	it('shows the error in the dialog that Escape could not close when the deletion fails', async () => {
		const deletion = pendingDeletion();
		open(deletion.onconfirm);
		await page.getByRole('button', { name: 'Elimina per sempre' }).click();
		await userEvent.keyboard('{Escape}');

		deletion.fail();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Server non raggiungibile.');
	});
});

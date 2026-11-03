import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import DialogHarness from './DialogHarness.svelte';

describe('Dialog', () => {
	beforeEach(async () => {
		await page.viewport(1024, 768);
	});

	async function openDialog(refuseClose = false, onclose = vi.fn()): Promise<void> {
		render(DialogHarness, { refuseClose, onclose });
		await page.getByRole('button', { name: 'Apri' }).click();
		await expect.element(page.getByRole('dialog')).toBeVisible();
	}

	function dialogElement(): HTMLDialogElement {
		const element = document.querySelector('dialog');
		if (element === null) {
			throw new Error('dialog missing');
		}
		return element;
	}

	function backdropPoint(): { x: number; y: number } {
		return { x: 4, y: 4 };
	}

	function dispatchOnDialog(type: 'pointerdown' | 'click'): void {
		const point = backdropPoint();
		const init = { bubbles: true, clientX: point.x, clientY: point.y };
		dialogElement().dispatchEvent(
			type === 'pointerdown' ? new PointerEvent(type, init) : new MouseEvent(type, init)
		);
	}

	it('calls onclose and closes on Escape when the parent lets it', async () => {
		const onclose = vi.fn();
		await openDialog(false, onclose);

		await userEvent.keyboard('{Escape}');

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		expect(onclose).toHaveBeenCalledTimes(1);
	});

	it('stays visible on Escape when the parent keeps it open', async () => {
		const onclose = vi.fn();
		await openDialog(true, onclose);

		await userEvent.keyboard('{Escape}');

		await expect.poll(() => onclose.mock.calls.length).toBe(1);
		await expect.element(page.getByRole('dialog')).toBeVisible();
	});

	it('stays modal after the parent refused a native close', async () => {
		await openDialog(true);

		await userEvent.keyboard('{Escape}');

		await expect.poll(() => dialogElement().open).toBe(true);
		expect(dialogElement().matches(':modal')).toBe(true);
	});

	it('closes when the press and the release are both on the backdrop', async () => {
		const onclose = vi.fn();
		await openDialog(false, onclose);

		dispatchOnDialog('pointerdown');
		dispatchOnDialog('click');

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		expect(onclose).toHaveBeenCalledTimes(1);
	});

	it('does not close when a drag starts inside and ends on the backdrop', async () => {
		const onclose = vi.fn();
		await openDialog(false, onclose);

		page
			.getByRole('textbox', { name: 'Dentro' })
			.element()
			.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		dispatchOnDialog('click');

		expect(onclose).not.toHaveBeenCalled();
		await expect.element(page.getByRole('dialog')).toBeVisible();
	});

	it('gives the focus back to the button that opened it', async () => {
		await openDialog();
		await userEvent.keyboard('{Escape}');
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();

		await expect.element(page.getByRole('button', { name: 'Apri' })).toHaveFocus();
	});
});

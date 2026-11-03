import { page } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';
import '../../../routes/layout.css';
import Popup from './Popup.svelte';

const message = createRawSnippet(() => ({ render: () => '<span>Salvato.</span>' }));

describe('Popup', () => {
	beforeEach(async () => {
		vi.useFakeTimers();
		await page.viewport(390, 844);
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	function open(
		onclose = vi.fn(),
		tone: 'status' | 'alert' = 'status',
		placement: 'center' | 'corner' = 'center'
	): ReturnType<typeof vi.fn> {
		render(Popup, { onclose, tone, placement, children: message });
		return onclose;
	}

	it('shows the message in the middle of the screen', async () => {
		open();

		await expect.element(page.getByRole('status')).toHaveTextContent('Salvato.');
		const box = page.getByRole('status').element().getBoundingClientRect();
		expect(Math.abs(box.left + box.width / 2 - window.innerWidth / 2)).toBeLessThan(2);
		expect(Math.abs(box.top + box.height / 2 - window.innerHeight / 2)).toBeLessThan(2);
	});

	it('uses the alert role for errors', async () => {
		open(vi.fn(), 'alert');

		await expect.element(page.getByRole('alert')).toBeVisible();
	});

	it('closes by itself after 4 seconds', () => {
		const onclose = open();

		vi.advanceTimersByTime(3900);
		expect(onclose).not.toHaveBeenCalled();
		vi.advanceTimersByTime(200);

		expect(onclose).toHaveBeenCalledOnce();
	});

	it('closes with the close button', async () => {
		const onclose = open();

		await page.getByRole('button', { name: 'Chiudi' }).click();

		expect(onclose).toHaveBeenCalledOnce();
	});

	it('closes when pressed and released outside', () => {
		const onclose = open();
		const backdrop = page.getByRole('status').element().parentElement;

		backdrop?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		backdrop?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

		expect(onclose).toHaveBeenCalledOnce();
	});

	it('stays open when the press started inside and ended outside', () => {
		const onclose = open();
		const panel = page.getByRole('status').element();

		panel.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		panel.parentElement?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

		expect(onclose).not.toHaveBeenCalled();
	});

	it('closes with Escape', () => {
		const onclose = open();

		window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

		expect(onclose).toHaveBeenCalledOnce();
	});

	describe('in the corner', () => {
		it('sits at the bottom right of the screen', async () => {
			open(vi.fn(), 'alert', 'corner');

			await expect.element(page.getByRole('alert')).toBeVisible();
			const box = page.getByRole('alert').element().getBoundingClientRect();
			expect(window.innerWidth - box.right).toBeLessThan(20);
			expect(window.innerHeight - box.bottom).toBeLessThan(20);
		});

		it('lets the page underneath be clicked', async () => {
			open(vi.fn(), 'alert', 'corner');

			await expect.element(page.getByRole('alert')).toBeVisible();
			const layer = page.getByRole('alert').element().parentElement ?? document.body;

			expect(getComputedStyle(layer).pointerEvents).toBe('none');
		});

		it('does not close when the screen outside is pressed', () => {
			const onclose = open(vi.fn(), 'alert', 'corner');
			const layer = page.getByRole('alert').element().parentElement;

			layer?.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
			layer?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

			expect(onclose).not.toHaveBeenCalled();
		});

		it('closes by itself after 4 seconds like the others', () => {
			const onclose = open(vi.fn(), 'alert', 'corner');

			vi.advanceTimersByTime(3900);
			expect(onclose).not.toHaveBeenCalled();
			vi.advanceTimersByTime(200);

			expect(onclose).toHaveBeenCalledOnce();
		});

		it('closes with the close button', async () => {
			const onclose = open(vi.fn(), 'alert', 'corner');

			await page.getByRole('button', { name: 'Chiudi' }).click();

			expect(onclose).toHaveBeenCalledOnce();
		});
	});
});

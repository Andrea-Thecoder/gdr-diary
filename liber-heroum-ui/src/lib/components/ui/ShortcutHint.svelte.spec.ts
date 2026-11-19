import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import ShortcutHint from './ShortcutHint.svelte';

describe('ShortcutHint', () => {
	it('names the keys that save at once', async () => {
		await page.viewport(1280, 800);
		render(ShortcutHint, {});

		await expect.element(page.getByText('per salvare subito')).toBeVisible();
		expect(page.getByText('Ctrl').element().tagName).toBe('KBD');
		expect(page.getByText('S', { exact: true }).element().tagName).toBe('KBD');
	});

	it('is left out on a phone, which has no keyboard shortcuts', async () => {
		await page.viewport(390, 800);
		render(ShortcutHint, {});

		expect(getComputedStyle(page.getByText('per salvare subito').element()).display).toBe('none');
	});

	it('takes the classes it is given', () => {
		const { container } = render(ShortcutHint, { class: 'text-center' });

		expect(container.querySelector('p')?.classList.contains('text-center')).toBe(true);
	});
});

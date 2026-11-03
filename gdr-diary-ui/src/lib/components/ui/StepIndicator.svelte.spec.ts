import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import StepIndicator from './StepIndicator.svelte';

describe('StepIndicator', () => {
	it('says which step it is and how many there are', async () => {
		render(StepIndicator, { step: 1, total: 2 });

		const bar = page.getByRole('progressbar', { name: 'Passo 1 di 2' });
		await expect.element(bar).toHaveAttribute('aria-valuenow', '1');
		await expect.element(bar).toHaveAttribute('aria-valuemax', '2');
		await expect.element(page.getByText('Passo 1 di 2')).toBeVisible();
	});

	it('draws one bar for each step and fills the ones reached', async () => {
		render(StepIndicator, { step: 2, total: 3 });
		await expect.element(page.getByRole('progressbar')).toBeVisible();

		const bars = [...page.getByRole('progressbar').element().children];
		expect(bars).toHaveLength(3);
		const filled = bars.map((bar) => bar.className.includes('bg-fill'));
		expect(filled).toEqual([true, true, false]);
	});
});

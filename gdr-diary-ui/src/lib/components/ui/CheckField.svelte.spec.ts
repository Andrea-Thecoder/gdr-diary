import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';
import '../../../routes/layout.css';
import CheckField from './CheckField.svelte';

const children = createRawSnippet(() => ({ render: () => '<span>Accetto</span>' }));

describe('CheckField', () => {
	it('starts unchecked', async () => {
		render(CheckField, { children });

		await expect.element(page.getByRole('checkbox', { name: 'Accetto' })).not.toBeChecked();
	});

	it('is checked by a click on the text', async () => {
		render(CheckField, { children });

		await page.getByText('Accetto').click();

		await expect.element(page.getByRole('checkbox', { name: 'Accetto' })).toBeChecked();
	});

	it('shows the error and links it to the box', async () => {
		render(CheckField, { children, error: 'Serve la spunta' });

		await expect.element(page.getByRole('alert')).toHaveTextContent('Serve la spunta');
		await expect
			.element(page.getByRole('checkbox', { name: 'Accetto' }))
			.toHaveAttribute('aria-invalid', 'true');
	});

	it('shows no alert without an error', () => {
		render(CheckField, { children });

		expect(page.getByRole('alert').elements()).toHaveLength(0);
	});
});

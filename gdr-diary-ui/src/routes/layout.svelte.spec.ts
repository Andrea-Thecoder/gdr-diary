import { page } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';
import { errorNotice } from '#lib/ui/error-notice.svelte.js';
import { rememberError } from '#lib/ui/flash-error.js';
import Layout from './+layout.svelte';

const children = createRawSnippet(() => ({ render: () => '<p>Contenuto</p>' }));

describe('root layout', () => {
	beforeEach(async () => {
		sessionStorage.clear();
		errorNotice.clear();
		await page.viewport(1280, 800);
	});

	afterEach(() => {
		sessionStorage.clear();
		errorNotice.clear();
	});

	it('shows the error left by the page that signed the user out, once', async () => {
		rememberError('Il server non risponde. Sei stato disconnesso.');
		render(Layout, { children } as never);

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Il server non risponde. Sei stato disconnesso.');
		const box = page.getByRole('alert').element().getBoundingClientRect();
		expect(window.innerWidth - box.right).toBeLessThan(20);
		expect(window.innerHeight - box.bottom).toBeLessThan(20);
		expect(sessionStorage.length).toBe(0);
	});

	it('shows nothing when no page left an error', async () => {
		render(Layout, { children } as never);

		await expect.element(page.getByText('Contenuto')).toBeVisible();
		expect(page.getByRole('alert').elements()).toHaveLength(0);
	});
});

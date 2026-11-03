import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';

const { login, register, auth } = vi.hoisted(() => {
	const login = vi.fn(() => Promise.resolve());
	const register = vi.fn(() => Promise.resolve());
	return { login, register, auth: { status: 'anonymous', login, register } };
});

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));

import AuthButton from './AuthButton.svelte';

const label = createRawSnippet(() => ({ render: () => '<span>Accedi</span>' }));

describe('AuthButton', () => {
	beforeEach(() => {
		login.mockClear();
		register.mockClear();
		auth.status = 'anonymous';
	});

	it('starts the login for an anonymous visitor', async () => {
		render(AuthButton, { children: label });

		await page.getByRole('button', { name: 'Accedi' }).click();

		expect(login).toHaveBeenCalledOnce();
	});

	it('is disabled while the session is being checked', async () => {
		auth.status = 'loading';
		render(AuthButton, { children: label });

		await expect.element(page.getByRole('button', { name: 'Accedi' })).toBeDisabled();
	});

	it('shows nothing for the login when the visitor is signed in', () => {
		auth.status = 'authenticated';
		render(AuthButton, { children: label });

		expect(page.getByRole('button').elements()).toHaveLength(0);
	});

	it('turns the registration into the way to the characters when the visitor is signed in', async () => {
		auth.status = 'authenticated';
		render(AuthButton, { action: 'register', children: label });

		await expect
			.element(page.getByRole('link', { name: 'I tuoi personaggi' }))
			.toHaveAttribute('href', '/characters');
	});
});

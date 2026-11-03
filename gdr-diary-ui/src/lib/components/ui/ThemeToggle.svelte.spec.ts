import { page } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import ThemeToggle from './ThemeToggle.svelte';

function systemTheme(): string {
	return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'viola' : 'pergamena';
}

function toggle(name: string): ReturnType<typeof page.getByRole> {
	return page.getByRole('button', { name, exact: true });
}

function clearThemeCookie(): void {
	document.cookie = 'gdr-theme=; Path=/; Max-Age=0';
}

describe('ThemeToggle', () => {
	beforeEach(() => {
		clearThemeCookie();
		document.documentElement.setAttribute('data-theme', systemTheme());
	});

	afterEach(() => {
		clearThemeCookie();
	});

	it('starts on the system theme and says what the next click does', async () => {
		render(ThemeToggle);

		await expect.element(toggle('Tema: sistema. Passa a chiaro')).toBeVisible();
	});

	it('cycles light, dark and system, remembering each choice', async () => {
		render(ThemeToggle);

		await toggle('Tema: sistema. Passa a chiaro').click();
		expect(document.documentElement.getAttribute('data-theme')).toBe('pergamena');
		expect(document.cookie).toContain('gdr-theme=pergamena');

		await toggle('Tema: chiaro. Passa a scuro').click();
		expect(document.documentElement.getAttribute('data-theme')).toBe('viola');
		expect(document.cookie).toContain('gdr-theme=viola');

		await toggle('Tema: scuro. Passa a sistema').click();
		expect(document.documentElement.getAttribute('data-theme')).toBe(systemTheme());
		expect(document.cookie).not.toContain('gdr-theme');

		await expect.element(toggle('Tema: sistema. Passa a chiaro')).toBeVisible();
	});

	it('shows the stored choice when it is mounted', async () => {
		document.cookie = 'gdr-theme=viola; Path=/';

		render(ThemeToggle);

		await expect.element(toggle('Tema: scuro. Passa a sistema')).toBeVisible();
	});

	it('can show the current choice as text next to the button', async () => {
		render(ThemeToggle, { showLabel: true });

		await expect.element(page.getByText('Tema: sistema', { exact: true })).toBeVisible();

		await page.getByRole('button', { name: 'Tema: sistema. Passa a chiaro' }).click();

		await expect.element(page.getByText('Tema: chiaro', { exact: true })).toBeVisible();
	});
});

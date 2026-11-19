import { expect, test } from './fixtures.js';

const link = { name: 'Come funziona', exact: true };

test.describe('narrow screen', () => {
	test.use({ viewport: { width: 390, height: 844 } });

	test('hides the links behind a burger button', async ({ page }) => {
		await page.goto('/');
		await expect(page.getByRole('link', link)).toBeHidden();
		await expect(page.getByRole('button', { name: 'Apri il menu' })).toHaveAttribute(
			'aria-expanded',
			'false'
		);
	});

	test('opens and closes the menu with the button', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: 'Apri il menu' }).click();

		await expect(page.getByRole('link', link)).toBeVisible();
		await expect(page.getByRole('link', { name: 'La scrittura' })).toBeVisible();
		await expect(page.getByRole('link', { name: 'Per chi è' })).toBeVisible();
		await expect(
			page.getByRole('navigation', { name: 'Principale' }).getByRole('button', { name: 'Accedi' })
		).toBeVisible();
		await expect(
			page
				.getByRole('navigation', { name: 'Principale' })
				.getByRole('button', { name: 'Registrati' })
		).toBeVisible();
		await expect(page.getByRole('button', { name: 'Chiudi il menu' })).toHaveAttribute(
			'aria-expanded',
			'true'
		);

		await page.getByRole('button', { name: 'Chiudi il menu' }).click();
		await expect(page.getByRole('link', link)).toBeHidden();
	});

	test('closes the menu after choosing a section', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: 'Apri il menu' }).click();
		await page.getByRole('link', { name: 'La scrittura' }).click();

		await expect(page).toHaveURL(/#scrittura$/);
		await expect(page.getByRole('link', { name: 'La scrittura' })).toBeHidden();
	});

	test('closes with Escape and gives the focus back to the button', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: 'Apri il menu' }).click();
		await page.keyboard.press('Escape');

		await expect(page.getByRole('link', link)).toBeHidden();
		await expect(page.getByRole('button', { name: 'Apri il menu' })).toBeFocused();
	});

	test('puts the theme button inside the menu', async ({ page }) => {
		await page.goto('/');
		await expect(page.getByRole('button', { name: /^Tema:/ })).toBeHidden();

		await page.getByRole('button', { name: 'Apri il menu' }).click();
		await expect(page.getByText('Tema: sistema', { exact: true })).toBeVisible();

		await page.getByRole('button', { name: 'Tema: sistema. Passa a chiaro' }).click();
		await expect(page.getByText('Tema: chiaro', { exact: true })).toBeVisible();
		await expect(page.getByRole('link', link)).toBeVisible();
	});
});

test.describe('wide screen', () => {
	test.use({ viewport: { width: 1280, height: 800 } });

	test('shows the links in the header and no burger', async ({ page }) => {
		await page.goto('/');
		await expect(page.getByRole('link', link)).toBeVisible();
		await expect(page.getByRole('button', { name: 'Apri il menu' })).toBeHidden();
		await expect(page.getByRole('button', { name: /^Tema:/ })).toBeVisible();
		await expect(
			page
				.getByRole('navigation', { name: 'Principale' })
				.getByRole('button', { name: 'Registrati' })
		).toBeVisible();
	});
});

test('resizing from phone to desktop and back leaves the menu closed', async ({ page }) => {
	await page.setViewportSize({ width: 390, height: 844 });
	await page.goto('/');
	await page.getByRole('button', { name: 'Apri il menu' }).click();

	await page.setViewportSize({ width: 1280, height: 800 });
	await expect(page.getByRole('link', link)).toBeVisible();
	await expect(page.getByRole('button', { name: 'Apri il menu' })).toBeHidden();

	await page.setViewportSize({ width: 390, height: 844 });
	await expect(page.getByRole('link', link)).toBeHidden();
	await expect(page.getByRole('button', { name: 'Apri il menu' })).toBeVisible();
});

test('the burger breakpoint sits at 900 px', async ({ page }) => {
	await page.setViewportSize({ width: 899, height: 800 });
	await page.goto('/');
	await expect(page.getByRole('button', { name: 'Apri il menu' })).toBeVisible();

	await page.setViewportSize({ width: 900, height: 800 });
	await expect(page.getByRole('button', { name: 'Apri il menu' })).toBeHidden();
	await expect(page.getByRole('link', link)).toBeVisible();
});

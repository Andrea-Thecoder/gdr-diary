import { expect, test } from './fixtures.js';

test('landing page shows the hero and the login button', async ({ page }) => {
	await page.goto('/');
	await expect(
		page.getByRole('heading', { level: 1, name: 'Ogni eroe merita il suo diario.' })
	).toBeVisible();
	await expect(page.getByRole('button', { name: 'Crea il tuo diario' })).toBeVisible();
});

test.describe('theme', () => {
	test.describe('light system preference', () => {
		test.use({ colorScheme: 'light' });

		test('uses the parchment theme', async ({ page }) => {
			await page.goto('/');
			await expect(page.locator('html')).toHaveAttribute('data-theme', 'pergamena');
		});
	});

	test.describe('dark system preference', () => {
		test.use({ colorScheme: 'dark' });

		test('uses the purple theme', async ({ page }) => {
			await page.goto('/');
			await expect(page.locator('html')).toHaveAttribute('data-theme', 'viola');
		});
	});
});

test.describe('theme switch', () => {
	test.use({ colorScheme: 'light' });

	test('cycles light, dark and system with one button and keeps the choice', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: 'Tema: sistema. Passa a chiaro' }).click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'pergamena');

		await page.getByRole('button', { name: 'Tema: chiaro. Passa a scuro' }).click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'viola');

		await page.reload();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'viola');
		await expect(page.getByRole('button', { name: 'Tema: scuro. Passa a sistema' })).toBeVisible();

		await page.getByRole('button', { name: 'Tema: scuro. Passa a sistema' }).click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'pergamena');
		expect(await page.evaluate(() => document.cookie)).not.toContain('gdr-theme');
	});

	test('follows the system when the system preference changes', async ({ page }) => {
		await page.goto('/');
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'pergamena');

		await page.emulateMedia({ colorScheme: 'dark' });
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'viola');
	});

	test('ignores the system once a theme is chosen', async ({ page }) => {
		await page.goto('/');
		await page.getByRole('button', { name: 'Tema: sistema. Passa a chiaro' }).click();

		await page.emulateMedia({ colorScheme: 'dark' });
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'pergamena');
	});
});

test.describe('mobile', () => {
	test.use({ viewport: { width: 390, height: 844 } });

	test('does not scroll horizontally', async ({ page }) => {
		await page.goto('/');
		const overflow = await page.evaluate(
			() => document.documentElement.scrollWidth - document.documentElement.clientWidth
		);
		expect(overflow).toBe(0);
	});
});

test('on load the app asks Keycloak for an existing session without leaving the page', async ({
	page
}) => {
	const asked = page.waitForRequest(
		(request) => new URL(request.url()).searchParams.get('prompt') === 'none'
	);

	await page.goto('/');
	const request = await asked;

	expect(new URL(request.url()).pathname).toContain('/protocol/openid-connect/auth');
	expect(new URL(request.url()).searchParams.get('redirect_uri')).toContain(
		'/silent-check-sso.html'
	);
	await expect(
		page.getByRole('navigation', { name: 'Principale' }).getByRole('button', { name: 'Accedi' })
	).toBeEnabled();
	expect(new URL(page.url()).pathname).toBe('/');
});

test('the silent check page hands the answer back to the app', async ({ page }) => {
	const response = await page.request.get('/silent-check-sso.html');

	expect(response.status()).toBe(200);
	expect(await response.text()).toContain('parent.postMessage(location.href, location.origin)');
});

test('protected area sends anonymous visitors to the Keycloak login with PKCE', async ({
	page
}) => {
	await page.goto('/characters');
	await page.waitForURL(/localhost:8180\/realms\/gdr-diary-dev\/protocol\/openid-connect\/auth/);

	const url = new URL(page.url());
	expect(url.searchParams.get('client_id')).toBe('gdr-diary-api');
	expect(url.searchParams.get('code_challenge_method')).toBe('S256');
	expect(url.searchParams.get('code_challenge')).toBeTruthy();
});

test('the register button sends the visitor to the Keycloak registration page', async ({
	page
}) => {
	await page.goto('/');
	await page
		.getByRole('navigation', { name: 'Principale' })
		.getByRole('button', { name: 'Registrati' })
		.click();
	await page.waitForURL(
		/localhost:8180\/realms\/gdr-diary-dev\/protocol\/openid-connect\/registrations/
	);

	const url = new URL(page.url());
	expect(url.searchParams.get('client_id')).toBe('gdr-diary-api');
	expect(url.searchParams.get('redirect_uri')).toContain('/characters');
});

test('the login button sends the visitor to the Keycloak login page', async ({ page }) => {
	await page.goto('/');
	await page
		.getByRole('navigation', { name: 'Principale' })
		.getByRole('button', { name: 'Accedi' })
		.click();
	await page.waitForURL(/localhost:8180\/realms\/gdr-diary-dev\/protocol\/openid-connect\/auth/);

	expect(new URL(page.url()).searchParams.get('redirect_uri')).toContain('/characters');
});

import { devices, type Page } from '@playwright/test';
import { expect, test } from './fixtures.js';

type Device = (typeof devices)[string];

function phone(device: Device): {
	viewport: Device['viewport'];
	userAgent: string;
	deviceScaleFactor: number;
	isMobile: boolean;
	hasTouch: boolean;
} {
	return {
		viewport: device.viewport,
		userAgent: device.userAgent,
		deviceScaleFactor: device.deviceScaleFactor,
		isMobile: device.isMobile,
		hasTouch: device.hasTouch
	};
}

async function overflowOf(page: Page): Promise<number> {
	return page.evaluate(
		() => document.documentElement.scrollWidth - document.documentElement.clientWidth
	);
}

const phones = [
	{ name: 'Android phone', options: phone(devices['Pixel 7']) },
	{ name: 'iPhone', options: phone(devices['iPhone 13']) }
];

for (const { name, options } of phones) {
	test.describe(name, () => {
		test.use(options);

		test('opens the landing page without sideways scroll', async ({ page }) => {
			await page.goto('/');
			await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
			const overflow = await page.evaluate(
				() => document.documentElement.scrollWidth - document.documentElement.clientWidth
			);
			expect(overflow).toBe(0);
		});

		test('opens the privacy page without sideways scroll', async ({ page }) => {
			await page.goto('/privacy');

			await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
			expect(await overflowOf(page)).toBe(0);
		});

		test('opens the not found page without sideways scroll', async ({ page }) => {
			await page.goto('/pagina-che-non-esiste');

			await expect(page.getByRole('heading', { level: 1 })).toHaveText('Pagina non trovata');
			expect(await overflowOf(page)).toBe(0);
		});

		test('lets the page cover the whole screen and tells the browser its colour', async ({
			page
		}) => {
			await page.goto('/');
			await expect(page.locator('meta[name="viewport"]')).toHaveAttribute(
				'content',
				/viewport-fit=cover/
			);
			await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute(
				'content',
				/^#[0-9A-Fa-f]{6}$/
			);
		});

		test('the theme button works with a tap and updates the browser colour', async ({ page }) => {
			await page.emulateMedia({ colorScheme: 'light' });
			await page.goto('/');
			await page.getByRole('button', { name: 'Apri il menu' }).tap();
			await page.getByRole('button', { name: 'Tema: sistema. Passa a chiaro' }).tap();
			await page.getByRole('button', { name: 'Tema: chiaro. Passa a scuro' }).tap();

			await expect(page.locator('html')).toHaveAttribute('data-theme', 'viola');
			await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#12031F');
		});

		test('starts the login with a tap', async ({ page }) => {
			await page.goto('/');
			const login = page.getByRole('button', { name: 'Crea il tuo diario' });
			await expect(login).toBeVisible();
			const box = await login.boundingBox();
			expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
		});
	});
}

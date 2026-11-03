import type { Page } from '@playwright/test';
import { expect, test } from './fixtures.js';

const widths = [280, 320, 360, 390, 412, 600, 768, 820, 1024, 1280, 1366, 1536, 1920, 2560, 3440];

const landscapePhones = [
	{ width: 667, height: 375 },
	{ width: 844, height: 390 }
];

const routes = ['/', '/privacy', '/pagina-che-non-esiste'];

async function expectNoHorizontalScroll(page: Page): Promise<void> {
	const overflow = await page.evaluate(
		() => document.documentElement.scrollWidth - document.documentElement.clientWidth
	);
	expect(overflow).toBe(0);
}

for (const route of routes) {
	for (const width of widths) {
		test(`${route} fits a ${String(width)} px wide screen`, async ({ page }) => {
			await page.setViewportSize({ width, height: 800 });
			await page.goto(route);
			await expect(page.getByRole('main')).toBeVisible();
			await expectNoHorizontalScroll(page);
		});
	}

	for (const { width, height } of landscapePhones) {
		test(`${route} fits a phone turned sideways (${String(width)} x ${String(height)})`, async ({
			page
		}) => {
			await page.setViewportSize({ width, height });
			await page.goto(route);
			await expect(page.getByRole('main')).toBeVisible();
			await expectNoHorizontalScroll(page);
		});
	}
}

async function openLanding(page: Page, width: number): Promise<void> {
	await page.setViewportSize({ width, height: 800 });
	await page.goto('/');
	await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
}

async function expectLandingControls(page: Page): Promise<void> {
	await expect(
		page.getByRole('navigation', { name: 'Principale' }).getByRole('button', { name: 'Accedi' })
	).toBeVisible();
	await expect(page.getByRole('button', { name: /^Tema:/ })).toBeVisible();
	await expect(
		page.getByRole('navigation', { name: 'Principale' }).getByRole('button', { name: 'Registrati' })
	).toBeVisible();
	await expect(page.getByRole('button', { name: 'Crea il tuo diario' })).toBeVisible();

	const tooSmall = await page.evaluate(
		() =>
			Array.from(document.querySelectorAll('header button, header a, footer a'))
				.map((element) => element.getBoundingClientRect())
				.filter((box) => box.width > 0 && (box.height < 44 || box.width < 44)).length
	);
	expect(tooSmall).toBe(0);
}

test.describe('landing page controls stay usable on every width', () => {
	for (const width of widths.filter((value) => value < 900)) {
		test(`${String(width)} px with the menu open`, async ({ page }) => {
			await openLanding(page, width);
			await page.getByRole('button', { name: 'Apri il menu' }).click();
			await expectNoHorizontalScroll(page);
			await expectLandingControls(page);
		});
	}

	for (const width of widths.filter((value) => value >= 900)) {
		test(`${String(width)} px`, async ({ page }) => {
			await openLanding(page, width);
			await expectLandingControls(page);
		});
	}
});

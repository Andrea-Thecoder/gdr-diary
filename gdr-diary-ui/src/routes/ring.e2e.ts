import type { Page } from '@playwright/test';
import { expect, test } from './fixtures.js';

async function angles(page: Page): Promise<{ outer: number; inner: number }> {
	return page.evaluate(() => {
		function angle(name: string): number {
			const group = document.querySelector(`[data-ring="${name}"]`);
			if (group === null) {
				throw new Error(`Ring ${name} not found`);
			}
			const matrix = new DOMMatrix(getComputedStyle(group).transform);
			return (Math.atan2(matrix.b, matrix.a) * 180) / Math.PI;
		}
		return { outer: angle('outer'), inner: angle('inner') };
	});
}

async function animationNames(page: Page): Promise<string[]> {
	return page.evaluate(() =>
		['outer', 'inner'].map((name) => {
			const group = document.querySelector(`[data-ring="${name}"]`);
			if (group === null) {
				throw new Error(`Ring ${name} not found`);
			}
			return getComputedStyle(group).animationName;
		})
	);
}

test.describe('arcane ring', () => {
	test.use({ reducedMotion: 'no-preference' });

	test('turns slowly: the outside counter-clockwise, the inside clockwise', async ({ page }) => {
		await page.goto('/');
		await expect(page.locator('[data-ring="outer"]')).toBeAttached();
		const before = await angles(page);
		await expect.poll(async () => (await angles(page)).outer).toBeLessThan(before.outer);
		await expect.poll(async () => (await angles(page)).inner).toBeGreaterThan(before.inner);
		const after = await angles(page);

		expect(Math.abs(after.outer - before.outer)).toBeLessThan(15);
		expect(Math.abs(after.inner - before.inner)).toBeLessThan(15);
	});

	for (const scheme of ['light', 'dark'] as const) {
		test(`shows both rings in ${scheme} mode`, async ({ page }) => {
			await page.emulateMedia({ colorScheme: scheme });
			await page.goto('/');
			const outer = await page.locator('[data-ring="outer"]').boundingBox();
			const inner = await page.locator('[data-ring="inner"]').boundingBox();

			expect(outer?.width ?? 0).toBeGreaterThan(100);
			expect(inner?.width ?? 0).toBeGreaterThan(100);
		});
	}
});

test.describe('arcane ring with reduced motion', () => {
	test.use({ reducedMotion: 'reduce' });

	test('stays still', async ({ page }) => {
		await page.goto('/');
		await expect(page.locator('[data-ring="outer"]')).toBeAttached();

		expect(await animationNames(page)).toEqual(['none', 'none']);
	});
});

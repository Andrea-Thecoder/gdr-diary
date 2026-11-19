import { expect, test } from './fixtures.js';

for (const width of [390, 768, 1360]) {
	test(`selected headings do not overlap from one line to the next at ${String(width)} px`, async ({
		page
	}) => {
		await page.setViewportSize({ width, height: 900 });
		await page.goto('/');
		await page.getByRole('heading', { level: 1 }).waitFor();
		await page.evaluate(() => document.fonts.ready);

		const overlaps = await page.evaluate(() => {
			const found: string[] = [];
			for (const heading of document.querySelectorAll('h1, h2, h3')) {
				const lineHeight = parseFloat(getComputedStyle(heading).lineHeight);
				const range = document.createRange();
				range.selectNodeContents(heading);
				const tallest = Math.max(...Array.from(range.getClientRects()).map((rect) => rect.height));
				if (tallest > lineHeight + 1) {
					found.push(`${heading.textContent} (${String(tallest)} > ${String(lineHeight)})`);
				}
			}
			return found;
		});

		expect(overlaps).toEqual([]);
	});
}

import { createRawSnippet } from 'svelte';
import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import CardGrid from './CardGrid.svelte';

function cards(count: number): ReturnType<typeof createRawSnippet> {
	return createRawSnippet(() => ({
		render: () =>
			`<div style="display:contents">${Array.from({ length: count }, (_, index) => `<li data-card="${String(index)}" style="height:40px">Card</li>`).join('')}</div>`
	}));
}

function columns(): number {
	const tops = [...document.querySelectorAll<HTMLElement>('[data-card]')].map(
		(card) => card.getBoundingClientRect().top
	);
	return tops.filter((top) => top === tops[0]).length;
}

describe('CardGrid', () => {
	it('keeps one column on a phone', async () => {
		await page.viewport(390, 800);
		render(CardGrid, { children: cards(5) });

		expect(columns()).toBe(1);
	});

	it('shows two columns on a portrait tablet', async () => {
		await page.viewport(706, 900);
		render(CardGrid, { children: cards(5) });

		expect(columns()).toBe(2);
	});

	it('shows three columns on a landscape tablet and never more', async () => {
		await page.viewport(942, 900);
		render(CardGrid, { children: cards(7) });
		expect(columns()).toBe(3);
		document.body.innerHTML = '';

		await page.viewport(1200, 900);
		render(CardGrid, { children: cards(7) });
		expect(columns()).toBe(3);
	});
});

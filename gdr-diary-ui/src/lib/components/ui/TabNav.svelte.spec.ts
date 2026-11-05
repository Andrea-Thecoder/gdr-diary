import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import TabNav from './TabNav.svelte';

const tabs = [
	{ id: 'one', label: 'Diari', href: '/a', count: 3, countLabel: '3 diari' },
	{ id: 'two', label: 'Schede', suffix: ' del personaggio', href: '/b' },
	{ id: 'three', label: 'Altro', href: '/c' }
] as const;

describe('TabNav', () => {
	it('is a labelled navigation with one link per tab', async () => {
		render(TabNav, { label: 'Contenuti', tabs, value: 'one' });

		await expect.element(page.getByRole('navigation', { name: 'Contenuti' })).toBeVisible();
		const links = page.getByRole('link');
		expect(links.elements()).toHaveLength(3);
		await expect.element(links.nth(1)).toHaveAttribute('href', '/b');
	});

	it('marks only the current tab', async () => {
		render(TabNav, { label: 'Contenuti', tabs, value: 'two' });

		await expect
			.element(page.getByRole('link', { name: /Schede/ }))
			.toHaveAttribute('aria-current', 'page');
		await expect
			.element(page.getByRole('link', { name: /Diari/ }))
			.not.toHaveAttribute('aria-current');
	});

	it('shows the count of a tab and reads it out in words', async () => {
		render(TabNav, { label: 'Contenuti', tabs, value: 'one' });

		await expect.element(page.getByText('3 diari')).toBeInTheDocument();
		expect(page.getByRole('link', { name: /Diari/ }).element().textContent).toContain('3');
	});

	it('keeps the long name of a tab on wide screens and drops it on narrow ones', async () => {
		await page.viewport(1280, 900);
		render(TabNav, { label: 'Contenuti', tabs, value: 'one' });

		await expect.element(page.getByRole('link', { name: 'Schede del personaggio' })).toBeVisible();

		await page.viewport(390, 844);
		await expect.element(page.getByRole('link', { name: 'Schede', exact: true })).toBeVisible();
	});
});

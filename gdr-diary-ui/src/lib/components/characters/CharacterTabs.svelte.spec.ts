import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import CharacterTabs from './CharacterTabs.svelte';

describe('CharacterTabs', () => {
	it('links the three tabs to the pages of the character', async () => {
		render(CharacterTabs, { characterId: 'id-alira', tab: 'diaries' });

		await expect
			.element(page.getByRole('link', { name: /Diari/ }))
			.toHaveAttribute('href', '/characters/id-alira');
		await expect
			.element(page.getByRole('link', { name: /Schede/ }))
			.toHaveAttribute('href', '/characters/id-alira/sheets');
		await expect
			.element(page.getByRole('link', { name: 'Altro' }))
			.toHaveAttribute('href', '/characters/id-alira/other');
	});

	it('marks the open tab as current', async () => {
		render(CharacterTabs, { characterId: 'id-alira', tab: 'other' });

		await expect
			.element(page.getByRole('link', { name: 'Altro' }))
			.toHaveAttribute('aria-current', 'page');
		await expect
			.element(page.getByRole('link', { name: /Diari/ }))
			.not.toHaveAttribute('aria-current');
	});

	it('shows the count on the open tab only', async () => {
		render(CharacterTabs, { characterId: 'id-alira', tab: 'sheets', count: 4 });

		await expect.element(page.getByText('4 schede')).toBeInTheDocument();
		expect(page.getByRole('link', { name: /Diari/ }).element().textContent).not.toMatch(/\d/);
	});

	it('shows no count while it is not known', () => {
		render(CharacterTabs, { characterId: 'id-alira', tab: 'diaries' });

		expect(page.getByRole('navigation').element().textContent).not.toMatch(/\d/);
	});
});

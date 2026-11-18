import '../../../routes/layout.css';
import { describe, expect, it, vi } from 'vitest';
import { page } from 'vitest/browser';
import { render } from 'vitest-browser-svelte';
import BackgroundBox from './BackgroundBox.svelte';

vi.mock('$app/paths', () => ({
	resolve: (route: string, params: { characterId: string }) =>
		route.replace('/(app)', '').replace('[characterId]', params.characterId)
}));

describe('BackgroundBox with a story', () => {
	it('shows the first words of the story', async () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin', preview: 'Nato a Erebor.' });
		await expect.element(page.getByText('Nato a Erebor.')).toBeVisible();
	});

	it('links to the whole story', async () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin', preview: 'Nato a Erebor.' });
		await expect
			.element(page.getByRole('link', { name: 'Leggi tutto' }))
			.toHaveAttribute('href', '/characters/c1/background');
	});

	it('does not invite to write when there is a story', async () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin', preview: 'Nato a Erebor.' });
		await expect.element(page.getByText('Scrivi la storia di Thorin')).not.toBeInTheDocument();
	});

	it('keeps long text inside three lines', () => {
		render(BackgroundBox, {
			characterId: 'c1',
			name: 'Thorin',
			preview: 'parola '.repeat(200)
		});
		const element = page.getByText('parola', { exact: false }).first().element();
		expect(getComputedStyle(element).overflow).toBe('hidden');
	});

	it('has a button as tall as a finger', () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin', preview: 'Nato a Erebor.' });
		const link = page.getByRole('link', { name: 'Leggi tutto' }).element();
		expect(link.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
	});
});

describe('BackgroundBox without a story', () => {
	it.each([[null], [undefined], [''], ['   ']])(
		'invites to write the story when the preview is %j',
		async (preview) => {
			render(BackgroundBox, { characterId: 'c1', name: 'Thorin', preview });
			await expect
				.element(page.getByRole('link', { name: 'Scrivi la storia di Thorin' }))
				.toBeVisible();
		}
	);

	it('opens the story from the invitation', async () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin' });
		await expect
			.element(page.getByRole('link', { name: 'Scrivi la storia di Thorin' }))
			.toHaveAttribute('href', '/characters/c1/background');
	});

	it('does not show the box of a written story', async () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin' });
		await expect.element(page.getByText('Leggi tutto')).not.toBeInTheDocument();
	});

	it('is at least as tall as a finger', () => {
		render(BackgroundBox, { characterId: 'c1', name: 'Thorin' });
		const link = page.getByRole('link', { name: 'Scrivi la storia di Thorin' }).element();
		expect(link.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
	});
});

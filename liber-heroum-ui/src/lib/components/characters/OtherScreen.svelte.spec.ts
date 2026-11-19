import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { CharacterDetail } from '#lib/characters/api.js';
import OtherScreen from './OtherScreen.svelte';

const character: CharacterDetail = {
	id: 'id-alira',
	kind: 'PLAYER',
	name: 'Alira dei Salici',
	avatarUrl: null
};

describe('OtherScreen', () => {
	it('opens on the Altro tab of the character', async () => {
		render(OtherScreen, { character });

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Alira dei Salici');
		await expect
			.element(page.getByRole('link', { name: 'Altro' }))
			.toHaveAttribute('aria-current', 'page');
	});

	it('previews the three kinds of content that are coming', async () => {
		render(OtherScreen, { character });

		for (const title of ['Mappe', 'Immagini', 'Appunti e documenti']) {
			await expect.element(page.getByRole('heading', { name: title })).toBeVisible();
		}
		expect(page.getByRole('img', { name: /in arrivo/ }).elements()).toHaveLength(3);
	});

	it('has nothing to click on the previews', () => {
		render(OtherScreen, { character });

		const buttons = page.getByRole('button', { name: 'In arrivo' });
		expect(buttons.elements()).toHaveLength(3);
		for (const button of buttons.elements()) {
			expect((button as HTMLButtonElement).disabled).toBe(true);
		}
	});
});

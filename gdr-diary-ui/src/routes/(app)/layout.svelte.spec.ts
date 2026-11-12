import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { createRawSnippet } from 'svelte';
import '../layout.css';

const { state } = vi.hoisted(() => ({ state: { page: { route: { id: '/(app)/characters' } } } }));

vi.mock('$app/state', () => state);
vi.mock('$app/navigation', () => ({ afterNavigate: vi.fn() }));
vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: {
		status: 'authenticated',
		user: { sub: 'x', email: 'nome@esempio.it', name: 'Lyra', nickname: 'lyra', roles: [] },
		logout: vi.fn()
	}
}));

import Layout from './+layout.svelte';

const children = createRawSnippet(() => ({ render: () => '<p>Contenuto</p>' }));

describe('app layout', () => {
	beforeEach(() => {
		state.page.route.id = '/(app)/characters';
	});

	it('shows the footer with the information pages under the content', async () => {
		render(Layout, { children } as never);

		await expect
			.element(page.getByRole('navigation', { name: 'Pagine informative' }))
			.toBeVisible();
	});

	it('keeps the footer under the characters of a diary list', async () => {
		state.page.route.id = '/(app)/characters/[characterId]';
		render(Layout, { children } as never);

		await expect.element(page.getByRole('contentinfo')).toBeVisible();
	});

	it('leaves the open diary without a footer, to give the room to the book', async () => {
		state.page.route.id = '/(app)/diaries/[diaryId]';
		render(Layout, { children } as never);

		await expect.element(page.getByRole('main')).toHaveTextContent('Contenuto');
		expect(page.getByRole('contentinfo').elements()).toHaveLength(0);
	});
});

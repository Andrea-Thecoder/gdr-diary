import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../layout.css';
import type * as FeedbackModule from '#lib/feedback/feedback.js';

vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: { status: 'anonymous', init: () => Promise.resolve(), login: vi.fn() }
}));
interface Navigation {
	from: { route: { id: string | null } } | null;
}

const { navigation, sendFeedback } = vi.hoisted(() => ({
	navigation: { from: null as Navigation['from'] },
	sendFeedback: vi.fn<(baseUrl: string, draft: { from: string }) => Promise<void>>()
}));

vi.mock('$app/navigation', () => ({
	goto: vi.fn(),
	afterNavigate: (callback: (value: Navigation) => void) => {
		callback({ from: navigation.from });
	}
}));
vi.mock('#lib/feedback/feedback.js', async (importOriginal) => ({
	...(await importOriginal<typeof FeedbackModule>()),
	sendFeedback
}));
vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/feedback') } }));

import Page from './+page.svelte';

describe('feedback page', () => {
	it('shows the title', async () => {
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Raccontaci la tua esperienza');
	});

	it('shows the form', async () => {
		render(Page);

		await expect.element(page.getByRole('form', { name: 'Modulo di feedback' })).toBeVisible();
	});

	it('does not explain what happens next', () => {
		render(Page);

		expect(page.getByRole('heading', { name: 'Cosa succede dopo' }).elements()).toHaveLength(0);
	});

	it('points to the questions and to the contacts', async () => {
		render(Page);
		const other = page.getByRole('navigation', { name: 'Altre pagine' });

		await expect
			.element(other.getByRole('link', { name: 'Le domande frequenti' }))
			.toHaveAttribute('href', '/faq');
		await expect
			.element(other.getByRole('link', { name: 'Scrivimi direttamente' }))
			.toHaveAttribute('href', '/contatti');
	});

	it('sends the screen the visitor came from, without numbers or groups', async () => {
		navigation.from = { route: { id: '/(app)/diaries/[diaryId]' } };
		sendFeedback.mockResolvedValue();
		render(Page);

		await page.getByLabelText('Il tuo messaggio').fill('Il segnalibro non si sposta.');
		await page.getByLabelText('La tua email').fill('nome@esempio.it');
		await page.getByRole('checkbox', { name: /Ho letto/ }).click();
		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(sendFeedback.mock.calls[0]?.[1].from).toBe('/diaries/[diaryId]');
	});

	it('sends nothing as the screen when the visitor came straight in', async () => {
		navigation.from = null;
		sendFeedback.mockResolvedValue();
		render(Page);

		await page.getByLabelText('Il tuo messaggio').fill('Ciao');
		await page.getByLabelText('La tua email').fill('nome@esempio.it');
		await page.getByRole('checkbox', { name: /Ho letto/ }).click();
		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(sendFeedback.mock.calls.at(-1)?.[1].from).toBe('');
	});

	it('shows the links to other pages beside the form', async () => {
		render(Page);

		await expect.element(page.getByRole('heading', { name: 'Cerchi altro?' })).toBeVisible();
	});

	it('shows only the thanks, without the side column, once the message is sent', async () => {
		sendFeedback.mockResolvedValue();
		render(Page);

		await page.getByLabelText('Il tuo messaggio').fill('Ciao');
		await page.getByLabelText('La tua email').fill('nome@esempio.it');
		await page.getByRole('checkbox', { name: /Ho letto/ }).click();
		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect
			.element(page.getByRole('heading', { name: 'Grazie, messaggio ricevuto.' }))
			.toBeVisible();
		expect(page.getByRole('heading', { name: 'Cerchi altro?' }).elements()).toHaveLength(0);
	});
});

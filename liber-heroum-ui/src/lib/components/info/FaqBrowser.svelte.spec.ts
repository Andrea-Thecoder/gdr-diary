import { page, userEvent } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { FaqGroup } from '#lib/info/faq.js';
import FaqBrowser from './FaqBrowser.svelte';

const groups: FaqGroup[] = [
	{
		id: 'uno',
		title: 'Primo argomento',
		items: [
			{ question: 'Domanda uno?', answer: 'Risposta uno.' },
			{ question: 'Domanda due?', answer: 'Risposta due.' }
		]
	},
	{
		id: 'due',
		title: 'Secondo argomento',
		items: [{ question: 'Domanda tre?', answer: 'Risposta tre.' }]
	}
];

const topics = (): ReturnType<typeof page.getByRole> =>
	page.getByRole('navigation', { name: 'Argomenti' });

describe('FaqBrowser', () => {
	it('lists the topics with how many questions each one has', async () => {
		render(FaqBrowser, { groups, query: '' });

		await expect.element(topics().getByRole('link', { name: 'Primo argomento 2' })).toBeVisible();
		await expect.element(topics().getByRole('link', { name: 'Secondo argomento 1' })).toBeVisible();
	});

	it('shows one heading for each topic', async () => {
		render(FaqBrowser, { groups, query: '' });

		await expect
			.element(page.getByRole('heading', { level: 2, name: 'Primo argomento' }))
			.toBeVisible();
		await expect
			.element(page.getByRole('heading', { level: 2, name: 'Secondo argomento' }))
			.toBeVisible();
	});

	it('opens only the first question at the start', () => {
		render(FaqBrowser, { groups, query: '' });

		const open = Array.from(document.querySelectorAll('details')).map((item) => item.open);

		expect(open).toEqual([true, false, false]);
	});

	it('opens a question when its title is clicked', async () => {
		render(FaqBrowser, { groups, query: '' });

		await page.getByText('Domanda due?').click();

		await expect.element(page.getByText('Risposta due.')).toBeVisible();
	});

	it('marks the first topic as the current one at the start', async () => {
		render(FaqBrowser, { groups, query: '' });

		await expect
			.element(topics().getByRole('link', { name: 'Primo argomento 2' }))
			.toHaveAttribute('aria-current', 'true');
	});

	it('marks the topic that was clicked as the current one', async () => {
		render(FaqBrowser, { groups, query: '' });

		await topics().getByRole('link', { name: 'Secondo argomento 1' }).click();

		await expect
			.element(topics().getByRole('link', { name: 'Secondo argomento 1' }))
			.toHaveAttribute('aria-current', 'true');
		await expect
			.element(topics().getByRole('link', { name: 'Primo argomento 2' }))
			.not.toHaveAttribute('aria-current');
	});

	it('points every topic link at the section with its questions', async () => {
		render(FaqBrowser, { groups, query: '' });

		await expect
			.element(topics().getByRole('link', { name: 'Secondo argomento 1' }))
			.toHaveAttribute('href', '#due');
		expect(document.getElementById('due')?.tagName).toBe('SECTION');
	});

	it('keeps only the questions that match the search', async () => {
		render(FaqBrowser, { groups, query: 'tre' });

		await expect.element(page.getByText('Domanda tre?')).toBeVisible();
		expect(page.getByText('Domanda uno?').elements()).toHaveLength(0);
		expect(page.getByRole('heading', { level: 2 }).elements()).toHaveLength(1);
	});

	it('opens the matching questions so the answer can be read', () => {
		render(FaqBrowser, { groups, query: 'risposta' });

		const open = Array.from(document.querySelectorAll('details')).map((item) => item.open);

		expect(open).toEqual([true, true, true]);
	});

	it('says that nothing matches', async () => {
		render(FaqBrowser, { groups, query: 'astronave' });

		await expect
			.element(page.getByRole('status'))
			.toHaveTextContent('Nessuna domanda corrisponde a «astronave»');
	});

	it('lets the keyboard open a question', async () => {
		render(FaqBrowser, { groups, query: '' });
		page.getByText('Domanda due?').element().focus();

		await userEvent.keyboard('{Enter}');

		await expect.element(page.getByText('Risposta due.')).toBeVisible();
	});
});

import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

const { api } = vi.hoisted(() => ({
	api: { GET: vi.fn<(...args: unknown[]) => Promise<unknown>>() }
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));

import PaymentsSection from './PaymentsSection.svelte';

const paid = {
	id: 'p1',
	status: 'SUCCEEDED',
	amountCents: 1999,
	currency: 'EUR',
	description: 'Premium annuale',
	paidAt: '2026-03-14T10:00:00Z',
	invoiceNumber: '2026/1',
	receiptUrl: 'https://pay.test/receipt/p1'
};

function pageOf(items: unknown[], totalPages = 1): { data: unknown } {
	return { data: { list: items, totalRows: items.length, totalPages, page: 1, pageSize: 10 } };
}

describe('payments section', () => {
	beforeEach(async () => {
		api.GET.mockReset();
		await page.viewport(390, 844);
	});

	it('says so when there are no payments', async () => {
		api.GET.mockResolvedValue(pageOf([]));

		render(PaymentsSection);

		await expect.element(page.getByText('Non hai ancora fatto pagamenti.')).toBeVisible();
	});

	it('shows what was paid, when and how much', async () => {
		api.GET.mockResolvedValue(pageOf([paid]));

		render(PaymentsSection);

		const row = page.getByRole('listitem');
		await expect.element(row).toHaveTextContent(/Premium annuale.*14 marzo 2026.*Pagato.*19,99/);
	});

	it('opens the receipt of the provider in a new tab', async () => {
		api.GET.mockResolvedValue(pageOf([paid]));

		render(PaymentsSection);

		await expect
			.element(page.getByRole('link', { name: 'Ricevuta' }))
			.toHaveAttribute('target', '_blank');
	});

	it('offers more payments when there is another page', async () => {
		api.GET.mockResolvedValue(pageOf([paid], 2));

		render(PaymentsSection);

		await expect
			.element(page.getByRole('button', { name: 'Mostra altri pagamenti' }))
			.toBeVisible();
	});

	it('says why when the payments cannot be read', async () => {
		api.GET.mockResolvedValue({ error: { title: 'INTERNAL_ERROR', status: 500 } });

		render(PaymentsSection);

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Si è verificato un errore imprevisto: riprova più tardi');
	});

	it('keeps the row within a phone screen', async () => {
		api.GET.mockResolvedValue(pageOf([{ ...paid, description: 'Premium annuale '.repeat(8) }]));

		render(PaymentsSection);
		await expect.element(page.getByRole('listitem')).toBeVisible();

		expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(390);
	});
});

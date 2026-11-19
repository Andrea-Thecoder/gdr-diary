import { beforeEach, describe, expect, it, vi } from 'vitest';

const { api, fetchSheet } = vi.hoisted(() => ({
	api: { name: 'api' },
	fetchSheet: vi.fn<(api: unknown, id: string) => Promise<unknown>>()
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/sheets/api.js', () => ({ fetchSheet }));

import { loadSheet } from '#lib/sheets/load.js';

describe('loadSheet', () => {
	beforeEach(() => {
		fetchSheet.mockReset();
	});

	it('hands the sheet to the page', async () => {
		fetchSheet.mockResolvedValue({ id: 's1' });

		await expect(loadSheet('s1')).resolves.toEqual({ sheet: { id: 's1' } });
		expect(fetchSheet).toHaveBeenCalledWith(api, 's1');
	});

	it('answers 404 with the message of the API when the sheet is missing', async () => {
		fetchSheet.mockRejectedValue(new Error('Scheda non trovata'));

		await expect(loadSheet('s1')).rejects.toMatchObject({
			status: 404,
			body: { message: 'Scheda non trovata' }
		});
	});

	it('answers 404 with a generic message when the failure is not an error', async () => {
		fetchSheet.mockRejectedValue('boom');

		await expect(loadSheet('s1')).rejects.toMatchObject({
			status: 404,
			body: { message: 'Non trovo questa scheda.' }
		});
	});

	it('answers 503 when the failure is a network error', async () => {
		fetchSheet.mockRejectedValue(new TypeError('Non riesco a raggiungere il server'));

		await expect(loadSheet('s1')).rejects.toMatchObject({ status: 503 });
	});
});

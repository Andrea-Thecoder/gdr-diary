import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

const { api, auth, navigation, startDownload } = vi.hoisted(() => ({
	api: { GET: vi.fn<(...args: unknown[]) => Promise<unknown>>() },
	auth: {
		recentlyAuthenticated: vi.fn<() => boolean>(),
		reauthenticate: vi.fn<(returnTo: string) => Promise<void>>()
	},
	navigation: { goto: vi.fn(() => Promise.resolve()) },
	startDownload: vi.fn<(url: string, fileName?: string) => void>()
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));
vi.mock('$app/navigation', () => navigation);
vi.mock('#lib/sheets/download.js', () => ({ startDownload }));

import { Notice } from '#lib/ui/notice.svelte.js';
import DataExportSection from './DataExportSection.svelte';

function archive(): { data: Blob; response: Response } {
	return {
		data: new Blob(['PK'], { type: 'application/zip' }),
		response: new Response(null, {
			headers: { 'Content-Disposition': 'attachment; filename="diario-gdr-2026-11-19.zip"' }
		})
	};
}

describe('data export section', () => {
	beforeEach(async () => {
		api.GET.mockReset();
		auth.recentlyAuthenticated.mockReset();
		auth.reauthenticate.mockReset();
		auth.reauthenticate.mockResolvedValue();
		startDownload.mockReset();
		window.history.replaceState(null, '', '/profile');
		await page.viewport(390, 844);
	});

	it('downloads the archive with the name the server gives after a recent login', async () => {
		auth.recentlyAuthenticated.mockReturnValue(true);
		api.GET.mockResolvedValue(archive());
		render(DataExportSection, { notice: new Notice() });

		await page.getByRole('button', { name: 'Scarica i miei dati' }).click();

		await expect.poll(() => startDownload.mock.calls.at(-1)?.[1]).toBe('diario-gdr-2026-11-19.zip');
	});

	it('says where to find the copy once it is downloaded', async () => {
		auth.recentlyAuthenticated.mockReturnValue(true);
		api.GET.mockResolvedValue(archive());
		const notice = new Notice();
		render(DataExportSection, { notice });

		await page.getByRole('button', { name: 'Scarica i miei dati' }).click();

		await expect
			.poll(() => notice.message)
			.toBe('Ecco la copia dei tuoi dati: la trovi tra i download.');
	});

	it('asks for the password first when the login is old, without calling the server', async () => {
		auth.recentlyAuthenticated.mockReturnValue(false);
		render(DataExportSection, { notice: new Notice() });

		await page.getByRole('button', { name: 'Scarica i miei dati' }).click();

		expect([auth.reauthenticate.mock.calls[0]?.[0], api.GET.mock.calls.length]).toEqual([
			`${window.location.origin}/profile?esporta=1`,
			0
		]);
	});

	it('asks for the password when the server finds the login too old', async () => {
		auth.recentlyAuthenticated.mockReturnValue(true);
		api.GET.mockResolvedValue({
			error: { title: 'UNAUTHORIZED', status: 401, violations: [] },
			response: new Response(null, { status: 401 })
		});
		render(DataExportSection, { notice: new Notice() });

		await page.getByRole('button', { name: 'Scarica i miei dati' }).click();

		await expect.poll(() => auth.reauthenticate.mock.calls.length).toBe(1);
	});

	it('shows the error and downloads nothing when the copy cannot be made', async () => {
		auth.recentlyAuthenticated.mockReturnValue(true);
		api.GET.mockResolvedValue({
			error: { title: 'INTERNAL_ERROR', status: 500, violations: [] },
			response: new Response(null, { status: 500 })
		});
		const notice = new Notice();
		render(DataExportSection, { notice });

		await page.getByRole('button', { name: 'Scarica i miei dati' }).click();

		await expect.poll(() => [notice.failed, startDownload.mock.calls.length]).toEqual([true, 0]);
	});

	it('starts the export by itself when it comes back from the password', async () => {
		window.history.replaceState(null, '', '/profile?esporta=1');
		auth.recentlyAuthenticated.mockReturnValue(true);
		api.GET.mockResolvedValue(archive());

		render(DataExportSection, { notice: new Notice() });

		await expect.poll(() => startDownload.mock.calls.length).toBe(1);
	});

	it('keeps the button as tall as a finger', async () => {
		render(DataExportSection, { notice: new Notice() });
		await expect.element(page.getByRole('button', { name: 'Scarica i miei dati' })).toBeVisible();

		const button = page.getByRole('button', { name: 'Scarica i miei dati' }).element();
		expect(button.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
	});
});

import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '#lib/api/client.js';
import { RecentLoginRequiredError } from './api.js';
import { DEFAULT_EXPORT_NAME, downloadUserExport, exportReturnUrl, wantsExport } from './export.js';

function apiAnswering(answer: unknown): { api: ApiClient; get: ReturnType<typeof vi.fn> } {
	const get = vi.fn(() => Promise.resolve(answer));
	return { api: { GET: get } as unknown as ApiClient, get };
}

describe('downloadUserExport', () => {
	it('asks the server for the archive as a file', async () => {
		const { api, get } = apiAnswering({ data: new Blob(['PK']), response: new Response(null) });

		await downloadUserExport(api);

		expect(get).toHaveBeenCalledWith('/me/export', { parseAs: 'blob' });
	});

	it('returns the archive with the name the server gives, or a default one', async () => {
		const blob = new Blob(['PK']);
		const named = apiAnswering({
			data: blob,
			response: new Response(null, {
				headers: { 'Content-Disposition': 'attachment; filename="diario-gdr-2026-11-19.zip"' }
			})
		});
		const unnamed = apiAnswering({ data: blob, response: new Response(null) });

		const files = [await downloadUserExport(named.api), await downloadUserExport(unnamed.api)];

		expect(files).toEqual([
			{ blob, fileName: 'diario-gdr-2026-11-19.zip' },
			{ blob, fileName: DEFAULT_EXPORT_NAME }
		]);
	});

	it('asks for the password again when the login is too old', async () => {
		const { api } = apiAnswering({
			error: { title: 'UNAUTHORIZED', status: 401, violations: [] },
			response: new Response(null, { status: 401 })
		});

		await expect(downloadUserExport(api)).rejects.toBeInstanceOf(RecentLoginRequiredError);
	});

	it('explains any other failure in Italian', async () => {
		const { api } = apiAnswering({
			error: { title: 'NOT_FOUND', status: 404, violations: [{ message: 'User not found' }] },
			response: new Response(null, { status: 404 })
		});

		await expect(downloadUserExport(api)).rejects.toThrow(/./);
	});
});

describe('export return address', () => {
	it('comes back to the profile asking to start the export', () => {
		const back = exportReturnUrl('http://localhost:5173');

		expect([
			new URL(back).pathname,
			wantsExport(back),
			wantsExport('http://localhost:5173/profile')
		]).toEqual(['/profile', true, false]);
	});
});

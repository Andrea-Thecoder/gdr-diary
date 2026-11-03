import { beforeEach, describe, expect, it, vi } from 'vitest';

const { api, fetchProfile, ensureAppUser } = vi.hoisted(() => ({
	api: { name: 'api' },
	fetchProfile: vi.fn<(api: unknown) => Promise<unknown>>(),
	ensureAppUser: vi.fn<() => Promise<string>>()
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('#lib/api/me.js', () => ({ ensureAppUser }));
vi.mock('#lib/profile/api.js', () => ({ fetchProfile }));

import { load } from './+page.js';

function runLoad(): Promise<unknown> {
	return (load as unknown as (event: unknown) => Promise<unknown>)({});
}

describe('profile page load', () => {
	beforeEach(() => {
		fetchProfile.mockReset();
		ensureAppUser.mockReset();
	});

	it('returns the profile and the id of the user together', async () => {
		const profile = { email: 'alira@example.com' };
		fetchProfile.mockResolvedValue(profile);
		ensureAppUser.mockResolvedValue('u1');

		await expect(runLoad()).resolves.toEqual({ profile, id: 'u1' });
		expect(fetchProfile).toHaveBeenCalledWith(api);
	});

	it('answers 502 when the profile cannot be read', async () => {
		fetchProfile.mockRejectedValue(new Error('Profilo non disponibile'));
		ensureAppUser.mockResolvedValue('u1');

		await expect(runLoad()).rejects.toMatchObject({
			status: 502,
			body: { message: 'Non riesco a leggere il tuo profilo. Riprova più tardi.' }
		});
	});

	it('answers 502 when the user cannot be created or found', async () => {
		fetchProfile.mockResolvedValue({ email: 'alira@example.com' });
		ensureAppUser.mockRejectedValue(new Error('Utente non trovato'));

		await expect(runLoad()).rejects.toMatchObject({
			status: 502,
			body: { message: 'Non riesco a leggere il tuo profilo. Riprova più tardi.' }
		});
	});
});

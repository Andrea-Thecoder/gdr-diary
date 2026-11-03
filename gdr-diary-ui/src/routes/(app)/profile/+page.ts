import { error } from '@sveltejs/kit';
import { getApi } from '#lib/api/index.js';
import { ensureAppUser } from '#lib/api/me.js';
import { fetchProfile } from '#lib/profile/api.js';
import type { PageLoad } from './$types';

export const load: PageLoad = async () => {
	try {
		const [profile, id] = await Promise.all([fetchProfile(getApi()), ensureAppUser()]);
		return { profile, id };
	} catch {
		error(502, 'Non riesco a leggere il tuo profilo. Riprova più tardi.');
	}
};

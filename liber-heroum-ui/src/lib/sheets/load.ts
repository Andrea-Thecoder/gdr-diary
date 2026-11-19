import { error } from '@sveltejs/kit';
import { getApi } from '#lib/api/index.js';
import { fetchSheet, type SheetDetail } from '#lib/sheets/api.js';

export async function loadSheet(sheetId: string): Promise<{ sheet: SheetDetail }> {
	try {
		return { sheet: await fetchSheet(getApi(), sheetId) };
	} catch (cause) {
		error(
			cause instanceof TypeError ? 503 : 404,
			cause instanceof Error ? cause.message : 'Non trovo questa scheda.'
		);
	}
}

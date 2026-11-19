import { loadSheet } from '#lib/sheets/load.js';
import type { PageLoad } from './$types';

export const load: PageLoad = ({ params }) => loadSheet(params.sheetId);

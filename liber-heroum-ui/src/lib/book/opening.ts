import type { Bookmark } from './bookmark.js';
import { parseSpread, spreadOf } from './spread.js';

export function openingSpread(
	requested: string | null,
	bookmark: Bookmark | null,
	totalPages: number
): number {
	const last = spreadOf(Math.max(totalPages, 1));
	if (requested !== null) {
		return Math.min(parseSpread(requested), last);
	}
	if (bookmark !== null) {
		return Math.min(spreadOf(Math.max(bookmark.pageNumber, 1)), last);
	}
	return last;
}

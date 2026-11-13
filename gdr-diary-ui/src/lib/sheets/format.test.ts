import { describe, expect, it } from 'vitest';
import {
	checkSheetFile,
	formatDate,
	isPdf,
	MAX_SHEET_BYTES,
	SHEET_NOT_PDF,
	SHEET_TOO_BIG
} from '#lib/sheets/format.js';

function file(name: string, type: string, size = 10): File {
	return new File([new Uint8Array(size)], name, { type });
}

describe('formatDate', () => {
	it('writes the date in long Italian form', () => {
		expect(formatDate('2026-03-14T10:00:00Z')).toBe('14 marzo 2026');
	});

	it('is empty when there is no date or it is not a date', () => {
		expect(formatDate(undefined)).toBe('');
		expect(formatDate(null)).toBe('');
		expect(formatDate('ieri')).toBe('');
	});
});

describe('isPdf', () => {
	it('accepts the PDF media type or the .pdf extension in any case', () => {
		expect(isPdf(file('scheda', 'application/pdf'))).toBe(true);
		expect(isPdf(file('Scheda.PDF', ''))).toBe(true);
	});

	it('rejects anything else', () => {
		expect(isPdf(file('scheda.docx', 'application/msword'))).toBe(false);
		expect(isPdf(file('pdf', 'image/png'))).toBe(false);
	});
});

describe('checkSheetFile', () => {
	it('accepts a PDF within the limit, limit included', () => {
		expect(checkSheetFile(file('a.pdf', 'application/pdf', MAX_SHEET_BYTES))).toBeNull();
	});

	it('explains that the file is not a PDF', () => {
		expect(checkSheetFile(file('a.png', 'image/png'))).toBe(SHEET_NOT_PDF);
	});

	it('explains that the file is too big', () => {
		expect(checkSheetFile(file('a.pdf', 'application/pdf', MAX_SHEET_BYTES + 1))).toBe(
			SHEET_TOO_BIG
		);
	});
});

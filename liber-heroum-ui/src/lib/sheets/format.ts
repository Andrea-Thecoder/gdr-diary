const MEGABYTE = 1024 * 1024;

export const MAX_SHEET_BYTES = 10 * MEGABYTE;
export const MAX_SHEET_LABEL = '10 MB';

export const SHEET_TOO_BIG = `Il file pesa troppo: il limite è ${MAX_SHEET_LABEL}.`;
export const SHEET_NOT_PDF = 'Il file non è un PDF: scegli un file con estensione .pdf.';

export function formatDate(iso: string | null | undefined): string {
	if (iso === null || iso === undefined) {
		return '';
	}
	const date = new Date(iso);
	if (Number.isNaN(date.getTime())) {
		return '';
	}
	return new Intl.DateTimeFormat('it-IT', {
		day: 'numeric',
		month: 'long',
		year: 'numeric'
	}).format(date);
}

export function isPdf(file: File): boolean {
	return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
}

export function checkSheetFile(file: File): string | null {
	if (!isPdf(file)) {
		return SHEET_NOT_PDF;
	}
	if (file.size > MAX_SHEET_BYTES) {
		return SHEET_TOO_BIG;
	}
	return null;
}

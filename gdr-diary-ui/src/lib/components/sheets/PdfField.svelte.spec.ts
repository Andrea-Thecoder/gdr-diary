import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import PdfField from './PdfField.svelte';

function pdf(name = 'scheda.pdf', type = 'application/pdf'): File {
	return new File(['%PDF-1.7'], name, { type });
}

function open(props: Record<string, unknown> = {}): {
	onfile: ReturnType<typeof vi.fn<(file: File) => void>>;
} {
	const onfile = vi.fn<(file: File) => void>();
	render(PdfField, { file: null, onfile, ...props } as never);
	return { onfile };
}

function drop(file: File): void {
	const zone = page.getByRole('group', { name: /File PDF/ }).element();
	const transfer = new DataTransfer();
	transfer.items.add(file);
	zone.dispatchEvent(
		new DragEvent('drop', { dataTransfer: transfer, bubbles: true, cancelable: true })
	);
}

describe('PdfField', () => {
	it('invites to drop or pick a file when it is empty', async () => {
		open();

		await expect.element(page.getByText('Trascina qui il PDF oppure')).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Scegli un file' })).toBeVisible();
		await expect.element(page.getByText('Solo file PDF, fino a 10 MB.')).toBeVisible();
	});

	it('hands a picked PDF over', async () => {
		const { onfile } = open();
		const file = pdf();

		await page.getByLabelText('File del PDF').upload(file);

		expect(onfile).toHaveBeenCalledWith(file);
	});

	it('hands a dropped PDF over', () => {
		const { onfile } = open();
		const file = pdf('trascinata.pdf');

		drop(file);

		expect(onfile).toHaveBeenCalledWith(file);
	});

	it('refuses a dropped file that is not a PDF and says why', async () => {
		const { onfile } = open();

		drop(pdf('foto.png', 'image/png'));

		await expect.element(page.getByRole('alert')).toHaveTextContent(/non è un PDF/);
		expect(onfile).not.toHaveBeenCalled();
	});

	it('shows the chosen file and a way to change it', async () => {
		open({ file: new File([new Uint8Array(2048)], 'livello-6.pdf', { type: 'application/pdf' }) });

		await expect.element(page.getByText('livello-6.pdf')).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Cambia' })).toBeVisible();
	});

	it('shows the current PDF of a sheet that is being edited', async () => {
		open({ current: { name: 'PDF attuale' } });

		await expect.element(page.getByText('PDF attuale')).toBeVisible();
	});

	it('shows the error handed by the form', async () => {
		open({ error: 'Scegli il PDF da caricare.' });

		await expect.element(page.getByRole('alert')).toHaveTextContent('Scegli il PDF da caricare.');
	});
});

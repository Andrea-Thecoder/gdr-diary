import { page, type Locator } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

type Call = (...args: unknown[]) => Promise<unknown>;

const { api, navigation } = vi.hoisted(() => ({
	api: { GET: vi.fn<Call>(), POST: vi.fn<Call>(), PUT: vi.fn<Call>(), DELETE: vi.fn<Call>() },
	navigation: {
		goto: vi.fn<(...args: unknown[]) => Promise<void>>(),
		beforeNavigate: vi.fn<(...args: unknown[]) => void>()
	}
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('$app/navigation', () => navigation);

import { required } from '#lib/test-support.js';
import { fillablePdfUrl, plainPdfUrl } from '#lib/pdf/test-support.js';
import { DEFAULT_PREFERENCES } from '#lib/settings/preferences.js';
import { preferences } from '#lib/settings/preferences.svelte.js';
import SheetEditorScreen from './SheetEditorScreen.svelte';

function sheetAt(url: string): Record<string, unknown> {
	return {
		id: 's-5',
		characterId: 'id-alira',
		title: 'Scheda livello 5',
		system: 'D&D 5e',
		fileName: 'alira-lv5.pdf',
		createdAt: '2026-03-14T10:00:00Z',
		url,
		downloadUrl: `${url}#download`
	};
}

function openSheet(url: string): void {
	render(SheetEditorScreen, { sheet: sheetAt(url) } as never);
}

// il primo campo di testo del PDF di prova è "nome"
function nameField(): Locator {
	return page.getByRole('textbox').first();
}

// pdf.js carica motore e documento in modo asincrono: i campi arrivano dopo un po'
async function fieldsDrawn(): Promise<void> {
	await expect.element(nameField(), { timeout: 15_000 }).toBeInTheDocument();
}

beforeEach(async () => {
	api.PUT.mockReset();
	// le prove che non riguardano il salvataggio automatico lo tengono spento
	preferences.value = { ...DEFAULT_PREFERENCES, autoSave: false };
	navigation.goto.mockReset();
	navigation.beforeNavigate.mockReset();
	await page.viewport(1100, 900);
});

describe('sheet editor with a fillable PDF', () => {
	it('shows the title and a way back to the sheets', async () => {
		openSheet(fillablePdfUrl());

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Scheda livello 5');
		await expect
			.element(page.getByRole('link', { name: 'Tutte le schede' }))
			.toHaveAttribute('href', '/characters/id-alira/sheets');
	});

	it('draws the fields of the PDF and keeps the save button off until something changes', async () => {
		openSheet(fillablePdfUrl());

		await fieldsDrawn();
		await expect.element(page.getByRole('status')).toHaveTextContent('Salvato');
		await expect.element(page.getByRole('button', { name: 'Salva la scheda' })).toBeDisabled();
	});

	it('enables the save button and warns when a field changes', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();

		await nameField().fill('Alira');

		await expect.element(page.getByRole('status')).toHaveTextContent('Non salvato');
		await expect.element(page.getByRole('button', { name: 'Salva la scheda' })).toBeEnabled();
	});

	it('saves the filled PDF over the old one, keeping the values inside the file', async () => {
		api.PUT.mockResolvedValue({ data: { id: 's-5', title: 'Scheda livello 5' } });
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');

		await page.getByRole('button', { name: 'Salva la scheda' }).click();

		await expect.element(page.getByText('Scheda salvata.')).toBeVisible();
		expect(api.PUT.mock.calls[0]?.[0]).toBe('/sheets/{id}/file');
		const options = api.PUT.mock.calls[0]?.[1] as {
			params: { path: { id: string } };
			bodySerializer: () => FormData;
		};
		expect(options.params.path.id).toBe('s-5');
		const saved = options.bodySerializer().get('file') as File;
		expect(saved.name).toBe('alira-lv5.pdf');
		expect(saved.type).toBe('application/pdf');

		const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs');
		pdfjs.GlobalWorkerOptions.workerSrc = (
			await import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url')
		).default;
		const reopened = await pdfjs.getDocument({ data: new Uint8Array(await saved.arrayBuffer()) })
			.promise;
		const fields = await reopened.getFieldObjects();
		expect(fields?.get('nome')).toContainEqual(expect.objectContaining({ value: 'Alira' }));
		await expect.element(page.getByRole('button', { name: 'Salva la scheda' })).toBeDisabled();
	});

	it('keeps the changes and says why when the server refuses the PDF', async () => {
		api.PUT.mockResolvedValue({ error: { title: 'File non valido', status: 400 } });
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');

		await page.getByRole('button', { name: 'Salva la scheda' }).click();

		await expect.element(page.getByText('File non valido')).toBeVisible();
		await expect.element(page.getByRole('status')).toHaveTextContent('Non salvato');
	});
});

interface Leaving {
	type: string;
	willUnload: boolean;
	to: { url: URL } | null;
	cancel: () => void;
}

// simula un clic su un link dell'app: il guardiano registrato con beforeNavigate decide
function leaveTo(path: string, overrides: Partial<Leaving> = {}): Leaving {
	const leaving: Leaving = {
		type: 'link',
		willUnload: false,
		to: { url: new URL(path, 'http://localhost') },
		cancel: vi.fn<() => void>(),
		...overrides
	};
	const guard = navigation.beforeNavigate.mock.calls.at(-1)?.[0] as (leaving: Leaving) => void;
	guard(leaving);
	return leaving;
}

describe('fields over the drawing', () => {
	it('lays the fields over a page exactly as big as the drawing', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();

		const canvas = required(
			document.querySelector('.pdfViewer .page canvas')
		).getBoundingClientRect();
		const layer = required(
			document.querySelector('.pdfViewer .annotationLayer')
		).getBoundingClientRect();

		expect(layer.width).toBeCloseTo(canvas.width, 0);
		expect(layer.height).toBeCloseTo(canvas.height, 0);
	});
});

describe('leaving the editor with unsaved changes', () => {
	it('lets you go when nothing was changed', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();

		const leaving = leaveTo('/characters/id-alira/sheets');

		expect(leaving.cancel).not.toHaveBeenCalled();
		expect(page.getByRole('dialog').elements()).toHaveLength(0);
	});

	it('asks with a dialog of the app, not with the alert of the browser', async () => {
		const alert = vi.spyOn(window, 'confirm');
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');

		const leaving = leaveTo('/characters/id-alira/sheets');

		expect(leaving.cancel).toHaveBeenCalledOnce();
		expect(alert).not.toHaveBeenCalled();
		await expect.element(page.getByRole('dialog', { name: 'Modifiche non salvate' })).toBeVisible();
		expect(navigation.goto).not.toHaveBeenCalled();
	});

	it('stays on the sheet when you choose to stay', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');
		leaveTo('/characters/id-alira/sheets');

		await page.getByRole('button', { name: 'Resta qui' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(page.getByRole('status')).toHaveTextContent('Non salvato');
		expect(navigation.goto).not.toHaveBeenCalled();
	});

	it('goes where you wanted when you leave without saving', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');
		leaveTo('/characters/id-alira/sheets');

		await page.getByRole('button', { name: 'Esci senza salvare' }).click();

		expect(navigation.goto).toHaveBeenCalledOnce();
		expect(String(navigation.goto.mock.calls[0]?.[0])).toBe(
			'http://localhost/characters/id-alira/sheets'
		);
		const again = leaveTo('/characters/id-alira/sheets');
		expect(again.cancel).not.toHaveBeenCalled();
	});

	it('leaves the closing of the tab to the browser', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');

		const leaving = leaveTo('/', { willUnload: true, to: null });

		expect(leaving.cancel).not.toHaveBeenCalled();
		expect(page.getByRole('dialog').elements()).toHaveLength(0);
	});
});

describe('autosave of the sheet', () => {
	// pausa brevissima: i valori delle impostazioni (da 5 a 30 secondi) li controlla `preferences`,
	// qui si prova solo che la scheda li usa
	beforeEach(() => {
		preferences.value = { ...DEFAULT_PREFERENCES, autoSave: true, autoSaveDelay: 0.3 };
		api.PUT.mockResolvedValue({ data: { id: 's-5', title: 'Scheda livello 5' } });
	});

	it('saves by itself after a pause and says so with the same popup as the button', async () => {
		openSheet(fillablePdfUrl());
		await fieldsDrawn();

		await nameField().fill('Alira');

		await expect.element(page.getByText('Scheda salvata.')).toBeVisible();
		expect(api.PUT).toHaveBeenCalledOnce();
		await expect.element(page.getByRole('button', { name: 'Salva la scheda' })).toBeDisabled();
	});

	it('shows what is happening like the diary: to save, saving, saved', async () => {
		preferences.value = { ...DEFAULT_PREFERENCES, autoSave: true, autoSaveDelay: 0.3 };
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await expect.element(page.getByRole('status')).toHaveTextContent('Salvato');

		await nameField().fill('Alira');

		await expect.element(page.getByRole('status')).toHaveTextContent('Da salvare');
		await expect.element(page.getByRole('status')).toHaveTextContent('Salvato');
	});

	it('says offline when the network is down and keeps the changes', async () => {
		api.PUT.mockRejectedValue(new TypeError('Failed to fetch'));
		openSheet(fillablePdfUrl());
		await fieldsDrawn();

		await nameField().fill('Alira');

		await expect.element(page.getByRole('status')).toHaveTextContent('Offline');
		await expect.element(page.getByRole('button', { name: 'Salva la scheda' })).toBeEnabled();
	});

	it('says not saved when the server refuses and keeps the changes', async () => {
		api.PUT.mockResolvedValue({ error: { title: 'File non valido', status: 400 } });
		openSheet(fillablePdfUrl());
		await fieldsDrawn();

		await nameField().fill('Alira');

		await expect.element(page.getByRole('status')).toHaveTextContent('Non salvato');
		await expect.element(page.getByText('File non valido')).toBeVisible();
	});

	it('saves on leaving the sheet and then goes where you wanted', async () => {
		preferences.value = { ...DEFAULT_PREFERENCES, autoSave: true, autoSaveDelay: 30 };
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');

		const leaving = leaveTo('/characters/id-alira/sheets');

		expect(leaving.cancel).toHaveBeenCalledOnce();
		await vi.waitFor(() => {
			expect(navigation.goto).toHaveBeenCalledOnce();
		});
		expect(api.PUT).toHaveBeenCalledOnce();
		expect(page.getByRole('dialog').elements()).toHaveLength(0);
	});

	it('asks with the dialog when the save on leaving fails', async () => {
		preferences.value = { ...DEFAULT_PREFERENCES, autoSave: true, autoSaveDelay: 30 };
		api.PUT.mockResolvedValue({ error: { title: 'File non valido', status: 400 } });
		openSheet(fillablePdfUrl());
		await fieldsDrawn();
		await nameField().fill('Alira');

		leaveTo('/characters/id-alira/sheets');

		await expect.element(page.getByRole('dialog', { name: 'Modifiche non salvate' })).toBeVisible();
		expect(navigation.goto).not.toHaveBeenCalled();
	});
});

describe('sheet editor with a PDF without fields', () => {
	it('says it can only be read and has no save button', async () => {
		openSheet(plainPdfUrl());

		await expect.element(page.getByText(/non ha campi da compilare/)).toBeVisible();
		expect(page.getByRole('button', { name: 'Salva la scheda' }).elements()).toHaveLength(0);
	});
});

describe('sheet editor when the PDF cannot be read', () => {
	it('shows the error and a link to open the PDF in a new tab', async () => {
		vi.spyOn(console, 'error').mockImplementation(() => undefined);
		openSheet('http://localhost:1/non-esiste.pdf');

		await expect.element(page.getByRole('alert')).toHaveTextContent('Non riesco a leggere il PDF.');
		const link = page.getByRole('link', { name: 'Aprila in una nuova scheda' });
		await expect.element(link).toHaveAttribute('href', 'http://localhost:1/non-esiste.pdf');
		await expect.element(link).toHaveAttribute('target', '_blank');
	});
});

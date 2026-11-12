import { page } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { FakeXhr } from '#lib/api/xhr-support.js';
import '../../../routes/layout.css';

type Call = (...args: unknown[]) => Promise<unknown>;

const { api } = vi.hoisted(() => ({
	api: {
		GET: vi.fn<Call>(),
		POST: vi.fn<Call>(),
		PUT: vi.fn<Call>(),
		DELETE: vi.fn<Call>()
	}
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
// le miniature dei PDF hanno i loro test (`SheetPile`): qui non si scarica nessun file
vi.mock('#lib/pdf/thumbnail.js', () => ({
	renderThumbnail: () => Promise.reject(new Error('niente miniature nei test della lista'))
}));

import SheetsScreen from './SheetsScreen.svelte';

const character = {
	id: 'id-alira',
	kind: 'PLAYER',
	name: 'Alira dei Salici',
	campaignName: 'Il Nord Spezzato',
	backgroundColor: '#3B2A6B',
	avatarUrl: null
};

const sheets = [
	{
		id: 's-5',
		characterId: 'id-alira',
		title: 'Scheda livello 5',
		system: 'D&D 5e',
		createdAt: '2026-03-14T10:00:00Z',
		url: 'https://files.test/s-5.pdf',
		downloadUrl: 'https://files.test/s-5.pdf?download=1'
	},
	{
		id: 's-3',
		characterId: 'id-alira',
		title: 'Scheda livello 3',
		system: 'Pathfinder 2e',
		createdAt: '2026-01-02T10:00:00Z',
		url: 'https://files.test/s-3.pdf',
		downloadUrl: 'https://files.test/s-3.pdf?download=1'
	},
	{
		id: 's-1',
		characterId: 'id-alira',
		title: 'Bozza',
		system: null,
		createdAt: '2025-12-01T10:00:00Z',
		url: 'https://files.test/s-1.pdf',
		downloadUrl: 'https://files.test/s-1.pdf?download=1'
	}
];

function listOf(items: unknown[], totalRows = items.length): { data: unknown } {
	return { data: { list: items, totalRows, totalPages: 1, page: 1, pageSize: 12 } };
}

function listCalls(): unknown[][] {
	return api.GET.mock.calls.filter((call) => call[0] === '/characters/{characterId}/sheets');
}

function lastQuery(): Record<string, unknown> {
	const options = listCalls().at(-1)?.[1] as { params: { query: Record<string, unknown> } };
	return options.params.query;
}

function pdf(name = 'scheda.pdf', type = 'application/pdf'): File {
	return new File(['%PDF-1.7'], name, { type });
}

async function ready(): Promise<void> {
	render(SheetsScreen, { character } as never);
	await expect.element(page.getByRole('heading', { name: 'Scheda livello 5' })).toBeVisible();
}

beforeEach(async () => {
	api.GET.mockReset();
	api.POST.mockReset();
	api.PUT.mockReset();
	api.DELETE.mockReset();
	api.GET.mockResolvedValue(listOf(sheets));
	await page.viewport(1280, 900);
});

afterEach(() => {
	vi.unstubAllEnvs();
	vi.unstubAllGlobals();
});

describe('sheets screen', () => {
	it('opens on the Schede tab with the character in the header and the count', async () => {
		await ready();

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Alira dei Salici');
		await expect
			.element(page.getByRole('link', { name: /Schede/ }))
			.toHaveAttribute('aria-current', 'page');
		await expect.element(page.getByText('3 schede').first()).toBeInTheDocument();
	});

	it('asks for the sheets of this character, most recent first', async () => {
		await ready();

		const options = listCalls()[0]?.[1] as { params: { path: { characterId: string } } };
		expect(options.params.path.characterId).toBe('id-alira');
		expect(lastQuery()).toMatchObject({
			sort: 'createdAt',
			descending: true,
			page: 1,
			size: 12
		});
	});

	it('shows system and date on every card, and no file size', async () => {
		await ready();

		const card = page.getByRole('listitem').filter({ hasText: 'Scheda livello 5' });
		await expect.element(card.getByText('D&D 5e', { exact: true })).toBeVisible();
		await expect.element(page.getByText('Caricata il 14 marzo 2026')).toBeVisible();
		expect(document.body.textContent).not.toMatch(/\b(KB|MB)\b/);
	});

	it('opens the sheet in the editor screen and offers a download', async () => {
		await ready();

		const open = page.getByRole('link', { name: 'Apri la scheda' }).first();
		await expect.element(open).toHaveAttribute('href', '/characters/id-alira/sheets/s-5');
		const download = page.getByRole('link', { name: 'Scarica Scheda livello 5' });
		await expect.element(download).toHaveAttribute('href', 'https://files.test/s-5.pdf?download=1');
		await expect.element(download).toHaveAttribute('download');
	});

	it('offers the system filter only when some sheet has a system', async () => {
		await ready();
		await expect.element(page.getByLabelText('Sistema di gioco')).toBeVisible();
	});

	it('hides the system filter when no sheet has a system', async () => {
		api.GET.mockResolvedValue(listOf([{ ...sheets[2] }]));
		render(SheetsScreen, { character } as never);
		await expect.element(page.getByRole('heading', { name: 'Bozza' })).toBeVisible();

		expect(page.getByLabelText('Sistema di gioco').elements()).toHaveLength(0);
	});

	it('reloads the list when the order is flipped', async () => {
		await ready();

		await page.getByRole('button', { name: /dalla più recente/ }).click();

		await expect.poll(() => lastQuery()['descending']).toBe(false);
		await expect.element(page.getByRole('button', { name: /dalla meno recente/ })).toBeVisible();
	});

	it('shows an error and no toolbar when the first load fails', async () => {
		api.GET.mockRejectedValue(new Error('Server giù'));
		render(SheetsScreen, { character } as never);

		await expect.element(page.getByText('Server giù')).toBeVisible();
		expect(page.getByRole('search').elements()).toHaveLength(0);
	});
});

describe('first sheet', () => {
	beforeEach(() => {
		api.GET.mockResolvedValue(listOf([]));
	});

	it('shows one big cell and hides the toolbar and the header button', async () => {
		render(SheetsScreen, { character } as never);

		await expect
			.element(page.getByRole('button', { name: 'Carica la prima scheda' }))
			.toBeVisible();
		await expect
			.element(page.getByText(/Alira dei Salici non ha ancora nessuna scheda/))
			.toBeVisible();
		expect(page.getByRole('search').elements()).toHaveLength(0);
		expect(page.getByRole('button', { name: 'Nuova scheda' }).elements()).toHaveLength(0);
	});

	it('opens the new sheet dialog from the cell', async () => {
		render(SheetsScreen, { character } as never);

		await page.getByRole('button', { name: 'Carica la prima scheda' }).click();

		await expect.element(page.getByRole('dialog', { name: 'Nuova scheda' })).toBeVisible();
	});
});

describe('new sheet dialog', () => {
	async function openDialog(): Promise<void> {
		await ready();
		await page.getByRole('button', { name: 'Nuova scheda' }).first().click();
		await expect.element(page.getByRole('dialog', { name: 'Nuova scheda' })).toBeVisible();
	}

	it('names the character and starts empty', async () => {
		await openDialog();

		await expect.element(page.getByText(/^Per Alira dei Salici\. Carica il PDF/)).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Titolo *' })).toHaveValue('');
		await expect.element(page.getByRole('textbox', { name: 'Sistema di gioco' })).toHaveValue('');
	});

	it('asks for the PDF and the title before sending', async () => {
		await openDialog();

		await page.getByRole('button', { name: 'Archivia la scheda' }).click();

		await expect.element(page.getByText('Il titolo è obbligatorio.')).toBeVisible();
		await expect.element(page.getByText('Scegli il PDF da caricare.')).toBeVisible();
		expect(api.POST).not.toHaveBeenCalled();
	});

	it('refuses a file that is not a PDF and sends nothing', async () => {
		await openDialog();

		await page.getByLabelText('File del PDF').upload(pdf('foto.png', 'image/png'));

		await expect.element(page.getByText(/non è un PDF/)).toBeVisible();
		expect(api.POST).not.toHaveBeenCalled();
	});

	it('refuses a PDF over the limit', async () => {
		await openDialog();
		const big = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'grande.pdf', {
			type: 'application/pdf'
		});

		await page.getByLabelText('File del PDF').upload(big);

		await expect.element(page.getByText(/pesa troppo/)).toBeVisible();
	});

	it('uploads the PDF, tells it and reloads the list', async () => {
		api.POST.mockResolvedValue({ data: { ...sheets[0], id: 's-new', title: 'Scheda nuova' } });
		await openDialog();

		await page.getByLabelText('File del PDF').upload(pdf('livello-6.pdf'));
		await expect.element(page.getByText('livello-6.pdf')).toBeVisible();
		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Scheda nuova');
		await page.getByRole('textbox', { name: 'Sistema di gioco' }).fill('D&D 5e');
		const reloads = listCalls().length;
		await page.getByRole('button', { name: 'Archivia la scheda' }).click();

		await expect.element(page.getByText('Scheda nuova è stata archiviata.')).toBeVisible();
		expect(api.POST.mock.calls[0]?.[0]).toBe('/characters/{characterId}/sheets');
		const options = api.POST.mock.calls[0]?.[1] as {
			params: { path: { characterId: string } };
			bodySerializer: () => FormData;
		};
		expect(options.params.path.characterId).toBe('id-alira');
		const form = options.bodySerializer();
		expect(form.get('title')).toBe('Scheda nuova');
		expect(form.get('system')).toBe('D&D 5e');
		expect((form.get('file') as File).name).toBe('livello-6.pdf');
		await expect.poll(() => listCalls().length).toBeGreaterThan(reloads);
		expect(page.getByRole('dialog').elements()).toHaveLength(0);
	});

	it('keeps the dialog open and shows the message of the API when the upload fails', async () => {
		api.POST.mockResolvedValue({ error: { title: 'File non valido', status: 400 } });
		await openDialog();

		await page.getByLabelText('File del PDF').upload(pdf());
		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Scheda nuova');
		await page.getByRole('button', { name: 'Archivia la scheda' }).click();

		await expect
			.element(page.getByRole('alert').filter({ hasText: 'File non valido' }))
			.toBeVisible();
		await expect.element(page.getByRole('dialog', { name: 'Nuova scheda' })).toBeVisible();
	});
});

describe('sheets limit', () => {
	it('offers the new sheet button while the character is under the limit', async () => {
		await ready();

		await expect.element(page.getByRole('button', { name: 'Nuova scheda' }).first()).toBeVisible();
		expect(page.getByText('Hai raggiunto il limite').elements()).toHaveLength(0);
	});

	it('takes the limit from the environment and says so when it is reached', async () => {
		vi.stubEnv('VITE_MAX_SHEETS_PER_CHARACTER', '3');
		await ready();

		await expect.element(page.getByText('Hai raggiunto il limite')).toBeVisible();
		await expect.element(page.getByText(/al massimo 3 schede/)).toBeVisible();
		expect(page.getByRole('button', { name: 'Nuova scheda' }).elements()).toHaveLength(0);
	});
});

describe('upload progress', () => {
	it('shows how much of the PDF has been sent, then that the sheet is being archived', async () => {
		vi.stubGlobal('XMLHttpRequest', FakeXhr);
		api.POST.mockImplementation(async (_path: unknown, options: unknown) => {
			const send = (options as { fetch: (request: Request) => Promise<Response> }).fetch;
			const response = await send(
				new Request('http://api.test/sheets', { method: 'POST', body: 'pdf' })
			);
			const data = (await response.json()) as unknown;
			return { data };
		});
		await ready();
		await page.getByRole('button', { name: 'Nuova scheda' }).first().click();
		await page.getByLabelText('File del PDF').upload(pdf());
		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Scheda nuova');
		const sent = FakeXhr.nextSent();

		await page.getByRole('button', { name: 'Archivia la scheda' }).click();
		const xhr = await sent;
		xhr.progress(1, 2);

		const bar = page.getByRole('progressbar');
		await expect.element(bar).toHaveAttribute('aria-valuenow', '50');
		await expect.element(bar).toHaveAccessibleName('Carico il PDF… 50%');
		xhr.progress(2, 2);
		await expect.element(page.getByRole('progressbar')).toHaveAccessibleName('Archivio la scheda…');
		xhr.finish(201, JSON.stringify({ ...sheets[0], title: 'Scheda nuova' }));
		await expect.element(page.getByText('Scheda nuova è stata archiviata.')).toBeVisible();
		expect(page.getByRole('progressbar').elements()).toHaveLength(0);
	});

	it('shows no bar when only the fields are saved', async () => {
		api.PUT.mockResolvedValue({ data: sheets[0] });
		await ready();
		await page.getByRole('button', { name: 'Modifica Scheda livello 5' }).click();

		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByText('Scheda livello 5 è stata aggiornata.')).toBeVisible();
		expect(page.getByRole('progressbar').elements()).toHaveLength(0);
	});
});

describe('edit sheet', () => {
	async function openEditor(): Promise<void> {
		await ready();
		await page.getByRole('button', { name: 'Modifica Scheda livello 5' }).click();
		await expect.element(page.getByRole('dialog', { name: 'Modifica scheda' })).toBeVisible();
	}

	it('starts from the current values and does not ask for a new PDF', async () => {
		await openEditor();

		await expect
			.element(page.getByRole('textbox', { name: 'Titolo *' }))
			.toHaveValue('Scheda livello 5');
		await expect
			.element(page.getByRole('textbox', { name: 'Sistema di gioco' }))
			.toHaveValue('D&D 5e');
		await expect.element(page.getByText('PDF attuale')).toBeVisible();
	});

	it('has no delete button, which lives on the card', async () => {
		await openEditor();

		expect(
			page
				.getByRole('dialog')
				.getByRole('button', { name: /Elimina/ })
				.elements()
		).toHaveLength(0);
	});

	it('saves the fields without replacing the PDF', async () => {
		api.PUT.mockResolvedValue({ data: { ...sheets[0], title: 'Scheda livello 6' } });
		await openEditor();

		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Scheda livello 6');
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByText('Scheda livello 6 è stata aggiornata.')).toBeVisible();
		expect(api.PUT).toHaveBeenCalledTimes(1);
		expect(api.PUT.mock.calls[0]?.[0]).toBe('/sheets/{id}');
		expect((api.PUT.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			title: 'Scheda livello 6',
			system: 'D&D 5e'
		});
	});

	it('says so when the fields are saved but the new PDF is refused', async () => {
		api.PUT.mockImplementation((path: unknown) =>
			Promise.resolve(
				path === '/sheets/{id}/file'
					? { error: { title: 'PDF corrotto', status: 400 } }
					: { data: sheets[0] }
			)
		);
		await openEditor();

		await page.getByLabelText('File del PDF').upload(pdf('nuova.pdf'));
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect
			.element(
				page.getByText('Scheda livello 5 è stata salvata, ma il PDF non è cambiato: PDF corrotto')
			)
			.toBeVisible();
	});
});

describe('delete sheet', () => {
	async function askToDelete(): Promise<void> {
		await ready();
		await page.getByRole('button', { name: 'Elimina Scheda livello 5' }).click();
		await expect
			.element(page.getByRole('heading', { name: 'Eliminare Scheda livello 5?' }))
			.toBeVisible();
	}

	it('asks for confirmation and deletes nothing when cancelled', async () => {
		await askToDelete();

		await page.getByRole('button', { name: 'Annulla' }).click();

		expect(api.DELETE).not.toHaveBeenCalled();
		expect(page.getByRole('dialog').elements()).toHaveLength(0);
	});

	it('deletes the sheet, tells it and reloads the list', async () => {
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await askToDelete();
		const reloads = listCalls().length;

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByText('Scheda livello 5 è stata eliminata.')).toBeVisible();
		expect(api.DELETE.mock.calls[0]?.[0]).toBe('/sheets/{id}');
		expect(
			(api.DELETE.mock.calls[0]?.[1] as { params: { path: { id: string } } }).params.path.id
		).toBe('s-5');
		await expect.poll(() => listCalls().length).toBeGreaterThan(reloads);
	});

	it('keeps the confirmation open and shows why when the API refuses', async () => {
		api.DELETE.mockResolvedValue({
			response: { ok: false, status: 500 },
			error: { title: 'Non posso eliminarla ora', status: 500 }
		});
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByText('Non posso eliminarla ora')).toBeVisible();
	});
});

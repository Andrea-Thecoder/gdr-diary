import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../layout.css';

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
vi.mock('$app/state', () => ({ page: { url: { pathname: '/characters/id-alira' } } }));

import { settle } from '#lib/test-support.js';
import { chooseAndCrop, samplePng } from '#lib/ui/test-image.js';
import Page from './+page.svelte';

const character = {
	id: 'id-alira',
	kind: 'PLAYER',
	name: 'Alira dei Salici',
	campaignName: 'Il Nord Spezzato',
	backgroundColor: '#3B2A6B',
	avatarUrl: null
};

const diaries = [
	{
		id: 'd-cronache',
		characterId: 'id-alira',
		title: 'Cronache del Nord',
		coverColor: '#2F4B43',
		coverUrl: null
	},
	{
		id: 'd-valkar',
		characterId: 'id-alira',
		title: 'La torre di Valkar',
		coverColor: '#7C2A22',
		coverUrl: null
	},
	{
		id: 'd-ceneri',
		characterId: 'id-alira',
		title: 'Ceneri',
		coverColor: null,
		coverUrl: 'http://img.test/c.png'
	}
];

const detail = {
	...diaries[0],
	description: 'Le prime avventure',
	font: 'Caveat',
	fontSize: 18,
	coverDocumentId: null
};

function listOf(items: unknown[], totalRows = items.length, totalPages = 1): { data: unknown } {
	return { data: { list: items, totalRows, totalPages, page: 1, pageSize: 12 } };
}

function listCalls(): unknown[][] {
	return api.GET.mock.calls.filter((call) => call[0] === '/characters/{characterId}/diaries');
}

function lastQuery(): Record<string, unknown> {
	const options = listCalls().at(-1)?.[1] as { params: { query: Record<string, unknown> } };
	return options.params.query;
}

function renderPage(shown: Record<string, unknown> = character): void {
	render(Page, { data: { character: shown }, params: { characterId: 'id-alira' } } as never);
}

async function ready(): Promise<void> {
	renderPage();
	await expect.element(page.getByRole('heading', { name: 'Cronache del Nord' })).toBeVisible();
}

beforeEach(async () => {
	api.GET.mockReset();
	api.POST.mockReset();
	api.PUT.mockReset();
	api.DELETE.mockReset();
	api.GET.mockImplementation((path: unknown) =>
		Promise.resolve(path === '/diaries/{id}' ? { data: detail } : listOf(diaries))
	);
	await page.viewport(1280, 900);
});

describe('diaries page', () => {
	it('calls a game master by its name in the header', async () => {
		renderPage({ ...character, kind: 'GAME_MASTER' });

		await expect.element(page.getByText('Il master', { exact: true })).toBeVisible();
	});

	it('shows the character in the header', async () => {
		await ready();

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Alira dei Salici');
		await expect.element(page.getByText('Il personaggio', { exact: true })).toBeVisible();
		await expect.element(page.getByText('Il Nord Spezzato').first()).toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'Tutti i personaggi' }))
			.toHaveAttribute('href', '/characters');
	});

	it('shows the description of the character under the header', async () => {
		renderPage({ ...character, description: 'Elfa del nord, esule dei Salici.' });

		await expect.element(page.getByText('Elfa del nord, esule dei Salici.')).toBeVisible();
		expect(document.body.textContent).not.toContain('Ogni diario ha le sue pagine');
	});

	it('shows the lead text when the description is empty or only spaces', async () => {
		renderPage({ ...character, description: '   ' });
		await expect.element(page.getByRole('heading', { name: 'Cronache del Nord' })).toBeVisible();

		expect(document.querySelector('[data-character-description]')).toBeNull();
		await expect.element(page.getByText(/Ogni diario ha le sue pagine/)).toBeVisible();
	});

	it('asks for the diaries of this character sorted by title', async () => {
		await ready();

		const options = listCalls()[0]?.[1] as {
			params: { path: { characterId: string }; query: Record<string, unknown> };
		};
		expect(options.params.path.characterId).toBe('id-alira');
		expect(options.params.query).toMatchObject({ sort: 'title', page: 1, size: 12 });
	});

	it('shows a card per diary with a closed book, a link and the count', async () => {
		await ready();

		await expect.element(page.getByRole('heading', { name: 'La torre di Valkar' })).toBeVisible();
		await expect
			.element(page.getByRole('img', { name: 'Copertina di Cronache del Nord: diario chiuso' }))
			.toBeVisible();
		await expect.element(page.getByRole('status').getByText('3 diari')).toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'Apri il diario' }).first())
			.toHaveAttribute('href', '/diaries/d-cronache');
		await expect.element(page.getByRole('button', { name: 'Nuovo diario' }).first()).toBeVisible();
		await expect
			.element(page.getByText('Un titolo e una copertina: le pagine vengono dopo.'))
			.toBeVisible();
	});

	it('draws the uploaded cover instead of the title', async () => {
		await ready();

		const book = page.getByRole('img', { name: 'Copertina di Ceneri: diario chiuso' }).element();

		expect(book.querySelector('img')?.getAttribute('src')).toBe('http://img.test/c.png');
	});

	it('opens the diary from anywhere on the card, except the edit button', async () => {
		await ready();
		const link = page.getByRole('link', { name: 'Apri il diario' }).first().element();
		const box = (link.closest('article') ?? link).getBoundingClientRect();
		const edit = page.getByRole('button', { name: 'Modifica Cronache del Nord' }).element();
		const editBox = edit.getBoundingClientRect();

		const middle = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
		const onEdit = document.elementFromPoint(
			editBox.left + editBox.width / 2,
			editBox.top + editBox.height / 2
		);

		expect(middle).toBe(link);
		expect(onEdit?.closest('button')).toBe(edit);
	});

	it('does not search while typing', async () => {
		await ready();
		const calls = listCalls().length;

		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill('torre');
		await settle();

		expect(listCalls().length).toBe(calls);
	});

	it('limits the length of the title search to 255 characters', async () => {
		await ready();

		await expect
			.element(page.getByRole('searchbox', { name: 'Cerca per titolo' }))
			.toHaveAttribute('maxlength', '255');
	});

	it('applies the title filter with the button and with Enter', async () => {
		await ready();

		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill(' torre ');
		await page.getByRole('button', { name: 'Applica filtri' }).click();
		await expect.poll(() => lastQuery()['title']).toBe('torre');

		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill('ceneri');
		await userEvent.keyboard('{Enter}');
		await expect.poll(() => lastQuery()['title']).toBe('ceneri');
	});

	it('has a tag search next to the title search, limited to 30 characters', async () => {
		await ready();

		const tag = page.getByRole('searchbox', { name: 'Cerca per tag' });
		await expect.element(tag).toHaveAttribute('maxlength', '30');
		const title = page.getByRole('searchbox', { name: 'Cerca per titolo' }).element();
		expect(title.getBoundingClientRect().left).toBeLessThan(
			tag.element().getBoundingClientRect().left
		);
	});

	it('sends the tag together with the title when the filters are applied', async () => {
		await ready();

		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill('torre');
		await page.getByRole('searchbox', { name: 'Cerca per tag' }).fill(' erebo ');
		await page.getByRole('button', { name: 'Applica filtri' }).click();

		await expect.poll(() => lastQuery()['tag']).toBe('erebo');
		expect(lastQuery()['title']).toBe('torre');
	});

	it('sends the tag by itself after a pause in the typing, without the title that was not applied', async () => {
		await ready();

		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill('torre');
		await page.getByRole('searchbox', { name: 'Cerca per tag' }).fill('erebo');

		await expect.poll(() => lastQuery()['tag']).toBe('erebo');
		expect(lastQuery()['title']).toBeUndefined();
	});

	it('does not send a tag that is empty or only spaces', async () => {
		await ready();

		await page.getByRole('searchbox', { name: 'Cerca per tag' }).fill('   ');
		await page.getByRole('button', { name: 'Applica filtri' }).click();

		expect(lastQuery()['tag']).toBeUndefined();
	});

	it('clears the tag with the title', async () => {
		await ready();
		await page.getByRole('searchbox', { name: 'Cerca per tag' }).fill('erebo');
		await page.getByRole('button', { name: 'Applica filtri' }).click();
		await expect.poll(() => lastQuery()['tag']).toBe('erebo');

		await page.getByRole('button', { name: 'Azzera', exact: true }).click();

		await expect.poll(() => lastQuery()['tag']).toBeUndefined();
		await expect.element(page.getByRole('searchbox', { name: 'Cerca per tag' })).toHaveValue('');
	});

	it('orders by title and clears the filter and the field', async () => {
		await ready();

		await page.getByRole('button', { name: /Ordina per titolo/ }).click();
		await expect.poll(() => lastQuery()['descending']).toBe(true);
		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill('torre');
		await page.getByRole('button', { name: 'Applica filtri' }).click();
		await expect.poll(() => lastQuery()['title']).toBe('torre');

		await page.getByRole('button', { name: 'Azzera', exact: true }).click();

		await expect.poll(() => lastQuery()['title']).toBeUndefined();
		await expect.element(page.getByRole('searchbox', { name: 'Cerca per titolo' })).toHaveValue('');
	});

	it('puts the apply button after the order button', async () => {
		await ready();

		const order = page.getByRole('button', { name: /Ordina per titolo/ }).element();
		const apply = page.getByRole('button', { name: 'Applica filtri' }).element();

		expect(order.getBoundingClientRect().left).toBeLessThan(apply.getBoundingClientRect().left);
	});

	it('offers to clear the filters when nothing matches', async () => {
		await ready();
		api.GET.mockResolvedValue(listOf([]));

		await page.getByRole('searchbox', { name: 'Cerca per titolo' }).fill('zzz');
		await page.getByRole('button', { name: 'Applica filtri' }).click();

		await expect.element(page.getByText('Nessun diario corrisponde alla ricerca.')).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Azzera i filtri' })).toBeVisible();
	});

	it('loads more diaries on request', async () => {
		api.GET.mockResolvedValue(listOf(diaries.slice(0, 2), 3, 2));
		renderPage();
		await expect.element(page.getByRole('heading', { name: 'Cronache del Nord' })).toBeVisible();
		api.GET.mockResolvedValue({
			data: { list: [diaries[2]], totalRows: 3, totalPages: 2, page: 2, pageSize: 12 }
		});

		await page.getByRole('button', { name: 'Mostra altri diari' }).click();

		await expect.element(page.getByRole('heading', { name: 'Ceneri' })).toBeVisible();
		expect(lastQuery()['page']).toBe(2);
	});

	it('shows the error with a retry button', async () => {
		api.GET.mockResolvedValue({ error: { title: 'Non disponibile', status: 500 } });
		renderPage();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Non disponibile');
		api.GET.mockResolvedValue(listOf(diaries));
		await page.getByRole('button', { name: 'Riprova' }).click();

		await expect.element(page.getByRole('heading', { name: 'Cronache del Nord' })).toBeVisible();
	});
});

describe('first diary', () => {
	beforeEach(() => {
		api.GET.mockResolvedValue(listOf([]));
	});

	it('shows one big cell and hides the toolbar and the header button', async () => {
		renderPage();

		await expect
			.element(page.getByRole('button', { name: 'Rilega il primo diario' }))
			.toBeVisible();
		await expect
			.element(
				page.getByText(
					'Alira dei Salici non ha ancora nessun diario. Rilegane uno e comincia dalla prima pagina.'
				)
			)
			.toBeVisible();
		expect(page.getByRole('search').elements()).toHaveLength(0);
		expect(page.getByRole('button', { name: 'Nuovo diario' }).elements()).toHaveLength(0);
	});

	it('opens the new diary dialog from the cell', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Rilega il primo diario' }).click();

		await expect.element(page.getByRole('dialog', { name: 'Nuovo diario' })).toBeVisible();
	});
});

describe('new diary dialog', () => {
	async function openDialog(): Promise<void> {
		await ready();
		await page.getByRole('button', { name: 'Nuovo diario' }).first().click();
		await expect.element(page.getByRole('dialog', { name: 'Nuovo diario' })).toBeVisible();
	}

	it('names the character and starts empty', async () => {
		await openDialog();

		await expect.element(page.getByText(/^Per Alira dei Salici\. Basta un titolo/)).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Titolo *' })).toHaveValue('');
		await expect.element(page.getByLabelText('Font')).toHaveValue('');
		await expect.element(page.getByLabelText('Dimensione')).toHaveValue('');
	});

	it('asks for the title before sending', async () => {
		await openDialog();

		await page.getByRole('button', { name: 'Rilega il diario' }).click();

		await expect.element(page.getByText('Il titolo è obbligatorio.')).toBeVisible();
		expect(api.POST).not.toHaveBeenCalled();
	});

	it('offers the preset colors only, with no free HEX field', async () => {
		await openDialog();

		await expect
			.element(page.getByRole('textbox', { name: 'Colore in formato HEX' }))
			.not.toBeInTheDocument();
		await expect
			.element(page.getByRole('button', { name: 'Viola #3B2A6B' }))
			.toHaveAttribute('aria-pressed', 'true');
	});

	it('creates the diary, closes, tells it and reloads the list', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', title: 'Il Nord' } });
		await openDialog();
		const reloads = listCalls().length;

		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Il Nord');
		await page.getByRole('button', { name: 'Verde #2F4B43' }).click();
		await page.getByRole('button', { name: 'Rilega il diario' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(page.getByText('Il Nord è pronto per le sue pagine.')).toBeVisible();
		expect(api.POST.mock.calls[0]?.[0]).toBe('/characters/{characterId}/diaries');
		expect(api.POST.mock.calls[0]?.[1]).toMatchObject({
			params: { path: { characterId: 'id-alira' } },
			body: { title: 'Il Nord', coverColor: '#2F4B43' }
		});
		await expect.poll(() => listCalls().length).toBeGreaterThan(reloads);
	});

	it('sends the chosen font and size', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', title: 'Il Nord' } });
		await openDialog();

		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Il Nord');
		await page.getByLabelText('Font').selectOptions('Caveat');
		await page.getByLabelText('Dimensione').selectOptions('18');
		await page.getByRole('button', { name: 'Rilega il diario' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		const body = (api.POST.mock.calls[0]?.[1] as { body: Record<string, unknown> }).body;
		expect(body).toMatchObject({ font: 'Caveat', fontSize: 18 });
	});

	it('uploads the chosen cover after creating the diary', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', title: 'Il Nord' } });
		api.PUT.mockResolvedValue({ response: { ok: true } });
		await openDialog();

		await chooseAndCrop(await samplePng('c.png'));
		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Il Nord');
		await page.getByRole('button', { name: 'Rilega il diario' }).click();

		await expect.element(page.getByText('Il Nord è pronto per le sue pagine.')).toBeVisible();
		expect(api.PUT.mock.calls[0]?.[0]).toBe('/diaries/{id}/cover');
	});

	it('keeps the diary and reports a failed cover upload', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', title: 'Il Nord' } });
		api.PUT.mockResolvedValue({
			error: { title: 'Troppo grande', status: 413 },
			response: { ok: false }
		});
		await openDialog();

		await chooseAndCrop(await samplePng('c.png'));
		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Il Nord');
		await page.getByRole('button', { name: 'Rilega il diario' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Il Nord è stato creato, ma la copertina non è stata caricata');
	});

	it('refuses an unsupported file', async () => {
		await openDialog();

		await page
			.getByLabelText('File dell’immagine')
			.upload(new File(['x'], 'c.gif', { type: 'image/gif' }));

		await expect
			.element(page.getByText('Formato non supportato: usa un file PNG, JPEG o WebP.'))
			.toBeVisible();
	});

	it('follows the title and the color in the preview', async () => {
		await openDialog();

		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Cronache nuove');

		await expect
			.element(page.getByRole('img', { name: 'Copertina di Cronache nuove: diario chiuso' }))
			.toBeVisible();
	});

	describe('while the diary is being created', () => {
		let finish: (value: unknown) => void = () => undefined;

		async function submitPending(): Promise<void> {
			api.POST.mockImplementation(
				() =>
					new Promise((resolve) => {
						finish = resolve;
					})
			);
			await openDialog();
			await page.getByRole('textbox', { name: 'Titolo *' }).fill('Il Nord');
			await page.getByRole('button', { name: 'Rilega il diario' }).click();
			await vi.waitFor(() => {
				expect(api.POST).toHaveBeenCalledTimes(1);
			});
		}

		it('stays open when Escape is pressed', async () => {
			await submitPending();

			await userEvent.keyboard('{Escape}');

			await expect.element(page.getByRole('dialog', { name: 'Nuovo diario' })).toBeVisible();
		});

		it('stays open when the backdrop is pressed and released', async () => {
			await submitPending();
			const dialog = page.getByRole('dialog').element();

			dialog.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
			dialog.dispatchEvent(new MouseEvent('click', { bubbles: true }));

			await expect.element(page.getByRole('dialog', { name: 'Nuovo diario' })).toBeVisible();
		});

		it('stays open when the close button of the header is pressed', async () => {
			await submitPending();

			await page.getByRole('button', { name: 'Chiudi' }).click({ force: true });

			await expect.element(page.getByRole('dialog', { name: 'Nuovo diario' })).toBeVisible();
		});

		it('closes as usual after the request has finished', async () => {
			await submitPending();
			await userEvent.keyboard('{Escape}');

			finish({ data: { id: 'nuovo', title: 'Il Nord' } });

			await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		});
	});

	it('keeps the dialog open and shows the server error', async () => {
		api.POST.mockResolvedValue({
			error: { title: 'Bad', status: 400, violations: [{ message: 'titolo già usato' }] }
		});
		await openDialog();

		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Il Nord');
		await page.getByRole('button', { name: 'Rilega il diario' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('titolo già usato');
		await expect.element(page.getByRole('dialog')).toBeVisible();
	});
});

describe('edit diary', () => {
	async function openEditor(): Promise<void> {
		await ready();
		await page.getByRole('button', { name: 'Modifica Cronache del Nord' }).click();
		await expect.element(page.getByRole('dialog', { name: 'Modifica diario' })).toBeVisible();
	}

	it('opens the editor with the saved data, loaded from the diary', async () => {
		await openEditor();

		const call = api.GET.mock.calls.find((entry) => entry[0] === '/diaries/{id}');
		expect((call?.[1] as { params: { path: { id: string } } }).params.path.id).toBe('d-cronache');
		await expect
			.element(page.getByRole('textbox', { name: 'Titolo *' }))
			.toHaveValue('Cronache del Nord');
		await expect
			.element(page.getByRole('textbox', { name: 'Descrizione' }))
			.toHaveValue('Le prime avventure');
		await expect.element(page.getByLabelText('Font')).toHaveValue('Caveat');
		await expect.element(page.getByLabelText('Dimensione')).toHaveValue('18');
	});

	it('saves the changes, closes and reloads the list', async () => {
		api.PUT.mockResolvedValue({ data: { ...detail, title: 'Cronache nuove' } });
		await openEditor();
		const reloads = listCalls().length;

		await page.getByRole('textbox', { name: 'Titolo *' }).fill('Cronache nuove');
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(page.getByText('Cronache nuove è stato aggiornato.')).toBeVisible();
		expect(api.PUT.mock.calls[0]?.[0]).toBe('/diaries/{id}');
		expect((api.PUT.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			title: 'Cronache nuove',
			coverColor: '#2F4B43',
			description: 'Le prime avventure',
			font: 'Caveat',
			fontSize: 18
		});
		await expect.poll(() => listCalls().length).toBeGreaterThan(reloads);
	});

	it('uploads the new cover chosen in the editor', async () => {
		api.PUT.mockResolvedValue({ data: detail, response: { ok: true } });
		await openEditor();

		await chooseAndCrop(await samplePng('copertina.png'));
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByText('Cronache del Nord è stato aggiornato.')).toBeVisible();
		expect(api.PUT.mock.calls.map((call) => call[0])).toContain('/diaries/{id}/cover');
	});

	it('removes the current cover when asked', async () => {
		api.GET.mockImplementation((path: unknown) =>
			Promise.resolve(
				path === '/diaries/{id}'
					? { data: { ...detail, coverUrl: 'http://img.test/c.png' } }
					: listOf(diaries)
			)
		);
		api.PUT.mockResolvedValue({ data: detail });
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await openEditor();

		await page.getByRole('button', { name: 'Rimuovi' }).click();
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByText('Cronache del Nord è stato aggiornato.')).toBeVisible();
		expect(api.DELETE.mock.calls[0]?.[0]).toBe('/diaries/{id}/cover');
	});

	it('shows the error and stays on the list when the diary cannot be loaded', async () => {
		api.GET.mockImplementation((path: unknown) =>
			Promise.resolve(
				path === '/diaries/{id}'
					? { error: { title: 'Non trovato', status: 404 } }
					: listOf(diaries)
			)
		);
		await ready();

		await page.getByRole('button', { name: 'Modifica Cronache del Nord' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Non trovato');
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});
});

describe('delete diary', () => {
	async function askToDelete(): Promise<void> {
		await ready();
		await page.getByRole('button', { name: 'Elimina Cronache del Nord' }).click();
	}

	it('puts the trash button beside the link that opens the diary', async () => {
		await ready();

		const link = page
			.getByRole('link', { name: /Apri il diario/ })
			.first()
			.element();
		const trash = page.getByRole('button', { name: 'Elimina Cronache del Nord' }).element();
		const linkBox = link.getBoundingClientRect();
		const trashBox = trash.getBoundingClientRect();

		expect(trashBox.left).toBeGreaterThanOrEqual(linkBox.right);
		expect(Math.abs(trashBox.top - linkBox.top)).toBeLessThan(2);
	});

	it('warns about what is lost before doing anything', async () => {
		await askToDelete();

		await expect
			.element(page.getByRole('heading', { name: 'Eliminare Cronache del Nord?' }))
			.toBeVisible();
		await expect
			.element(page.getByText('tutte le sue pagine, con il testo scritto.'))
			.toBeVisible();
		await expect
			.element(page.getByText('Non si può annullare e non c’è modo di recuperare i dati.'))
			.toBeVisible();
		expect(api.DELETE).not.toHaveBeenCalled();
	});

	it('keeps the diary when cancelled', async () => {
		await askToDelete();

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		expect(api.DELETE).not.toHaveBeenCalled();
	});

	it('deletes the diary, tells it and reloads the list', async () => {
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await askToDelete();
		const reloads = listCalls().length;

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByText('Cronache del Nord è stato eliminato.')).toBeVisible();
		expect(api.DELETE.mock.calls[0]?.[0]).toBe('/diaries/{id}');
		expect(
			(api.DELETE.mock.calls[0]?.[1] as { params: { path: { id: string } } }).params.path.id
		).toBe('d-cronache');
		await expect.poll(() => listCalls().length).toBeGreaterThan(reloads);
	});

	function serveAfterDeletion(before: unknown[], after: unknown[]): void {
		let shown = before;
		api.GET.mockImplementation((path: unknown) =>
			Promise.resolve(path === '/diaries/{id}' ? { data: detail } : listOf(shown))
		);
		api.DELETE.mockImplementation(() => {
			shown = after;
			return Promise.resolve({ response: { ok: true } });
		});
	}

	it('shows the first-diary state after the last diary is deleted', async () => {
		serveAfterDeletion([diaries[0]], []);
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect
			.element(page.getByRole('button', { name: 'Rilega il primo diario' }))
			.toBeVisible();
		expect(page.getByRole('search').elements()).toHaveLength(0);
	});

	it('keeps the other cards after a deletion', async () => {
		serveAfterDeletion(diaries, diaries.slice(1));
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('heading', { name: 'La torre di Valkar' })).toBeVisible();
		expect(page.getByRole('heading', { name: 'Cronache del Nord' }).elements()).toHaveLength(0);
	});

	it('stays open with the problem when the server refuses', async () => {
		api.DELETE.mockResolvedValue({
			error: { title: 'Errore', status: 500 },
			response: { ok: false, status: 500 }
		});
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('alert')).toBeVisible();
		await expect.element(page.getByRole('dialog')).toBeVisible();
	});

	it('counts an already deleted diary as deleted', async () => {
		api.DELETE.mockResolvedValue({
			error: { title: 'Diary not found', status: 404 },
			response: { ok: false, status: 404 }
		});
		await askToDelete();
		const reloads = listCalls().length;

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByText('Cronache del Nord è stato eliminato.')).toBeVisible();
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.poll(() => listCalls().length).toBeGreaterThan(reloads);
	});

	it('explains a change during the deletion in Italian and lets the user retry', async () => {
		api.DELETE.mockResolvedValueOnce({
			error: {
				title: 'Conflict',
				status: 409,
				violations: [{ message: 'The diary changed while it was being deleted, try again' }]
			},
			response: { ok: false, status: 409 }
		});
		api.DELETE.mockResolvedValueOnce({ response: { ok: true, status: 204 } });
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Il diario è cambiato mentre lo eliminavi: riprova');
		await page.getByRole('button', { name: 'Elimina per sempre' }).click();
		await expect.element(page.getByText('Cronache del Nord è stato eliminato.')).toBeVisible();
	});
});

describe('layout', () => {
	const widths = [280, 320, 360, 390, 412, 600, 768, 1024, 1280, 1920];

	function expectNoHorizontalScroll(): void {
		expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
	}

	for (const width of widths) {
		it(`fits a ${String(width)} px wide screen`, async () => {
			await page.viewport(width, 800);
			await ready();

			expectNoHorizontalScroll();
		});

		it(`fits the open dialog on ${String(width)} px`, async () => {
			await page.viewport(width, 800);
			await ready();
			await page.getByRole('button', { name: 'Nuovo diario' }).first().click();
			const dialog = page.getByRole('dialog', { name: 'Nuovo diario' });
			await expect.element(dialog).toBeVisible();

			const box = dialog.element().getBoundingClientRect();
			expect(box.left).toBeGreaterThanOrEqual(0);
			expect(box.right).toBeLessThanOrEqual(width);
			expectNoHorizontalScroll();
		});
	}

	it('keeps every control at least 44 px tall on a phone', async () => {
		await page.viewport(360, 800);
		await ready();

		const controls = [
			...page.getByRole('button').elements(),
			...page.getByRole('link').elements(),
			...page.getByRole('searchbox').elements()
		];
		const heights = controls.map((control) => control.getBoundingClientRect().height);

		expect(Math.min(...heights)).toBeGreaterThanOrEqual(43.5);
	});
});

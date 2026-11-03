import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../layout.css';

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

import { areaChoice } from '#lib/characters/area.svelte.js';
import { settle } from '#lib/test-support.js';
import { chooseAndCrop, samplePng } from '#lib/ui/test-image.js';
import Page from './+page.svelte';

const characters = [
	{
		id: 'id-alira',
		kind: 'PLAYER',
		name: 'Alira dei Salici',
		campaignName: 'Il Nord Spezzato',
		backgroundColor: '#3B2A6B',
		avatarUrl: null
	},
	{
		id: 'id-brann',
		kind: 'PLAYER',
		name: 'Brann Pietrafranta',
		campaignName: 'Le Ceneri di Valkar',
		backgroundColor: '#2F4B43',
		avatarUrl: null
	},
	{
		id: 'id-orlan',
		kind: 'GAME_MASTER',
		name: 'Orlan Bastacorta',
		campaignName: null,
		backgroundColor: '#3C3F6B',
		avatarUrl: null
	}
];

function listOf(items: unknown[], totalRows = items.length, totalPages = 1): { data: unknown } {
	return { data: { list: items, totalRows, totalPages, page: 1, pageSize: 12 } };
}

function lastQuery(): Record<string, unknown> {
	const call = api.GET.mock.calls.at(-1);
	const options = call?.[1] as { params: { query: Record<string, unknown> } };
	return options.params.query;
}

beforeEach(async () => {
	areaChoice.select('characters');
	api.GET.mockReset();
	api.POST.mockReset();
	api.PUT.mockReset();
	api.DELETE.mockReset();
	api.GET.mockResolvedValue(listOf(characters));
	await page.viewport(1280, 900);
});

describe('characters page', () => {
	it('lists the characters with their campaign', async () => {
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Chi scrive oggi?');
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await expect.element(page.getByText('Il Nord Spezzato')).toBeVisible();
		await expect.element(page.getByText('Senza campagna')).toBeVisible();
		await expect.element(page.getByRole('status').getByText('3 personaggi')).toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'Apri i diari' }).first())
			.toHaveAttribute('href', '/characters/id-alira');
	});

	it('shows the first-time state when there are no characters', async () => {
		api.GET.mockResolvedValue(listOf([]));
		render(Page);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Il tuo primo eroe ti aspetta.');
		await expect
			.element(page.getByRole('button', { name: /Forgia il tuo primo eroe/ }))
			.toBeVisible();
		expect(page.getByRole('searchbox').elements()).toHaveLength(0);
	});

	it('shows an error and retries', async () => {
		api.GET.mockResolvedValueOnce({ error: { title: 'Servizio fermo', status: 500 } });
		render(Page);

		await expect.element(page.getByRole('alert')).toHaveTextContent('Servizio fermo');
		await page.getByRole('button', { name: 'Riprova' }).click();

		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
	});

	it('does not search while typing', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		const calls = api.GET.mock.calls.length;

		await page.getByRole('searchbox', { name: 'Cerca per nome' }).fill('ali');
		await page.getByRole('searchbox', { name: 'Filtra per campagna' }).fill('Valkar');
		await settle();

		expect(api.GET.mock.calls.length).toBe(calls);
	});

	it('limits the length of the name search to 255 characters', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await expect
			.element(page.getByRole('searchbox', { name: 'Cerca per nome' }))
			.toHaveAttribute('maxlength', '255');
	});

	it('limits the length of the campaign filter to 255 characters', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await expect
			.element(page.getByRole('searchbox', { name: 'Filtra per campagna' }))
			.toHaveAttribute('maxlength', '255');
	});

	it('applies the name and campaign filters with the button', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await page.getByRole('searchbox', { name: 'Cerca per nome' }).fill(' ali ');
		await page.getByRole('searchbox', { name: 'Filtra per campagna' }).fill('Valkar');
		await page.getByRole('button', { name: 'Applica filtri' }).click();

		await expect.poll(() => lastQuery()['name']).toBe('ali');
		expect(lastQuery()['campaignName']).toBe('Valkar');
	});

	it('puts the apply button after the order button', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		const order = page.getByRole('button', { name: /Ordina per nome/ }).element();
		const apply = page.getByRole('button', { name: 'Applica filtri' }).element();

		expect(order.getBoundingClientRect().left).toBeLessThan(apply.getBoundingClientRect().left);
	});

	it('applies the filters with Enter', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await page.getByRole('searchbox', { name: 'Cerca per nome' }).fill('bra');
		await userEvent.keyboard('{Enter}');

		await expect.poll(() => lastQuery()['name']).toBe('bra');
	});

	it('clears the filters and the fields from the toolbar', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await page.getByRole('searchbox', { name: 'Cerca per nome' }).fill('ali');
		await page.getByRole('button', { name: 'Applica filtri' }).click();
		await expect.poll(() => lastQuery()['name']).toBe('ali');

		await page.getByRole('button', { name: 'Azzera', exact: true }).click();

		await expect.poll(() => lastQuery()['name']).toBeUndefined();
		await expect.element(page.getByRole('searchbox', { name: 'Cerca per nome' })).toHaveValue('');
	});

	it('reverses the order', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await page.getByRole('button', { name: /Ordina per nome/ }).click();

		await expect.poll(() => lastQuery()['descending']).toBe(true);
		await expect.element(page.getByRole('button', { name: /Z alla A/ })).toBeVisible();
	});

	it('offers to clear filters when nothing matches', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		api.GET.mockResolvedValue(listOf([], 0));

		await page.getByRole('searchbox', { name: 'Cerca per nome' }).fill('zzz');
		await page.getByRole('button', { name: 'Applica filtri' }).click();

		await expect
			.element(page.getByText('Nessun personaggio corrisponde alla ricerca.'))
			.toBeVisible();
		api.GET.mockResolvedValue(listOf(characters));
		await page.getByRole('button', { name: 'Azzera i filtri' }).click();

		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await expect.poll(() => lastQuery()['name']).toBeUndefined();
	});

	it('loads more characters', async () => {
		api.GET.mockResolvedValueOnce(listOf(characters, 4, 2));
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		api.GET.mockResolvedValueOnce({
			data: {
				list: [{ id: 'id-sereth', name: 'Sereth l’Errante', campaignName: 'Il Nord Spezzato' }],
				totalRows: 4,
				totalPages: 2,
				page: 2,
				pageSize: 12
			}
		});

		await page.getByRole('button', { name: 'Mostra altri personaggi' }).click();

		await expect.element(page.getByRole('heading', { name: 'Sereth l’Errante' })).toBeVisible();
		expect(lastQuery()['page']).toBe(2);
		expect(page.getByRole('button', { name: 'Mostra altri personaggi' }).elements()).toHaveLength(
			0
		);
	});
});

describe('master characters', () => {
	it('marks only the game master cards with a crown', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Orlan Bastacorta' })).toBeVisible();

		await expect.element(page.getByRole('img', { name: 'Master' })).toBeVisible();
		expect(page.getByRole('img', { name: 'Master' }).elements()).toHaveLength(1);
	});
});

describe('areas', () => {
	it('starts on the characters area', async () => {
		render(Page);

		await expect
			.element(page.getByRole('tab', { name: 'I miei personaggi' }))
			.toHaveAttribute('aria-selected', 'true');
		await expect
			.element(page.getByRole('tab', { name: 'Le mie campagne' }))
			.toHaveAttribute('aria-selected', 'false');
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
	});

	it('shows a coming soon message for the campaigns', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await page.getByRole('tab', { name: 'Le mie campagne' }).click();

		await expect.element(page.getByText('Coming Soon')).toBeVisible();
		await expect.element(page.getByText('Prossimamente')).toBeVisible();
		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Le campagne stanno arrivando');
		await expect
			.element(page.getByRole('heading', { name: 'Alira dei Salici' }))
			.not.toBeInTheDocument();
		await expect
			.element(page.getByRole('button', { name: 'Nuovo personaggio' }))
			.not.toBeInTheDocument();
	});

	it('shows the same card, in the same place, as the first-time state', async () => {
		api.GET.mockResolvedValue(listOf([]));
		render(Page);
		await expect.element(page.getByText('Forgia il tuo primo eroe')).toBeVisible();
		const first = document.querySelector('main li')?.getBoundingClientRect();

		await page.getByRole('tab', { name: 'Le mie campagne' }).click();
		await expect.element(page.getByText('Coming Soon')).toBeVisible();

		const soon = document.querySelector('main li')?.getBoundingClientRect();
		expect(soon?.left).toBe(first?.left);
		expect(soon?.width).toBe(first?.width);
		expect(soon?.height).toBe(first?.height);
	});

	it('moves between the areas with the arrow keys', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		(page.getByRole('tab', { name: 'I miei personaggi' }).element() as HTMLElement).focus();

		await userEvent.keyboard('{ArrowRight}');

		await expect
			.element(page.getByRole('tab', { name: 'Le mie campagne' }))
			.toHaveAttribute('aria-selected', 'true');
		await expect.element(page.getByRole('tab', { name: 'Le mie campagne' })).toHaveFocus();

		await userEvent.keyboard('{ArrowLeft}');

		await expect
			.element(page.getByRole('tab', { name: 'I miei personaggi' }))
			.toHaveAttribute('aria-selected', 'true');
	});

	it('opens on the area the user chose, when coming back from another page', async () => {
		areaChoice.select('campaigns');

		render(Page);

		await expect
			.element(page.getByRole('tab', { name: 'Le mie campagne' }))
			.toHaveAttribute('aria-selected', 'true');
		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Le campagne stanno arrivando');
	});

	it('draws the line under the areas as wide in both of them', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		const tablist = page.getByRole('tablist').element();
		const first = tablist.getBoundingClientRect().width;

		await page.getByRole('tab', { name: 'Le mie campagne' }).click();
		await expect.element(page.getByText('Coming Soon')).toBeVisible();

		expect(tablist.getBoundingClientRect().width).toBe(first);
	});

	it('keeps the list and the filters when coming back', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await page.getByRole('tab', { name: 'Le mie campagne' }).click();
		const calls = api.GET.mock.calls.length;

		await page.getByRole('tab', { name: 'I miei personaggi' }).click();

		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		expect(api.GET.mock.calls.length).toBe(calls);
	});

	it('fits the coming soon area on every width', async () => {
		for (const width of [280, 360, 768, 1280, 3440]) {
			await page.viewport(width, 800);
			const view = render(Page);
			await page.getByRole('tab', { name: 'Le mie campagne' }).click();
			await expect.element(page.getByText('Coming Soon')).toBeVisible();

			expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
			await view.unmount();
		}
	});
});

describe('edit character', () => {
	const detail = {
		id: 'id-alira',
		kind: 'PLAYER',
		name: 'Alira dei Salici',
		description: 'Elfa del nord',
		campaignName: 'Il Nord Spezzato',
		backgroundColor: '#3B2A6B',
		avatarUrl: null
	};

	function serve(overrides: Record<string, unknown> = {}): void {
		api.GET.mockImplementation((path: unknown) =>
			Promise.resolve(
				path === '/characters/{id}' ? { data: { ...detail, ...overrides } } : listOf(characters)
			)
		);
	}

	async function openEditor(): Promise<void> {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await page.getByRole('button', { name: 'Modifica Alira dei Salici' }).click();
		await expect.element(page.getByRole('dialog', { name: 'Modifica personaggio' })).toBeVisible();
	}

	it('opens the editor with the saved data, loaded from the character', async () => {
		serve();

		await openEditor();

		const call = api.GET.mock.calls.find((entry) => entry[0] === '/characters/{id}');
		expect((call?.[1] as { params: { path: { id: string } } }).params.path.id).toBe('id-alira');
		await expect
			.element(page.getByRole('textbox', { name: 'Nome *' }))
			.toHaveValue('Alira dei Salici');
		await expect
			.element(page.getByRole('textbox', { name: 'Descrizione' }))
			.toHaveValue('Elfa del nord');
		await expect
			.element(page.getByRole('textbox', { name: 'Campagna' }))
			.toHaveValue('Il Nord Spezzato');
		expect(page.getByText('Che cosa crei?').elements()).toHaveLength(0);
	});

	it('saves the changes, closes and reloads the list', async () => {
		serve();
		api.PUT.mockResolvedValue({ data: { ...detail, name: 'Alira la Saggia' } });
		await openEditor();
		const reloads = api.GET.mock.calls.length;

		await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira la Saggia');
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(page.getByText('Alira la Saggia è stato aggiornato.')).toBeVisible();
		expect(api.PUT.mock.calls[0]?.[0]).toBe('/characters/{id}');
		expect((api.PUT.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			name: 'Alira la Saggia',
			description: 'Elfa del nord',
			campaignName: 'Il Nord Spezzato',
			backgroundColor: '#3B2A6B'
		});
		await expect.poll(() => api.GET.mock.calls.length).toBeGreaterThan(reloads);
	});

	it('uploads the new image chosen in the editor', async () => {
		serve({ avatarUrl: 'http://img.test/a.png' });
		api.PUT.mockResolvedValue({ data: detail, response: { ok: true } });
		await openEditor();

		await chooseAndCrop(await samplePng('nuova.png'));
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByText('Alira dei Salici è stato aggiornato.')).toBeVisible();
		expect(api.PUT.mock.calls.map((call) => call[0])).toContain('/characters/{id}/avatar');
	});

	it('removes the current image when asked', async () => {
		serve({ avatarUrl: 'http://img.test/a.png' });
		api.PUT.mockResolvedValue({ data: detail });
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await openEditor();

		await page.getByRole('button', { name: 'Rimuovi' }).click();
		await page.getByRole('button', { name: 'Salva le modifiche' }).click();

		await expect.element(page.getByText('Alira dei Salici è stato aggiornato.')).toBeVisible();
		expect(api.DELETE).toHaveBeenCalledTimes(1);
	});

	it('shows the error and stays on the list when the character cannot be loaded', async () => {
		api.GET.mockImplementation((path: unknown) =>
			Promise.resolve(
				path === '/characters/{id}'
					? { error: { title: 'Non trovato', status: 404 } }
					: listOf(characters)
			)
		);
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		await page.getByRole('button', { name: 'Modifica Alira dei Salici' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Non trovato');
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('starts a new character after an edit with an empty form', async () => {
		serve();
		await openEditor();
		await page.getByRole('button', { name: 'Annulla' }).click();

		await page.getByRole('button', { name: 'Nuovo personaggio' }).first().click();

		await expect.element(page.getByRole('dialog', { name: 'Nuovo personaggio' })).toBeVisible();
		await expect.element(page.getByText('Che cosa crei?')).toBeVisible();
		await page.getByRole('button', { name: 'Continua' }).click();
		await expect.element(page.getByRole('textbox', { name: 'Nome *' })).toHaveValue('');
	});
});

describe('card link', () => {
	it('opens the diaries from anywhere on the card, except the edit button', async () => {
		await page.viewport(1280, 800);
		render(Page);
		const title = page.getByRole('heading', { name: 'Alira dei Salici' });
		await expect.element(title).toBeVisible();
		const link = page
			.getByRole('link', { name: /Apri i diari/ })
			.first()
			.element();
		const card = link.closest('article') ?? link;
		const box = card.getBoundingClientRect();

		const middle = document.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
		const edit = page.getByRole('button', { name: 'Modifica Alira dei Salici' }).element();
		const editBox = edit.getBoundingClientRect();
		const onEdit = document.elementFromPoint(
			editBox.left + editBox.width / 2,
			editBox.top + editBox.height / 2
		);

		expect(middle).toBe(link);
		expect(onEdit?.closest('button')).toBe(edit);
	});
});

describe('delete character', () => {
	async function askToDelete(): Promise<void> {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await page.getByRole('button', { name: 'Elimina Alira dei Salici' }).click();
	}

	it('puts the trash button beside the link that opens the diaries', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		const link = page
			.getByRole('link', { name: /Apri i diari/ })
			.first()
			.element();
		const trash = page.getByRole('button', { name: 'Elimina Alira dei Salici' }).element();
		const linkBox = link.getBoundingClientRect();
		const trashBox = trash.getBoundingClientRect();

		expect(trashBox.left).toBeGreaterThanOrEqual(linkBox.right);
		expect(Math.abs(trashBox.top - linkBox.top)).toBeLessThan(2);
		expect(document.elementFromPoint(trashBox.left + 5, trashBox.top + 5)?.closest('button')).toBe(
			trash
		);
	});

	it('warns about what is lost before doing anything', async () => {
		await askToDelete();

		await expect
			.element(page.getByRole('heading', { name: 'Eliminare Alira dei Salici?' }))
			.toBeVisible();
		await expect
			.element(page.getByText('tutti i suoi diari, con ogni pagina scritta;'))
			.toBeVisible();
		await expect
			.element(page.getByText('Non si può annullare e non c’è modo di recuperare i dati.'))
			.toBeVisible();
		expect(api.DELETE).not.toHaveBeenCalled();
	});

	it('wraps a name of 255 characters without spaces inside the dialog on the narrowest phone', async () => {
		const longName = 'A'.repeat(255);
		api.GET.mockResolvedValue(listOf([{ ...characters[0], name: longName }]));
		await page.viewport(320, 700);
		render(Page);
		await page.getByRole('button', { name: `Elimina ${longName}` }).click();

		const dialog = page.getByRole('dialog').element();
		await expect
			.element(page.getByRole('heading', { name: `Eliminare ${longName}?` }))
			.toBeVisible();

		expect(dialog.scrollWidth).toBeLessThanOrEqual(dialog.clientWidth);
	});

	it('does not scroll the page sideways with a name of 255 characters in the dialog', async () => {
		const longName = 'A'.repeat(255);
		api.GET.mockResolvedValue(listOf([{ ...characters[0], name: longName }]));
		await page.viewport(320, 700);
		render(Page);
		await page.getByRole('button', { name: `Elimina ${longName}` }).click();
		await expect
			.element(page.getByRole('heading', { name: `Eliminare ${longName}?` }))
			.toBeVisible();

		const overflow = document.documentElement.scrollWidth - document.documentElement.clientWidth;

		expect(overflow).toBe(0);
	});

	it('keeps the character when cancelled', async () => {
		await askToDelete();

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		expect(api.DELETE).not.toHaveBeenCalled();
	});

	it('deletes the character, tells it and reloads the list', async () => {
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await askToDelete();
		const reloads = api.GET.mock.calls.length;

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByText('Alira dei Salici è stato eliminato.')).toBeVisible();
		expect(api.DELETE.mock.calls[0]?.[0]).toBe('/characters/{id}');
		expect(
			(api.DELETE.mock.calls[0]?.[1] as { params: { path: { id: string } } }).params.path.id
		).toBe('id-alira');
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.poll(() => api.GET.mock.calls.length).toBeGreaterThan(reloads);
	});

	it('shows the first-time state after the last character is deleted', async () => {
		api.GET.mockResolvedValueOnce(listOf([characters[0]]));
		api.GET.mockResolvedValue(listOf([]));
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Il tuo primo eroe ti aspetta.');
		expect(page.getByRole('searchbox').elements()).toHaveLength(0);
	});

	it('keeps the other cards after a deletion', async () => {
		api.GET.mockResolvedValueOnce(listOf(characters));
		api.GET.mockResolvedValue(listOf(characters.slice(1)));
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('heading', { name: 'Brann Pietrafranta' })).toBeVisible();
		await expect.element(page.getByRole('heading', { name: 'Orlan Bastacorta' })).toBeVisible();
		expect(page.getByRole('heading', { name: 'Alira dei Salici' }).elements()).toHaveLength(0);
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

	it('counts an already deleted character as deleted', async () => {
		api.DELETE.mockResolvedValue({
			error: { title: 'Character not found', status: 404 },
			response: { ok: false, status: 404 }
		});
		await askToDelete();
		const reloads = api.GET.mock.calls.length;

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByText('Alira dei Salici è stato eliminato.')).toBeVisible();
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.poll(() => api.GET.mock.calls.length).toBeGreaterThan(reloads);
	});

	it('explains a change during the deletion in Italian and lets the user retry', async () => {
		api.DELETE.mockResolvedValueOnce({
			error: {
				title: 'Conflict',
				status: 409,
				violations: [{ message: 'The character changed while it was being deleted, try again' }]
			},
			response: { ok: false, status: 409 }
		});
		api.DELETE.mockResolvedValueOnce({ response: { ok: true, status: 204 } });
		await askToDelete();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Il personaggio è cambiato mentre lo eliminavi: riprova');
		await page.getByRole('button', { name: 'Elimina per sempre' }).click();
		await expect.element(page.getByText('Alira dei Salici è stato eliminato.')).toBeVisible();
	});
});

describe('card glow', () => {
	it('carries the color of the card', async () => {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		const style = document.querySelector('article.pcard')?.getAttribute('style') ?? '';

		expect(style).toContain('--card-glow');
		expect(style).toContain('#3B2A6B');
	});
});

describe('new character dialog', () => {
	async function openDialog(): Promise<void> {
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await page.getByRole('button', { name: 'Nuovo personaggio' }).first().click();
		await expect.element(page.getByRole('dialog', { name: 'Nuovo personaggio' })).toBeVisible();
	}

	async function openFields(): Promise<void> {
		await openDialog();
		await page.getByRole('button', { name: 'Continua' }).click();
		await expect.element(page.getByRole('textbox', { name: 'Nome *' })).toBeVisible();
	}

	it('opens from the header button and closes with Annulla', async () => {
		await openDialog();

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('closes with Escape', async () => {
		await openDialog();

		await userEvent.keyboard('{Escape}');

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('stays open when a drag that started inside ends on the backdrop', async () => {
		await openDialog();
		const dialog = page.getByRole('dialog').element();

		page
			.getByText('Che cosa crei?')
			.element()
			.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		dialog.dispatchEvent(new MouseEvent('click', { bubbles: true }));

		await expect.element(page.getByRole('dialog', { name: 'Nuovo personaggio' })).toBeVisible();
	});

	it('closes when the backdrop is pressed and released', async () => {
		await openDialog();
		const dialog = page.getByRole('dialog').element();

		dialog.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
		dialog.dispatchEvent(new MouseEvent('click', { bubbles: true }));

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('asks for the name before sending', async () => {
		await openFields();

		await page.getByRole('button', { name: 'Forgia il personaggio' }).click();

		await expect.element(page.getByText('Il nome è obbligatorio.')).toBeVisible();
		expect(api.POST).not.toHaveBeenCalled();
	});

	it('rejects a malformed HEX color', async () => {
		await openFields();

		await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira');
		await page.getByRole('textbox', { name: 'Colore in formato HEX' }).fill('#12');
		await page.getByRole('button', { name: 'Forgia il personaggio' }).click();

		await expect.element(page.getByText(/Usa il formato #RRGGBB/)).toBeVisible();
		expect(api.POST).not.toHaveBeenCalled();
	});

	it('picks a preset color', async () => {
		await openFields();

		await page.getByRole('button', { name: 'Rosso #7C2A22' }).click();

		await expect
			.element(page.getByRole('textbox', { name: 'Colore in formato HEX' }))
			.toHaveValue('#7C2A22');
		await expect
			.element(page.getByRole('button', { name: 'Rosso #7C2A22' }))
			.toHaveAttribute('aria-pressed', 'true');
	});

	it('refuses an unsupported image', async () => {
		await openFields();

		await page
			.getByLabelText('File dell’immagine')
			.upload(new File(['x'], 'animata.gif', { type: 'image/gif' }));

		await expect.element(page.getByRole('alert')).toHaveTextContent('Formato non supportato');
	});

	it('creates the character, closes and reloads the list', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', name: 'Alira' } });
		await openFields();
		const reloads = api.GET.mock.calls.length;

		await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira');
		await page.getByRole('textbox', { name: 'Campagna' }).fill('Il Nord');
		await page.getByRole('button', { name: 'Forgia il personaggio' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(page.getByText('Alira è pronto per i suoi diari.')).toBeVisible();
		const body = (api.POST.mock.calls[0]?.[1] as { body: Record<string, string> }).body;
		expect(body).toEqual({
			kind: 'PLAYER',
			name: 'Alira',
			campaignName: 'Il Nord',
			backgroundColor: '#3B2A6B'
		});
		await expect.poll(() => api.GET.mock.calls.length).toBeGreaterThan(reloads);
	});

	it('offers the player kind by default', async () => {
		await openDialog();

		await expect.element(page.getByRole('radio', { name: /Personaggio/ })).toBeChecked();
		await expect.element(page.getByRole('radio', { name: /Master/ })).not.toBeChecked();
	});

	it('starts with the choice of the kind only, with the warning that it is final', async () => {
		await openDialog();

		await expect.element(page.getByRole('progressbar', { name: 'Passo 1 di 2' })).toBeVisible();
		await expect.element(page.getByText(/La scelta è definitiva e non modificabile/)).toBeVisible();
		expect(page.getByRole('textbox').elements()).toHaveLength(0);
		expect(page.getByRole('button', { name: 'Forgia il personaggio' }).elements()).toHaveLength(0);
	});

	it('moves to the fields with the chosen kind shown and a way to change it', async () => {
		await openFields();

		await expect.element(page.getByRole('progressbar', { name: 'Passo 2 di 2' })).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Cambia' })).toBeVisible();
		expect(page.getByText('Che cosa crei?').elements()).toHaveLength(0);
		await expect.element(page.getByRole('button', { name: 'Forgia il personaggio' })).toBeVisible();
	});

	it('goes back to the kind with Cambia and with Indietro, keeping what was typed', async () => {
		await openFields();
		await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira');

		await page.getByRole('button', { name: 'Cambia' }).click();
		await expect.element(page.getByRole('radio', { name: /Personaggio/ })).toBeChecked();
		await page.getByRole('button', { name: 'Continua' }).click();
		await expect.element(page.getByRole('textbox', { name: 'Nome *' })).toHaveValue('Alira');

		await page.getByRole('button', { name: 'Indietro' }).click();
		await expect.element(page.getByText('Che cosa crei?')).toBeVisible();
	});

	it('words the second step for a master', async () => {
		await openDialog();
		await page.getByText('Master', { exact: true }).click();
		await page.getByRole('button', { name: 'Continua' }).click();

		await expect.element(page.getByRole('dialog', { name: 'Nuovo Master' })).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Campagna che guidi' })).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Nome *' }))
			.toHaveAttribute('placeholder', 'Il Master della Torre');
	});

	it('does not send anything between the two steps', async () => {
		await openFields();

		expect(api.POST).not.toHaveBeenCalled();
	});

	it('creates a game master when Master is chosen', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', name: 'Orlan', kind: 'GAME_MASTER' } });
		await openDialog();

		await page.getByText('Master', { exact: true }).click();
		await page.getByRole('button', { name: 'Continua' }).click();
		await page.getByRole('textbox', { name: 'Nome *' }).fill('Orlan');
		await page.getByRole('button', { name: 'Crea il Master' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		const body = (api.POST.mock.calls[0]?.[1] as { body: Record<string, string> }).body;
		expect(body['kind']).toBe('GAME_MASTER');
	});

	it('uploads the chosen image after creating the character', async () => {
		api.POST.mockResolvedValue({ data: { id: 'nuovo', name: 'Alira' } });
		api.PUT.mockResolvedValue({ response: { ok: true } });
		await openFields();

		await chooseAndCrop(await samplePng('v.png'));
		await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira');
		await page.getByRole('button', { name: 'Forgia il personaggio' }).click();

		await expect.element(page.getByText('Alira è pronto per i suoi diari.')).toBeVisible();
		expect(api.PUT).toHaveBeenCalledTimes(1);
	});

	describe('while the character is being created', () => {
		let finish: (value: unknown) => void = () => undefined;

		async function submitPending(): Promise<void> {
			api.POST.mockImplementation(
				() =>
					new Promise((resolve) => {
						finish = resolve;
					})
			);
			await openFields();
			await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira');
			await page.getByRole('button', { name: 'Forgia il personaggio' }).click();
			await vi.waitFor(() => {
				expect(api.POST).toHaveBeenCalledTimes(1);
			});
		}

		it('stays open when Escape is pressed', async () => {
			await submitPending();

			await userEvent.keyboard('{Escape}');

			await expect.element(page.getByRole('dialog', { name: 'Nuovo personaggio' })).toBeVisible();
		});

		it('stays open when the backdrop is pressed and released', async () => {
			await submitPending();
			const dialog = page.getByRole('dialog').element();

			dialog.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
			dialog.dispatchEvent(new MouseEvent('click', { bubbles: true }));

			await expect.element(page.getByRole('dialog', { name: 'Nuovo personaggio' })).toBeVisible();
		});

		it('stays open when the close button of the header is pressed', async () => {
			await submitPending();

			await page.getByRole('button', { name: 'Chiudi' }).click({ force: true });

			await expect.element(page.getByRole('dialog', { name: 'Nuovo personaggio' })).toBeVisible();
		});

		it('closes as usual after the request has finished', async () => {
			await submitPending();
			await userEvent.keyboard('{Escape}');

			finish({ data: { id: 'nuovo', name: 'Alira' } });

			await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		});
	});

	it('keeps the dialog open and shows the server error', async () => {
		api.POST.mockResolvedValue({
			error: { title: 'Bad', status: 400, violations: [{ message: 'nome già usato' }] }
		});
		await openFields();

		await page.getByRole('textbox', { name: 'Nome *' }).fill('Alira');
		await page.getByRole('button', { name: 'Forgia il personaggio' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('nome già usato');
		await expect.element(page.getByRole('dialog')).toBeVisible();
	});
});

describe('layout', () => {
	const widths = [280, 320, 360, 390, 412, 600, 768, 1024, 1280, 1920, 3440];

	function expectNoHorizontalScroll(): void {
		const overflow = document.documentElement.scrollWidth - document.documentElement.clientWidth;
		expect(overflow).toBe(0);
	}

	for (const width of widths) {
		it(`fits a ${String(width)} px wide screen`, async () => {
			await page.viewport(width, 800);
			render(Page);
			await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

			expectNoHorizontalScroll();
		});

		it(`fits the open dialog on ${String(width)} px`, async () => {
			await page.viewport(width, 800);
			render(Page);
			await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
			await page.getByRole('button', { name: 'Nuovo personaggio' }).first().click();
			const dialog = page.getByRole('dialog', { name: 'Nuovo personaggio' });
			await expect.element(dialog).toBeVisible();

			const box = dialog.element().getBoundingClientRect();
			expect(box.left).toBeGreaterThanOrEqual(0);
			expect(box.right).toBeLessThanOrEqual(width);
			expectNoHorizontalScroll();

			await page.getByRole('button', { name: 'Continua' }).click();
			await expect.element(page.getByRole('textbox', { name: 'Nome *' })).toBeVisible();
			const second = dialog.element().getBoundingClientRect();
			expect(second.left).toBeGreaterThanOrEqual(0);
			expect(second.right).toBeLessThanOrEqual(width);
			expectNoHorizontalScroll();
		});
	}

	it('keeps every control of both steps at least 44 px tall on a phone', async () => {
		await page.viewport(360, 800);
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();
		await page.getByRole('button', { name: 'Nuovo personaggio' }).first().click();
		const dialog = page.getByRole('dialog', { name: 'Nuovo personaggio' });
		await expect.element(dialog).toBeVisible();

		const heights = (): number[] =>
			[
				...dialog
					.getByRole('button')
					.elements()
					.filter((button) => !(button instanceof HTMLInputElement)),
				...dialog
					.getByRole('radio')
					.elements()
					.map((radio) => radio.closest('label') ?? radio)
			].map((control) => control.getBoundingClientRect().height);

		const first = Math.min(...heights());
		await page.getByRole('button', { name: 'Continua' }).click();
		await expect.element(page.getByRole('textbox', { name: 'Nome *' })).toBeVisible();
		const second = Math.min(...heights());

		expect(Math.min(first, second)).toBeGreaterThanOrEqual(43.5);
	});

	it('keeps every control at least 44 px tall on a phone', async () => {
		await page.viewport(360, 800);
		render(Page);
		await expect.element(page.getByRole('heading', { name: 'Alira dei Salici' })).toBeVisible();

		const controls = [
			...page.getByRole('button').elements(),
			...page.getByRole('tab').elements(),
			...page.getByRole('link').elements(),
			...page.getByRole('searchbox').elements()
		];
		const heights = controls.map((control) => control.getBoundingClientRect().height);

		expect(Math.min(...heights)).toBeGreaterThanOrEqual(43.5);
	});
});

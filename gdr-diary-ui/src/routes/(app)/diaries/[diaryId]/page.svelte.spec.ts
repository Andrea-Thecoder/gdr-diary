import { page, userEvent } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import type { JSONContent } from '@tiptap/core';
import '../../../layout.css';

type Call = (...args: unknown[]) => Promise<unknown>;

const { api, navigation } = vi.hoisted(() => ({
	api: { GET: vi.fn<Call>(), POST: vi.fn<Call>(), PUT: vi.fn<Call>(), DELETE: vi.fn<Call>() },
	navigation: {
		goto: vi.fn<(...args: unknown[]) => Promise<void>>(),
		beforeNavigate: vi.fn<(...args: unknown[]) => void>(),
		onNavigate: vi.fn<(...args: unknown[]) => void>()
	}
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('$app/navigation', () => navigation);
vi.mock('$app/state', async () => {
	const { pageState } = await import('./page-state.svelte.js');
	return { page: pageState };
});

import { DEFAULT_PREFERENCES } from '#lib/settings/preferences.js';
import { preferences } from '#lib/settings/preferences.svelte.js';
import type { Bookmark } from '#lib/book/bookmark.js';
import { clearAllDrafts, type Draft } from '#lib/book/draft.js';
import { emptyDoc, type SpreadData } from '#lib/book/pages.js';
import {
	characterFixture,
	diaryFixture,
	doc,
	serveBook,
	spreadData
} from '#lib/book/test-support.js';
import { required, settle } from '#lib/test-support.js';
import Page from './+page.svelte';
import { pageState, setPageUrl, setShallowUrl } from './page-state.svelte.js';

const diary = diaryFixture;
const character = characterFixture;

function spreadOf(total: number, spread: number): SpreadData {
	return spreadData(total, spread, (number) => `Testo della pagina ${String(number)}`);
}

function mockServer(total: number): void {
	serveBook(api, total, (number) => `Testo della pagina ${String(number)}`);
}

function renderPage(
	total: number,
	spread = 1,
	drafts: Draft[] = [],
	bookmark: Bookmark | null = null
): void {
	setPageUrl(`http://localhost/diaries/d1?vista=${String(spread)}`);
	render(Page, {
		data: { diary, character, initial: spreadOf(total, spread), spread, drafts, bookmark },
		params: { diaryId: 'd1' }
	} as never);
}

const LEAF = '.leaf-swing';

function leafShown(): boolean {
	return document.querySelector(LEAF) !== null;
}

function watchLeaf(): () => boolean {
	let seen = leafShown();
	const observer = new MutationObserver(() => {
		seen = seen || leafShown();
	});
	observer.observe(document.body, { childList: true, subtree: true });
	onTestFinished(() => {
		observer.disconnect();
	});
	return () => seen;
}

function putCalls(): unknown[][] {
	return api.PUT.mock.calls;
}

async function typeLines(lines: string[]): Promise<void> {
	for (const line of lines) {
		await userEvent.keyboard(line);
	}
}

function enteredLines(label: string, from: number, to: number): string[] {
	return Array.from(
		{ length: to - from + 1 },
		(_, index) => `{Enter}${label} ${String(from + index)}`
	);
}

function numberedLines(label: string, count: number): string[] {
	return Array.from(
		{ length: count },
		(_, index) => `${label} ${String(index + 1)}${index + 1 < count ? '{Enter}' : ''}`
	);
}

afterEach(() => {
	Reflect.deleteProperty(document, 'visibilityState');
});

beforeEach(async () => {
	await clearAllDrafts();
	vi.clearAllMocks();
	api.GET.mockReset();
	api.POST.mockReset();
	api.PUT.mockReset();
	navigation.goto.mockImplementation(async (href: unknown, options: unknown) => {
		await new Promise((resolve) => setTimeout(resolve, 20));
		if ((options as { shallow?: boolean } | undefined)?.shallow === true) {
			setShallowUrl(String(href));
		} else {
			setPageUrl(String(href));
		}
	});
	preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: false };
	await page.viewport(1280, 900);
});

describe('open diary', () => {
	it('shows the recap, the counter and the two pages', async () => {
		mockServer(23);
		renderPage(23);

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Cronache del Nord');
		await expect.element(page.getByText('Alira dei Salici')).toBeVisible();
		await expect.element(page.getByText('Il Nord Spezzato')).toBeVisible();
		await expect.element(page.getByText('Pagine 1 e 2 di 23')).toBeVisible();
		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 1' }))
			.toHaveTextContent('Testo della pagina 1');
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('Testo della pagina 2');
		await expect.element(page.getByText('23 pagine scritte')).toBeVisible();
	});

	it('links back to the diaries of the character', async () => {
		mockServer(4);
		renderPage(4);

		await expect
			.element(page.getByRole('link', { name: /I diari di Alira dei Salici/ }))
			.toHaveAttribute('href', '/characters/c1');
	});

	it('has the next corner and no previous corner on the first spread', async () => {
		mockServer(23);
		renderPage(23);

		await expect.element(page.getByRole('button', { name: 'Pagine successive' })).toBeVisible();
		expect(page.getByRole('button', { name: 'Pagine precedenti' }).elements()).toHaveLength(0);
	});

	it('has the previous corner and no next corner on the last spread', async () => {
		mockServer(4);
		renderPage(4, 2);

		await expect.element(page.getByRole('button', { name: 'Pagine precedenti' })).toBeVisible();
		expect(page.getByRole('button', { name: 'Pagine successive' }).elements()).toHaveLength(0);
		await expect.element(page.getByText('Pagine 3 e 4 di 4')).toBeVisible();
	});

	it('shows a blank sheet after an odd last page', async () => {
		mockServer(23);
		renderPage(23, 12);

		await expect.element(page.getByText('Pagina 23 di 23')).toBeVisible();
		expect(page.getByText('Comincia a scrivere la tua storia...').elements()).toHaveLength(0);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 23' })).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 24' }))
			.toHaveAttribute('contenteditable', 'false');
	});

	it('guides the first page of an empty diary', async () => {
		mockServer(0);
		renderPage(0);

		await expect.element(page.getByText('Nessuna pagina ancora')).toBeVisible();
		await expect.element(page.getByText('Comincia a scrivere la tua storia...')).toBeVisible();
		expect(page.getByText('Comincia a scrivere la tua storia...').elements()).toHaveLength(1);
		expect(
			page.getByRole('button', { name: /Pagine (successive|precedenti)/ }).elements()
		).toHaveLength(0);
	});

	it('says the diary is full at one hundred pages', async () => {
		mockServer(100);
		renderPage(100, 50);

		await expect.element(page.getByText(/Il diario è pieno: 100 pagine su 100/)).toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'Nuovo diario' }))
			.toHaveAttribute('href', '/characters/c1');
		expect(page.getByRole('button', { name: 'Pagine successive' }).elements()).toHaveLength(0);
		await expect.element(page.getByText('100 pagine scritte')).toBeVisible();
	});
});

describe('turning pages', () => {
	it('opens the next spread from the corner', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('button', { name: 'Pagine successive' }).click();

		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 3' }))
			.toHaveTextContent('Testo della pagina 3');
		await expect.element(page.getByRole('button', { name: 'Pagine precedenti' })).toBeVisible();
		expect(navigation.goto).toHaveBeenCalledWith('/diaries/d1?vista=2', { shallow: true });
	});

	it('stays on the spread it turned to while the page address is still the one it was loaded with', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('button', { name: 'Pagine successive' }).click();
		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
		await vi.waitFor(() => {
			expect(pageState.shallow?.url.search).toBe('?vista=2');
		});
		await settle();

		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
	});

	it('goes back from the corner', async () => {
		mockServer(23);
		renderPage(23, 3);

		await page.getByRole('button', { name: 'Pagine precedenti' }).click();

		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
	});

	it('turns with Alt and the arrows', async () => {
		mockServer(23);
		renderPage(23);

		await userEvent.keyboard('{Alt>}{ArrowRight}{/Alt}');
		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();

		await userEvent.keyboard('{Alt>}{ArrowLeft}{/Alt}');
		await expect.element(page.getByText('Pagine 1 e 2 di 23')).toBeVisible();
	});

	it('scrolls the pages from the bar under the book', async () => {
		mockServer(23);
		renderPage(23);

		const slider = page.getByRole('slider', { name: 'Sfoglia le pagine' });
		expect(page.getByRole('spinbutton').elements()).toHaveLength(0);
		expect(page.getByRole('button', { name: 'Vai' }).elements()).toHaveLength(0);
		await expect.element(slider).toHaveAttribute('max', '12');
		await expect.element(slider).toHaveAttribute('aria-valuetext', 'Pagine 1 e 2 di 23');

		slider.element().focus();
		await userEvent.keyboard('{ArrowRight}');
		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
		await expect.element(slider).toHaveAttribute('aria-valuetext', 'Pagine 3 e 4 di 23');

		await userEvent.keyboard('{End}');
		await expect.element(page.getByText('Pagina 23 di 23')).toBeVisible();

		await userEvent.keyboard('{Home}');
		await expect.element(page.getByText('Pagine 1 e 2 di 23')).toBeVisible();
	});

	it('keeps the handle where it was dropped while the pages turn', async () => {
		mockServer(23);
		preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: true };
		renderPage(23);

		const slider = page.getByRole('slider', { name: 'Sfoglia le pagine' });
		slider.element().focus();
		await userEvent.keyboard('{End}');
		expect(slider.element()).toHaveProperty('value', '12');
		await vi.waitFor(() => {
			expect(leafShown()).toBe(true);
		});
		expect(slider.element()).toHaveProperty('value', '12');
		await expect.element(page.getByText('Pagina 23 di 23')).toBeVisible();
		expect(slider.element()).toHaveProperty('value', '12');
	});

	it('keeps the bar still when the diary has a single page', async () => {
		mockServer(1);
		renderPage(1);

		await expect.element(page.getByRole('slider', { name: 'Sfoglia le pagine' })).toBeDisabled();
	});

	it('shows the turning leaf while the page turns and removes it afterwards', async () => {
		mockServer(23);
		preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: true };
		renderPage(23);

		await page.getByRole('button', { name: 'Pagine successive' }).click();

		await vi.waitFor(() => {
			expect(leafShown()).toBe(true);
		});
		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
		await vi.waitFor(() => {
			expect(leafShown()).toBe(false);
		});
	});

	it('turns without the leaf when the reduced motion preference is on', async () => {
		mockServer(23);
		preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: true, reduceMotion: true };
		renderPage(23);
		const leafSeen = watchLeaf();

		await page.getByRole('button', { name: 'Pagine successive' }).click();

		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
		expect(leafSeen()).toBe(false);
	});

	it('turns with the leaf when the page turn is on and motion is not reduced', async () => {
		mockServer(23);
		preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: true, reduceMotion: false };
		renderPage(23);
		const leafSeen = watchLeaf();

		await page.getByRole('button', { name: 'Pagine successive' }).click();

		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
		expect(leafSeen()).toBe(true);
	});

	it('does not turn with Alt and the arrows while the cursor is in the editor', async () => {
		mockServer(23);
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();

		await userEvent.keyboard('{Alt>}{ArrowRight}{/Alt}');
		await settle();

		expect(navigation.goto).not.toHaveBeenCalled();
	});

	it('keeps the spread in the editor when Alt and the left arrow are pressed there', async () => {
		mockServer(23);
		renderPage(23, 3);
		await page.getByRole('textbox', { name: 'Pagina 5' }).click();

		await userEvent.keyboard('{Alt>}{ArrowLeft}{/Alt}');
		await settle();

		await expect.element(page.getByText('Pagine 5 e 6 di 23')).toBeVisible();
	});

	it('turns with Alt and the arrows when the focus is on a link', async () => {
		mockServer(23);
		renderPage(23);
		page
			.getByRole('link', { name: /I diari di Alira dei Salici/ })
			.element()
			.focus();

		await userEvent.keyboard('{Alt>}{ArrowRight}{/Alt}');

		await expect.element(page.getByText('Pagine 3 e 4 di 23')).toBeVisible();
	});

	it('stays on the page and shows the error when the save before turning fails', async () => {
		mockServer(23);
		api.PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('x');
		await page.getByRole('button', { name: 'Pagine successive' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('La pagina 1 non è stata salvata');
		await expect.element(page.getByText('Pagine 1 e 2 di 23')).toBeVisible();
	});
});

describe('writing', () => {
	it('saves the page after a pause in writing', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('{Control>}{End}{/Control}!');

		await expect.element(page.getByText('Da salvare', { exact: true })).toBeVisible();
		expect(page.getByText('Salvataggio…').elements()).toHaveLength(0);
		await vi.waitFor(
			() => {
				expect(putCalls()).toHaveLength(1);
			},
			{ timeout: 4000 }
		);
		const call = putCalls()[0];
		expect(call?.[0]).toBe('/pages/{id}');
		expect(JSON.stringify(call?.[1])).toContain('Testo della pagina 1!');
		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
	});

	it(
		'creates the first page of an empty diary only when there is text',
		{ timeout: 20000 },
		async () => {
			mockServer(0);
			renderPage(0);

			await page.getByRole('textbox', { name: 'Pagina 1' }).click();
			await userEvent.keyboard('{Enter}');
			await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
			expect(api.POST).not.toHaveBeenCalled();

			await userEvent.keyboard('Primo giorno');
			await vi.waitFor(
				() => {
					expect(api.POST).toHaveBeenCalledTimes(1);
				},
				{ timeout: 4000 }
			);
			await expect.element(page.getByText('Pagina 1 di 1')).toBeVisible();
		}
	);

	it('shows the failure, keeps the text and saves again on retry', async () => {
		mockServer(23);
		api.PUT.mockResolvedValueOnce({ error: { title: 'Rete assente' }, response: { status: 500 } });
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await expect
			.element(page.getByRole('alert'), { timeout: 4000 })
			.toHaveTextContent('La pagina 1 non è stata salvata');
		await expect.element(page.getByText('Non salvato', { exact: true })).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('!');

		await page.getByRole('button', { name: 'Riprova ora' }).click();

		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
		expect(page.getByRole('alert').elements()).toHaveLength(0);
	});

	describe('when the API refuses the page', () => {
		const refusal = {
			error: { title: 'Contenuto non valido', status: 400 },
			response: { status: 400 }
		};

		async function typeRefused(): Promise<void> {
			mockServer(23);
			api.PUT.mockResolvedValueOnce(refusal);
			renderPage(23);
			await page.getByRole('textbox', { name: 'Pagina 1' }).click();
			await userEvent.keyboard('!');
		}

		it('shows the message of the API and says the text is not lost', async () => {
			await typeRefused();

			await expect
				.element(page.getByRole('alert'), { timeout: 4000 })
				.toHaveTextContent(
					'La pagina 1 non è stata salvata: Contenuto non valido Il testo è ancora qui sopra: non lo perdi.'
				);
		});

		it('offers to retry now', async () => {
			await typeRefused();

			await expect
				.element(page.getByRole('button', { name: 'Riprova ora' }), { timeout: 4000 })
				.toBeVisible();
		});

		it('keeps the text in the page', async () => {
			await typeRefused();
			await expect.element(page.getByRole('alert'), { timeout: 4000 }).toBeVisible();

			await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('!');
		});

		it('saves again on retry and removes the banner', async () => {
			await typeRefused();
			await expect.element(page.getByRole('alert'), { timeout: 4000 }).toBeVisible();

			await page.getByRole('button', { name: 'Riprova ora' }).click();

			await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
			expect(page.getByRole('alert').elements()).toHaveLength(0);
		});
	});

	it('says the page was deleted elsewhere and offers no retry', async () => {
		mockServer(23);
		api.PUT.mockResolvedValue({
			error: { title: 'Pagina non trovata', status: 404 },
			response: { status: 404 }
		});
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');

		await expect
			.element(page.getByRole('alert'), { timeout: 4000 })
			.toHaveTextContent(
				'Pagina 1: Questa pagina è stata eliminata altrove: copia il testo se ti serve e ricarica il diario.'
			);
		expect(page.getByRole('button', { name: 'Riprova ora' }).elements()).toHaveLength(0);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('!');
	});

	it('says it is offline, keeps the text and saves when the connection comes back', async () => {
		mockServer(23);
		api.PUT.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await expect
			.element(page.getByRole('alert'), { timeout: 4000 })
			.toHaveTextContent('Sei offline');
		await expect.element(page.getByText('Offline', { exact: true })).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('!');

		window.dispatchEvent(new Event('online'));

		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
		expect(page.getByRole('alert').elements()).toHaveLength(0);
	});

	it('asks which version to keep after a conflict and saves mine over the saved one', async () => {
		mockServer(23);
		api.PUT.mockResolvedValueOnce({
			error: { title: 'CONFLICT', status: 409 },
			response: { status: 409 }
		});
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await expect
			.element(page.getByRole('alert'), { timeout: 4000 })
			.toHaveTextContent('modificata altrove');
		await expect.element(page.getByText('Conflitto', { exact: true })).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('!');

		api.GET.mockImplementation(() =>
			Promise.resolve({
				data: {
					list: [{ id: 'p1', pageNumber: 1, content: doc('Scritto altrove'), version: 7 }],
					totalRows: 23
				}
			})
		);
		await page.getByRole('button', { name: 'Tieni la mia versione' }).click();

		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
		expect(page.getByRole('alert').elements()).toHaveLength(0);
		expect(JSON.stringify(putCalls()[1]?.[1])).toContain('"version":7');
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('!');
	});

	it('takes the saved version after a conflict when asked to', async () => {
		mockServer(23);
		api.PUT.mockResolvedValueOnce({
			error: { title: 'CONFLICT', status: 409 },
			response: { status: 409 }
		});
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await expect
			.element(page.getByRole('alert'), { timeout: 4000 })
			.toHaveTextContent('modificata altrove');

		api.GET.mockImplementation(() =>
			Promise.resolve({
				data: {
					list: [{ id: 'p1', pageNumber: 1, content: doc('Scritto altrove'), version: 7 }],
					totalRows: 23
				}
			})
		);
		await page.getByRole('button', { name: 'Usa la versione salvata' }).click();

		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 1' }))
			.toHaveTextContent('Scritto altrove');
		expect(putCalls()).toHaveLength(1);
	});

	it('brings back the text that was not saved the last time and saves it', async () => {
		mockServer(23);
		const unsaved: Draft = {
			diaryId: 'd1',
			page: 1,
			pageId: 'p1',
			baseVersion: 3,
			content: doc('Testo recuperato'),
			savedAt: 1
		};
		renderPage(23, 1, [unsaved]);

		await expect
			.element(page.getByText('Modifiche non salvate recuperate (pagina 1)'))
			.toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 1' }))
			.toHaveTextContent('Testo recuperato');
		await vi.waitFor(
			() => {
				expect(putCalls()).toHaveLength(1);
			},
			{ timeout: 4000 }
		);
		expect(JSON.stringify(putCalls()[0]?.[1])).toContain('Testo recuperato');
	});

	it(
		'saves with the button, and with Ctrl+S, even when automatic saving is off',
		{ timeout: 20000 },
		async () => {
			mockServer(23);
			preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: false, autoSave: false };
			renderPage(23);

			await page.getByRole('textbox', { name: 'Pagina 1' }).click();
			await userEvent.keyboard('!');
			await expect.element(page.getByText('Non salvato', { exact: true })).toBeVisible();
			expect(putCalls()).toHaveLength(0);

			await page.getByRole('button', { name: 'Salva', exact: true }).click();

			await vi.waitFor(() => {
				expect(putCalls()).toHaveLength(1);
			});
			await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
			await expect.element(page.getByRole('button', { name: 'Salva', exact: true })).toBeDisabled();

			await page.getByRole('textbox', { name: 'Pagina 1' }).click();
			await userEvent.keyboard('?');
			await expect.element(page.getByRole('button', { name: 'Salva', exact: true })).toBeEnabled();
			await userEvent.keyboard('{Control>}s{/Control}');
			await vi.waitFor(() => {
				expect(putCalls()).toHaveLength(2);
			});
		}
	);

	it('moves the text to a new page when the page is full and follows it', async () => {
		mockServer(0);
		renderPage(0);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await typeLines(numberedLines('riga', 17));

		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('riga 16');
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('riga 17');
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 1' }))
			.not.toHaveTextContent('riga 16');
		await vi.waitFor(
			() => {
				expect(api.POST).toHaveBeenCalledTimes(2);
			},
			{ timeout: 9000 }
		);
		expect(page.getByText('Pagina 2 aggiunta').elements()).toHaveLength(0);
		await expect.element(page.getByText('Pagine 1 e 2 di 2')).toBeVisible();
	});
});

describe('a blank page after a page that is not full', () => {
	function blankSecond(): void {
		const first = {
			id: 'p1',
			pageNumber: 1,
			content: doc('Testo'),
			version: 3
		};
		const second = {
			id: 'p2',
			pageNumber: 2,
			content: emptyDoc(),
			version: 3
		};
		api.GET.mockResolvedValue({ data: { list: [first, second], totalRows: 2 } });
		api.PUT.mockImplementation((_path: unknown, init: unknown) => {
			const id = (init as { params: { path: { id: string } } }).params.path.id;
			return Promise.resolve({
				data: { id, pageNumber: Number(id.slice(1)), content: {}, version: 4 }
			});
		});
		setPageUrl('http://localhost/diaries/d1?vista=1');
		render(Page, {
			data: { diary, character, initial: { pages: [first, second], totalPages: 2 }, spread: 1 },
			params: { diaryId: 'd1' }
		} as never);
	}

	it('cannot be written until the page before is complete, then it opens', async () => {
		blankSecond();

		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveAttribute('contenteditable', 'false');
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('{Control>}{End}{/Control}');
		await typeLines(enteredLines('riga', 2, 14));
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveAttribute('contenteditable', 'false');

		await userEvent.keyboard('{Enter}riga 15');

		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveAttribute('contenteditable', 'true');
	});
});

describe('blank pages', () => {
	it('removes a page that was emptied when the writer leaves it, and the next ones slide back', async () => {
		const list = ['p1', 'p2', 'p3', 'p4'];
		const texts = new Map(
			list.map((id, index) => [id, doc(`Testo della pagina ${String(index + 1)}`)])
		);
		const view = (number: number): SpreadData => ({
			totalPages: list.length,
			pages: list
				.map((id, index) => ({
					id,
					pageNumber: index + 1,
					content: required(texts.get(id), 'Testo'),
					version: 3
				}))
				.filter((item) => item.pageNumber === 2 * number - 1 || item.pageNumber === 2 * number)
		});
		api.GET.mockImplementation((_path: unknown, init: unknown) => {
			const number = Number((init as { params: { query: { page: string } } }).params.query.page);
			return Promise.resolve({ data: { list: view(number).pages, totalRows: list.length } });
		});
		api.PUT.mockImplementation((_path: unknown, init: unknown) => {
			const { params, body } = init as {
				params: { path: { id: string } };
				body: { content: JSONContent };
			};
			texts.set(params.path.id, body.content);
			return Promise.resolve({
				data: {
					id: params.path.id,
					pageNumber: list.indexOf(params.path.id) + 1,
					content: body.content,
					version: 4
				}
			});
		});
		api.DELETE.mockImplementation((_path: unknown, init: unknown) => {
			const id = (init as { params: { path: { id: string } } }).params.path.id;
			list.splice(list.indexOf(id), 1);
			return Promise.resolve({ response: { ok: true } });
		});
		setPageUrl('http://localhost/diaries/d1?vista=1');
		render(Page, {
			data: { diary, character, initial: view(1), spread: 1 },
			params: { diaryId: 'd1' }
		} as never);

		await page.getByRole('textbox', { name: 'Pagina 2' }).click();
		await userEvent.keyboard('{Control>}a{/Control}{Backspace}');
		await page.getByRole('button', { name: 'Pagine successive' }).click();

		await vi.waitFor(() => {
			expect(api.DELETE).toHaveBeenCalledTimes(1);
		});
		expect(page.getByText('Pagina vuota eliminata').elements()).toHaveLength(0);
		expect(api.DELETE).toHaveBeenCalledWith('/pages/{id}', { params: { path: { id: 'p2' } } });
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('Testo della pagina 3');
		await expect.element(page.getByText('Pagine 1 e 2 di 3')).toBeVisible();
	});
});

describe('overflow', () => {
	it('puts the text that does not fit in front of the next page when it already has text', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('{Control>}{End}{/Control}');
		await typeLines(enteredLines('nuova', 1, 16));

		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('nuova 16');
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('Testo della pagina 2');
		await vi.waitFor(
			() => {
				expect(putCalls()).toHaveLength(2);
			},
			{ timeout: 8000 }
		);
		expect(api.POST).not.toHaveBeenCalled();
	});

	it('creates the next spread and turns the page to it when the right page is full', async () => {
		mockServer(2);
		preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: true };
		renderPage(2);

		await page.getByRole('textbox', { name: 'Pagina 2' }).click();
		await userEvent.keyboard('{Control>}{End}{/Control}');
		await typeLines(enteredLines('nuova', 1, 16));

		await expect.element(page.getByText('Pagina 3 di 3'), { timeout: 4000 }).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 3' }))
			.toHaveTextContent('nuova 15nuova 16');
		await vi.waitFor(() => {
			expect(document.activeElement).toBe(
				page.getByRole('textbox', { name: 'Pagina 3' }).element()
			);
		});
	});

	it('refuses the text that does not fit on the hundredth page', async () => {
		mockServer(100);
		renderPage(100, 50);

		await page.getByRole('textbox', { name: 'Pagina 100' }).click();
		await userEvent.keyboard('{Control>}{End}{/Control}');
		await typeLines(enteredLines('ancora', 1, 16));

		await expect
			.element(page.getByText('Il diario è pieno: la pagina 100 non ha più spazio.'))
			.toBeVisible();
		expect(api.POST).not.toHaveBeenCalled();
		await expect.element(page.getByText('100 pagine scritte')).toBeVisible();
	});
});

describe('formatting bar', () => {
	it('lists every tool with at least 44 px to touch', async () => {
		mockServer(23);
		renderPage(23);
		await expect
			.element(page.getByRole('button', { name: 'Grassetto', exact: true }))
			.toBeVisible();

		const names = [
			'Titolo 1',
			'Titolo 2',
			'Grassetto',
			'Corsivo',
			'Sottolineato',
			'Elenco puntato',
			'Elenco numerato',
			'Citazione',
			'Allinea a sinistra',
			'Centra',
			'Allinea a destra',
			'Linea di separazione',
			'Annulla',
			'Ripeti'
		];
		const boxes = names.map((name) =>
			page.getByRole('button', { name, exact: true }).element().getBoundingClientRect()
		);

		expect(Math.min(...boxes.flatMap((box) => [box.width, box.height]))).toBeGreaterThanOrEqual(44);
	});

	it('toggles bold on the page being written and shows it pressed', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await page.getByRole('button', { name: 'Grassetto' }).click();
		await userEvent.keyboard('forte');

		await expect
			.element(page.getByRole('button', { name: 'Grassetto' }))
			.toHaveAttribute('aria-pressed', 'true');
		expect(
			page.getByRole('textbox', { name: 'Pagina 1' }).element().querySelector('strong')?.textContent
		).toBe('forte');
	});

	it('titles only the words written while the tool is on', async () => {
		mockServer(23);
		renderPage(23);

		const textbox = page.getByRole('textbox', { name: 'Pagina 1' });
		await textbox.click();
		await userEvent.keyboard('{Control>}{End}{/Control}');
		await userEvent.keyboard(' ');
		await page.getByRole('button', { name: 'Titolo 2' }).click();
		await userEvent.keyboard('grande');
		await page.getByRole('button', { name: 'Titolo 2' }).click();
		await userEvent.keyboard(' piccolo');

		const titles = Array.from(textbox.element().querySelectorAll('[data-title]')).map(
			(node) => node.textContent
		);
		expect(titles).toEqual(['grande']);
		expect(textbox.element().querySelectorAll('h1, h2')).toHaveLength(0);
	});

	it('goes back to the end of the page before when backspace is pressed at the start of a page', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 2' }).click();
		await userEvent.keyboard('{Control>}{Home}{/Control}');
		await userEvent.keyboard('{Backspace}');
		await userEvent.keyboard('X');

		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 1' }))
			.toHaveTextContent(/Testo della pagina 1.*X$/);
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('Testo della pagina 2');
	});

	it('disables undo until something is written', async () => {
		mockServer(23);
		renderPage(23);

		await expect.element(page.getByRole('button', { name: 'Annulla' })).toBeDisabled();
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('x');
		await expect.element(page.getByRole('button', { name: 'Annulla' })).toBeEnabled();
	});

	it('does not take the focus away from the page when a tool is pressed', async () => {
		mockServer(23);
		renderPage(23);

		await page.getByRole('textbox', { name: 'Pagina 2' }).click();
		await page.getByRole('button', { name: 'Corsivo' }).click();

		expect(document.activeElement).toBe(page.getByRole('textbox', { name: 'Pagina 2' }).element());
	});
});

describe('delete page', () => {
	function serveList(items: string[]): { ids: () => string[] } {
		const list = items.map((text, index) => ({ id: `p${String(index + 1)}`, text }));
		api.GET.mockImplementation((_path: unknown, init: unknown) => {
			const view = Number((init as { params: { query: { page: string } } }).params.query.page);
			const pages = list
				.map((item, index) => ({
					id: item.id,
					pageNumber: index + 1,
					content: doc(item.text),
					version: 3
				}))
				.filter((item) => item.pageNumber === 2 * view - 1 || item.pageNumber === 2 * view);
			return Promise.resolve({ data: { list: pages, totalRows: list.length } });
		});
		api.DELETE.mockImplementation((_path: unknown, init: unknown) => {
			const id = (init as { params: { path: { id: string } } }).params.path.id;
			list.splice(
				list.findIndex((item) => item.id === id),
				1
			);
			return Promise.resolve({ response: { ok: true } });
		});
		return { ids: () => list.map((item) => item.id) };
	}

	function renderList(items: string[]): { ids: () => string[] } {
		const server = serveList(items);
		setPageUrl('http://localhost/diaries/d1?vista=1');
		const data = spreadOf(items.length, 1);
		data.pages = data.pages.map((item, index) => ({
			...item,
			content: doc(items[index] ?? '')
		}));
		render(Page, {
			data: { diary, character, initial: data, spread: 1, drafts: [] },
			params: { diaryId: 'd1' }
		} as never);
		return server;
	}

	it('puts a quiet trash button on each written page, next to the binding', async () => {
		renderList(['uno', 'due', 'tre']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();

		const left = page.getByRole('button', { name: 'Elimina la pagina 1' }).element();
		const right = page.getByRole('button', { name: 'Elimina la pagina 2' }).element();
		const leftFrame = left.parentElement?.getBoundingClientRect();
		const rightFrame = right.parentElement?.getBoundingClientRect();
		const leftBox = left.getBoundingClientRect();
		const rightBox = right.getBoundingClientRect();

		expect(leftFrame).toBeDefined();
		expect(rightFrame).toBeDefined();
		expect(Math.abs((leftFrame?.right ?? 0) - leftBox.right)).toBeLessThan(4);
		expect(Math.abs((rightFrame?.left ?? 0) - rightBox.left)).toBeLessThan(4);
		expect(Math.abs((leftFrame?.top ?? 0) - leftBox.top)).toBeLessThan(4);
		expect(leftBox.width).toBeGreaterThanOrEqual(44);
		expect(leftBox.height).toBeGreaterThanOrEqual(44);
		expect(Number(getComputedStyle(left).opacity)).toBeLessThan(0.6);
	});

	it('on a phone the trash leaves the page and sits beside the scrubber', async () => {
		await page.viewport(390, 844);
		renderList(['uno', 'due', 'tre']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();

		const trash = page.getByRole('button', { name: 'Elimina la pagina 1' });
		await expect.element(trash).toBeVisible();
		const box = trash.element().getBoundingClientRect();
		const text = page.getByRole('textbox', { name: 'Pagina 1' }).element().getBoundingClientRect();
		const slider = page.getByRole('slider').element().getBoundingClientRect();

		expect(box.width).toBeGreaterThanOrEqual(44);
		expect(box.height).toBeGreaterThanOrEqual(44);
		expect(box.top).toBeGreaterThan(text.bottom);
		expect(Math.abs(box.top + box.height / 2 - (slider.top + slider.height / 2))).toBeLessThan(8);

		await trash.click();
		await expect
			.element(page.getByRole('heading', { name: 'Eliminare la pagina 1?' }))
			.toBeVisible();
	});

	it('has no trash button on a page that does not exist yet', async () => {
		renderList(['uno']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();

		await expect.element(page.getByRole('button', { name: 'Elimina la pagina 1' })).toBeVisible();
		await expect
			.element(page.getByRole('button', { name: 'Elimina la pagina 2' }))
			.not.toBeInTheDocument();
	});

	it('warns that the text of the page is lost before doing anything', async () => {
		renderList(['uno', 'due', 'tre']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toBeVisible();

		await page.getByRole('button', { name: 'Elimina la pagina 2' }).click();

		await expect
			.element(page.getByRole('heading', { name: 'Eliminare la pagina 2?' }))
			.toBeVisible();
		await expect.element(page.getByText('tutto il testo scritto in questa pagina.')).toBeVisible();
		await expect
			.element(page.getByText('Non si può annullare e non c’è modo di recuperare i dati.'))
			.toBeVisible();
		expect(api.DELETE).not.toHaveBeenCalled();
	});

	it('keeps the page when cancelled', async () => {
		renderList(['uno', 'due', 'tre']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toBeVisible();
		await page.getByRole('button', { name: 'Elimina la pagina 2' }).click();

		await page.getByRole('dialog').getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		expect(api.DELETE).not.toHaveBeenCalled();
	});

	it('deletes the page and the next ones slide back into the view', async () => {
		const server = renderList(['uno', 'due', 'tre']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toBeVisible();
		await page.getByRole('button', { name: 'Elimina la pagina 2' }).click();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		expect(api.DELETE.mock.calls[0]?.[0]).toBe('/pages/{id}');
		expect(server.ids()).toEqual(['p1', 'p3']);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toHaveTextContent('tre');
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toHaveTextContent('uno');
	});

	it('follows the view of the address after a pending deletion has finished', async () => {
		const server = renderList(['uno', 'due', 'tre', 'quattro', 'cinque', 'sei', 'sette']);
		const remove = api.DELETE.getMockImplementation();
		let release: () => void = () => undefined;
		api.DELETE.mockImplementation(
			(path: unknown, init: unknown) =>
				new Promise((resolve) => {
					release = () => {
						resolve(remove?.(path, init));
					};
				})
		);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toBeVisible();
		await page.getByRole('button', { name: 'Elimina la pagina 2' }).click();
		await page.getByRole('button', { name: 'Elimina per sempre' }).click();
		await vi.waitFor(() => {
			expect(api.DELETE).toHaveBeenCalledTimes(1);
		});

		setPageUrl('http://localhost/diaries/d1?vista=3');
		await settle();
		release();

		await expect.element(page.getByRole('textbox', { name: 'Pagina 5' })).toHaveTextContent('sei');
		expect(server.ids()).toEqual(['p1', 'p3', 'p4', 'p5', 'p6', 'p7']);
	});

	it('stays open with the problem when the server refuses', async () => {
		renderList(['uno', 'due']);
		api.DELETE.mockResolvedValue({ error: { status: 500 }, response: { ok: false } });
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toBeVisible();
		await page.getByRole('button', { name: 'Elimina la pagina 2' }).click();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.element(page.getByRole('alert')).toBeVisible();
		await expect.element(page.getByRole('dialog')).toBeVisible();
		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toHaveTextContent('due');
	});
});

describe('every screen', () => {
	for (const width of [320, 390, 768, 1280, 3440]) {
		it(`has no horizontal scroll at ${String(width)} px`, async () => {
			await page.viewport(width, 900);
			mockServer(23);
			renderPage(23);

			await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();
			expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
		});
	}

	it('shows one page on a phone and two on a computer', async () => {
		await page.viewport(390, 900);
		mockServer(23);
		renderPage(23);

		await expect.element(page.getByText('Pagina 1 di 23')).toBeVisible();
		expect(page.getByRole('textbox', { name: /^Pagina \d+$/ }).elements()).toHaveLength(1);
	});

	it('reaches the next page one at a time on a phone', async () => {
		await page.viewport(390, 900);
		mockServer(23);
		renderPage(23);

		await page.getByRole('button', { name: 'Pagine successive' }).click();
		await expect.element(page.getByText('Pagina 2 di 23')).toBeVisible();
		await expect
			.element(page.getByRole('textbox', { name: 'Pagina 2' }))
			.toHaveTextContent('Testo della pagina 2');
	});

	it('moves the bar one page at a time on a phone and by spreads on a computer', async () => {
		await page.viewport(390, 900);
		mockServer(23);
		renderPage(23);

		const slider = page.getByRole('slider', { name: 'Sfoglia le pagine' });
		await expect.element(slider).toHaveAttribute('max', '23');
		slider.element().focus();
		await userEvent.keyboard('{ArrowRight}');
		await expect.element(page.getByText('Pagina 2 di 23')).toBeVisible();
	});

	it('keeps the corners of a phone at least 44 px wide', async () => {
		await page.viewport(390, 900);
		mockServer(23);
		renderPage(23, 2);

		const boxes = ['Pagine precedenti', 'Pagine successive'].map((name) =>
			page.getByRole('button', { name }).element().getBoundingClientRect()
		);

		expect(Math.min(...boxes.flatMap((box) => [box.width, box.height]))).toBeGreaterThanOrEqual(44);
	});
});

describe('blank content', () => {
	it('treats a stored empty document as a blank page', async () => {
		mockServer(2);
		api.GET.mockResolvedValue({
			data: {
				list: [
					{ id: 'p1', pageNumber: 1, content: doc('Uno') },
					{ id: 'p2', pageNumber: 2, content: emptyDoc() }
				],
				totalRows: 2
			}
		});
		renderPage(2);

		await expect.element(page.getByRole('textbox', { name: 'Pagina 2' })).toBeVisible();
	});
});

describe('leaving the page', () => {
	interface Navigation {
		type: string;
		cancel: () => void;
	}

	function navigate(type: string): ReturnType<typeof vi.fn<() => void>> {
		const cancel = vi.fn<() => void>();
		const guard = navigation.beforeNavigate.mock.calls.at(-1)?.[0] as
			((navigation: Navigation) => void) | undefined;
		guard?.({ type, cancel });
		return cancel;
	}

	async function saveNow(): Promise<void> {
		await page.getByRole('button', { name: 'Salva', exact: true }).click();
	}

	it('asks the browser to confirm closing the tab only while there is unsaved text', async () => {
		mockServer(23);
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		const event = new Event('beforeunload', { cancelable: true });

		window.dispatchEvent(event);

		expect(event.defaultPrevented).toBe(true);
	});

	it('lets the browser close the tab when everything is saved', async () => {
		mockServer(23);
		renderPage(23);
		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();
		const event = new Event('beforeunload', { cancelable: true });

		window.dispatchEvent(event);

		expect(event.defaultPrevented).toBe(false);
	});

	it('saves the page when the tab goes to the background', async () => {
		mockServer(23);
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		Object.defineProperty(document, 'visibilityState', {
			configurable: true,
			get: () => 'hidden'
		});

		document.dispatchEvent(new Event('visibilitychange'));

		await vi.waitFor(() => {
			expect(putCalls()).toHaveLength(1);
		});
	});

	it('does not save when the tab becomes visible again', async () => {
		mockServer(23);
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');

		document.dispatchEvent(new Event('visibilitychange'));
		await settle();

		expect(putCalls()).toHaveLength(0);
	});

	it('cancels a link navigation while a save failure is shown', async () => {
		mockServer(23);
		api.PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await saveNow();
		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('La pagina 1 non è stata salvata');

		const cancel = navigate('link');

		expect(cancel).toHaveBeenCalledOnce();
	});

	it('cancels a link navigation while a conflict is shown', async () => {
		mockServer(23);
		api.PUT.mockResolvedValue({
			error: { title: 'CONFLICT', status: 409 },
			response: { status: 409 }
		});
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await saveNow();
		await expect.element(page.getByRole('alert')).toHaveTextContent('modificata altrove');

		const cancel = navigate('link');

		expect(cancel).toHaveBeenCalledOnce();
	});

	it('lets the back button work even while a save failure is shown', async () => {
		mockServer(23);
		api.PUT.mockResolvedValue({ error: { title: 'Rete assente' }, response: { status: 500 } });
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await saveNow();
		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('La pagina 1 non è stata salvata');

		const cancel = navigate('popstate');

		expect(cancel).not.toHaveBeenCalled();
	});

	it('cancels a link navigation while the overflow of a page is held back', async () => {
		mockServer(23);
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 2' }).click();
		api.GET.mockRejectedValue(new TypeError('Failed to fetch'));
		await userEvent.keyboard('{Control>}{End}{/Control}');
		await typeLines(enteredLines('nuova', 1, 16));
		await expect.element(page.getByText(/Non riesco ad aprire la pagina 3/)).toBeVisible();

		const cancel = navigate('link');

		expect(cancel).toHaveBeenCalledOnce();
	});

	it('saves the pending text before the app leaves for another page', async () => {
		mockServer(23);
		preferences.value = { ...DEFAULT_PREFERENCES, pageTurn: false, autoSave: false };
		renderPage(23);
		await page.getByRole('textbox', { name: 'Pagina 1' }).click();
		await userEvent.keyboard('!');
		await expect.element(page.getByText('Non salvato', { exact: true })).toBeVisible();
		const leaving = navigation.onNavigate.mock.calls.at(-1)?.[0] as
			(() => Promise<void>) | undefined;

		await required(leaving)();

		expect(JSON.stringify(putCalls()[0]?.[1])).toContain('Testo della pagina 1!');
	});

	it('lets a link navigation go through when everything is saved', async () => {
		mockServer(23);
		renderPage(23);
		await expect.element(page.getByText('Salvato', { exact: true })).toBeVisible();

		const cancel = navigate('link');

		expect(cancel).not.toHaveBeenCalled();
	});
});

describe('bookmark', () => {
	const marked: Bookmark = { pageId: 'p3', pageNumber: 3 };

	function ribbon(number: number): ReturnType<typeof page.getByRole> {
		return page.getByRole('button', { name: `Segnalibro, pagina ${String(number)}` });
	}

	it('puts a ribbon on each written page of the spread, not pressed', async () => {
		mockServer(4);
		renderPage(4);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();

		await expect.element(ribbon(1)).toHaveAttribute('aria-pressed', 'false');
		await expect.element(ribbon(2)).toHaveAttribute('aria-pressed', 'false');
	});

	it('has no ribbon on a page that does not exist yet', async () => {
		mockServer(1);
		renderPage(1);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();

		await expect.element(ribbon(1)).toBeVisible();
		await expect.element(ribbon(2)).not.toBeInTheDocument();
	});

	it('shows pressed the ribbon of the bookmarked page', async () => {
		mockServer(6);
		renderPage(6, 2, [], marked);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 3' })).toBeVisible();

		await expect.element(ribbon(3)).toHaveAttribute('aria-pressed', 'true');
		await expect.element(ribbon(4)).toHaveAttribute('aria-pressed', 'false');
	});

	it('keeps at least 44 px to touch', async () => {
		mockServer(4);
		renderPage(4);
		await expect.element(ribbon(2)).toBeVisible();

		const box = ribbon(2).element().getBoundingClientRect();

		expect(box.width).toBeGreaterThanOrEqual(44);
		expect(box.height).toBeGreaterThanOrEqual(44);
	});

	it('marks the page when the ribbon is pressed', async () => {
		mockServer(4);
		api.PUT.mockResolvedValue({ data: { pageId: 'p2', pageNumber: 2 } });
		renderPage(4);
		await expect.element(ribbon(2)).toBeVisible();

		await ribbon(2).click();

		await expect.element(ribbon(2)).toHaveAttribute('aria-pressed', 'true');
		expect(api.PUT).toHaveBeenCalledWith('/diaries/{id}/bookmark', {
			params: { path: { id: 'd1' } },
			body: { pageId: 'p2' }
		});
	});

	it('moves the bookmark from the page that had it', async () => {
		mockServer(6);
		api.PUT.mockResolvedValue({ data: { pageId: 'p4', pageNumber: 4 } });
		renderPage(6, 2, [], marked);
		await expect.element(ribbon(4)).toBeVisible();

		await ribbon(4).click();

		await expect.element(ribbon(4)).toHaveAttribute('aria-pressed', 'true');
		await expect.element(ribbon(3)).toHaveAttribute('aria-pressed', 'false');
	});

	it('takes the bookmark off when the pressed ribbon is pressed again', async () => {
		mockServer(6);
		api.DELETE.mockResolvedValue({ response: { ok: true } });
		renderPage(6, 2, [], marked);
		await expect.element(ribbon(3)).toBeVisible();

		await ribbon(3).click();

		await expect.element(ribbon(3)).toHaveAttribute('aria-pressed', 'false');
		expect(api.DELETE).toHaveBeenCalledWith('/diaries/{id}/bookmark', {
			params: { path: { id: 'd1' } }
		});
	});

	it('says what went wrong and keeps the ribbon as it was when the server refuses', async () => {
		mockServer(4);
		api.PUT.mockResolvedValue({ error: { title: 'Page not found' }, response: { ok: false } });
		renderPage(4);
		await expect.element(ribbon(2)).toBeVisible();

		await ribbon(2).click();

		await expect.element(page.getByText('Pagina non trovata')).toBeVisible();
		await expect.element(ribbon(2)).toHaveAttribute('aria-pressed', 'false');
	});

	it('is on the page too on a phone', async () => {
		await page.viewport(390, 844);
		mockServer(4);
		renderPage(4);
		await expect.element(page.getByRole('textbox', { name: 'Pagina 1' })).toBeVisible();

		await expect.element(ribbon(1)).toBeVisible();
		await expect.element(ribbon(2)).not.toBeInTheDocument();
	});
});

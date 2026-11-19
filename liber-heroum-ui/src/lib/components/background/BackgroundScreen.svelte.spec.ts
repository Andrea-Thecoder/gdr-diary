import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';

type Call = (...args: unknown[]) => Promise<unknown>;

const { api, navigation, startDownload } = vi.hoisted(() => ({
	api: { GET: vi.fn<Call>(), PUT: vi.fn<Call>() },
	startDownload: vi.fn<(url: string, fileName?: string) => void>(),
	navigation: {
		afterNavigate: vi.fn(),
		beforeNavigate: vi.fn(),
		onNavigate: vi.fn(),
		goto: vi.fn()
	}
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('$app/navigation', () => navigation);
vi.mock('#lib/sheets/download.js', () => ({ startDownload }));

import { doc } from '#lib/book/test-support.js';
import { settle } from '#lib/test-support.js';
import { emptyDoc } from '#lib/book/pages.js';
import BackgroundScreen from './BackgroundScreen.svelte';

const character = {
	id: 'c1',
	kind: 'PLAYER',
	name: 'Alira dei Salici',
	campaignName: 'Il Nord Spezzato'
};

function renderScreen(
	initial: { content: unknown; version: number },
	extra: Record<string, unknown> = {}
): void {
	render(BackgroundScreen, { character, initial, ...extra } as never);
}

function writtenStory(): { content: unknown; version: number } {
	return { content: doc('Nata tra i salici.'), version: 3 };
}

function emptyStory(): { content: unknown; version: number } {
	return { content: emptyDoc(), version: 0 };
}

beforeEach(async () => {
	api.GET.mockReset();
	api.PUT.mockReset();
	startDownload.mockReset();
	api.PUT.mockImplementation((_path: unknown, init: unknown) => {
		const body = (init as { body: { content: unknown; version: number } }).body;
		return Promise.resolve({
			data: { content: body.content, version: body.version + 1 },
			response: { status: 200 }
		});
	});
	await page.viewport(1280, 900);
});

describe('story screen, reading', () => {
	it('shows the title of the page', async () => {
		renderScreen(writtenStory());

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Il Background');
	});

	it('shows the name of the character and the campaign under the title', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByText('Il Nord Spezzato')).toBeVisible();
		const line = page.getByText('Il Nord Spezzato').element().parentElement;
		expect(line?.textContent).toContain('Alira dei Salici');
	});

	it('names the page after the character for screen readers', async () => {
		renderScreen(writtenStory());

		await expect
			.element(page.getByRole('article', { name: 'Il background di Alira dei Salici' }))
			.toBeVisible();
	});

	it('shows how full the scroll is', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByText('Il rotolo è pieno al 1%')).toBeVisible();
		await expect.element(page.getByText('50.000 caratteri al massimo')).toBeVisible();
	});

	it('says the scroll is still blank when nothing is written', async () => {
		renderScreen(emptyStory());

		await expect.element(page.getByText('Il rotolo è ancora bianco')).toBeVisible();
	});

	it('shows the text of the story', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByText('Nata tra i salici.')).toBeVisible();
	});

	it('starts locked so that the text cannot be changed by mistake', async () => {
		renderScreen(writtenStory());

		await expect
			.element(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }))
			.toHaveAttribute('contenteditable', 'false');
	});

	it('offers to edit and not to finish', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByRole('button', { name: 'Modifica' })).toBeVisible();
		expect(page.getByRole('button', { name: 'Fatto' }).elements()).toHaveLength(0);
	});

	it('does not show the counter or the toolbar while reading', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		expect(document.querySelector('[data-background-counter]')).toBeNull();
		expect(page.getByRole('toolbar').elements()).toHaveLength(0);
	});

	it('goes back to the character', async () => {
		renderScreen(writtenStory());

		await expect
			.element(page.getByRole('link', { name: 'Alira dei Salici' }))
			.toHaveAttribute('href', '/characters/c1');
	});

	it('asks the server for the PDF of the story', async () => {
		api.GET.mockResolvedValue({
			data: new Blob(['%PDF-'], { type: 'application/pdf' }),
			response: new Response(null, {
				headers: { 'Content-Disposition': 'attachment; filename="background-alira.pdf"' }
			})
		});
		renderScreen(writtenStory());

		await userEvent.click(page.getByRole('button', { name: 'Scarica PDF' }));

		await expect
			.poll(() => api.GET.mock.calls.at(-1))
			.toEqual([
				'/characters/{characterId}/background/pdf',
				{ params: { path: { characterId: 'c1' } }, parseAs: 'blob' }
			]);
	});

	it('downloads the PDF with the name the server gives', async () => {
		api.GET.mockResolvedValue({
			data: new Blob(['%PDF-'], { type: 'application/pdf' }),
			response: new Response(null, {
				headers: { 'Content-Disposition': 'attachment; filename="background-alira.pdf"' }
			})
		});
		renderScreen(writtenStory());

		await userEvent.click(page.getByRole('button', { name: 'Scarica PDF' }));

		await expect.poll(() => startDownload.mock.calls.at(-1)?.[1]).toBe('background-alira.pdf');
	});

	it('does not offer the PDF of a story that is still empty', async () => {
		renderScreen(emptyStory());

		await expect.element(page.getByRole('button', { name: 'Scarica PDF' })).toBeDisabled();
	});

	it('saves what is written before asking for the PDF', async () => {
		api.GET.mockResolvedValue({
			data: new Blob(['%PDF-'], { type: 'application/pdf' }),
			response: new Response(null)
		});
		renderScreen({ content: doc('Nata'), version: 3 });
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		await userEvent.click(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }));
		await userEvent.keyboard('{Control>}{End}{/Control} tra i salici.');

		await userEvent.click(page.getByRole('button', { name: 'Scarica PDF' }));

		await expect.poll(() => startDownload.mock.calls.length).toBe(1);
		expect(api.PUT).toHaveBeenCalledTimes(1);
	});

	it('draws a roll above and a roll below the page', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		expect(document.querySelector('[data-roll="top"]')).not.toBeNull();
		expect(document.querySelector('[data-roll="bottom"]')).not.toBeNull();
	});
});

describe('story screen, writing', () => {
	it('opens ready to write when the story is empty', async () => {
		renderScreen(emptyStory());

		await expect
			.element(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }))
			.toHaveAttribute('contenteditable', 'true');
		await expect.element(page.getByRole('button', { name: 'Fatto' })).toBeVisible();
	});

	it('invites to write when the story is empty', async () => {
		renderScreen(emptyStory());

		await expect
			.element(page.getByText(/Comincia da qui\. Chi è Alira dei Salici\?/))
			.toBeVisible();
	});

	it('unlocks the text when asked', async () => {
		renderScreen(writtenStory());

		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));

		await expect
			.element(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }))
			.toHaveAttribute('contenteditable', 'true');
	});

	it('shows how many characters are used', async () => {
		renderScreen(writtenStory());

		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));

		await expect.element(page.getByText('18 / 50.000 caratteri')).toBeVisible();
	});

	it('saves what is written with the version that was read', async () => {
		renderScreen({ content: doc('Nata'), version: 3 });
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		await userEvent.click(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }));
		await userEvent.keyboard('{Control>}{End}{/Control} tra i salici.');

		await userEvent.click(page.getByRole('button', { name: 'Salva', exact: true }));

		await expect.element(page.getByRole('status')).toHaveTextContent('Salvato');
		const [path, init] = api.PUT.mock.calls.at(-1) ?? [];
		expect(path).toBe('/characters/{characterId}/background');
		expect(init).toMatchObject({ params: { path: { characterId: 'c1' } }, body: { version: 3 } });
		expect(JSON.stringify(init)).toContain('Nata tra i salici.');
	});

	it('locks the text again when done', async () => {
		renderScreen(writtenStory());
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));

		await userEvent.click(page.getByRole('button', { name: 'Fatto' }));

		await expect
			.element(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }))
			.toHaveAttribute('contenteditable', 'false');
	});

	it('stays unlocked when done but the story cannot be saved', async () => {
		api.PUT.mockResolvedValue({ error: { title: 'BAD' }, response: { status: 400 } });
		renderScreen({ content: doc('Nata'), version: 3 });
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		await userEvent.click(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }));
		await userEvent.keyboard('{Control>}{End}{/Control}!');

		await userEvent.click(page.getByRole('button', { name: 'Fatto' }));

		await expect.element(page.getByRole('button', { name: 'Fatto' })).toBeVisible();
		await expect.element(page.getByText('Il Background non è stato salvato.')).toBeVisible();
	});

	it('asks which version to keep when the story changed elsewhere', async () => {
		api.PUT.mockResolvedValue({ error: { title: 'CONFLICT' }, response: { status: 409 } });
		renderScreen({ content: doc('Nata'), version: 3 });
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		await userEvent.click(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }));
		await userEvent.keyboard('{Control>}{End}{/Control}!');

		await userEvent.click(page.getByRole('button', { name: 'Salva', exact: true }));

		await expect
			.element(page.getByRole('button', { name: 'Usa la versione salvata' }))
			.toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Tieni la mia versione' })).toBeVisible();
	});

	it('shows the text found in a draft and says so', async () => {
		const draft = {
			diaryId: 'background:c1',
			page: 1,
			pageId: null,
			baseVersion: 3,
			content: doc('Nata tra i salici, poi partì.'),
			savedAt: 1
		};
		renderScreen(writtenStory(), { drafts: [draft] });

		await expect.element(page.getByText('Nata tra i salici, poi partì.')).toBeVisible();
		await expect.element(page.getByText(/Ho ritrovato il testo/)).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Fatto' })).toBeVisible();
	});
});

describe('story screen, notices and shortcuts', () => {
	async function startEditing(initial = { content: doc('Nata'), version: 3 }): Promise<void> {
		renderScreen(initial);
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		await userEvent.click(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }));
		await userEvent.keyboard('{Control>}{End}{/Control}!');
	}

	it('saves at once with Ctrl+S', async () => {
		await startEditing();

		await userEvent.keyboard('{Control>}s{/Control}');

		await expect.element(page.getByRole('status')).toHaveTextContent('Salvato');
		expect(api.PUT).toHaveBeenCalledTimes(1);
	});

	it('tells the user the text saves itself and shows the shortcut while writing', async () => {
		await startEditing();

		await expect
			.element(page.getByText('Il testo si salva da solo dopo qualche secondo di pausa.'))
			.toBeVisible();
		await expect.element(page.getByText('per salvare subito')).toBeVisible();
	});

	it('does not talk about saving while only reading', async () => {
		renderScreen(writtenStory());

		await expect.element(page.getByRole('button', { name: 'Modifica' })).toBeVisible();
		expect(page.getByText('per salvare subito').elements()).toHaveLength(0);
	});

	it('says the story was not saved and offers to try again now', async () => {
		api.PUT.mockResolvedValue({ error: { title: 'BAD' }, response: { status: 500 } });
		await startEditing();

		await userEvent.click(page.getByRole('button', { name: 'Salva', exact: true }));

		await expect.element(page.getByText('Il Background non è stato salvato.')).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Riprova ora' })).toBeVisible();
	});

	it('says the text stays on the device when the network is down', async () => {
		api.PUT.mockRejectedValue(new TypeError('Failed to fetch'));
		await startEditing();

		await userEvent.click(page.getByRole('button', { name: 'Salva', exact: true }));

		await expect.element(page.getByText('Sei offline.')).toBeVisible();
		await expect.element(page.getByText(/il testo resta in questo dispositivo/)).toBeVisible();
	});

	it('says the scroll is full and links back to the diaries', async () => {
		renderScreen({ content: doc('a'.repeat(50000)), version: 3 });

		await expect
			.element(page.getByText('Il rotolo è pieno: 50.000 caratteri su 50.000.'))
			.toBeVisible();
		await expect
			.element(page.getByRole('link', { name: 'Apri i diari' }))
			.toHaveAttribute('href', '/characters/c1');
	});
});

describe('story screen near the limit', () => {
	async function writeAtTheEnd(initial: string): Promise<void> {
		renderScreen({ content: doc(initial), version: 3 });
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		const editor = page
			.getByRole('textbox', { name: 'La storia di Alira dei Salici' })
			.element() as HTMLElement;
		editor.focus();
		window.getSelection()?.selectAllChildren(editor);
		window.getSelection()?.collapseToEnd();
		await settle();
	}

	it('keeps the letters that still fit when typing past the limit', async () => {
		await writeAtTheEnd('abcd '.repeat(9_999));

		await userEvent.keyboard('bcdefghij');

		await expect.element(page.getByText('50.000 / 50.000 caratteri')).toBeVisible();
	});

	it('says the scroll is full once the last letter fits', async () => {
		await writeAtTheEnd('abcd '.repeat(9_999));

		await userEvent.keyboard('bcdefghij');

		await expect
			.element(page.getByText('Il rotolo è pieno: 50.000 caratteri su 50.000.'))
			.toBeVisible();
	});

	it('says how much room is left when a pasted text does not fit', async () => {
		await writeAtTheEnd('a'.repeat(49_000));
		const editor = page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }).element();
		const data = new DataTransfer();
		data.setData('text/plain', 'b'.repeat(2_000));

		editor.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true }));

		await expect
			.element(page.getByText('Il testo non ci sta: restano 1.000 caratteri.'))
			.toBeVisible();
	});

	it('leaves the text as it was when a pasted text does not fit', async () => {
		await writeAtTheEnd('a'.repeat(49_000));
		const editor = page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }).element();
		const data = new DataTransfer();
		data.setData('text/plain', 'b'.repeat(2_000));

		editor.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true }));

		await expect.element(page.getByText('49.000 / 50.000 caratteri')).toBeVisible();
	});
});

describe('story screen, full scroll', () => {
	function arrive(from: URL | null): void {
		const callback = navigation.afterNavigate.mock.calls.at(-1)?.[0] as (event: unknown) => void;
		callback({ from: from === null ? null : { url: from } });
	}

	it('goes back to where the user came from with "Apri i diari"', async () => {
		const back = vi.spyOn(history, 'back').mockImplementation(() => undefined);
		renderScreen({ content: doc('a'.repeat(50000)), version: 3 });
		arrive(new URL('http://localhost/characters/c1'));

		await userEvent.click(page.getByRole('link', { name: 'Apri i diari' }));

		expect(back).toHaveBeenCalledTimes(1);
		back.mockRestore();
	});

	it('keeps the link to the character for who opened the page directly', async () => {
		renderScreen({ content: doc('a'.repeat(50000)), version: 3 });
		arrive(null);

		await expect
			.element(page.getByRole('link', { name: 'Apri i diari' }))
			.toHaveAttribute('href', '/characters/c1');
	});
});

describe('story screen, steady header', () => {
	function headerHeight(): number {
		const bar = document.querySelector('[data-background-bar]');
		return (
			(bar?.parentElement?.getBoundingClientRect().top ?? 0) +
			(bar?.getBoundingClientRect().top ?? 0)
		);
	}

	function counterWidth(): number {
		return document.querySelector('[data-background-counter]')?.getBoundingClientRect().width ?? 0;
	}

	function controlsBottom(): number {
		return document.querySelector('[data-background-counter]')?.getBoundingClientRect().bottom ?? 0;
	}

	it.each([[1000], [1326], [800]])(
		'keeps the counter and the page where they are while the state changes at %i px',
		async (width) => {
			await page.viewport(width, 900);
			renderScreen({ content: doc('Nata'), version: 3 });
			await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
			await expect.element(page.getByRole('status')).toHaveTextContent('Salvato');
			const savedBar = headerHeight();
			const savedWidth = counterWidth();
			const savedBottom = controlsBottom();
			await userEvent.click(page.getByRole('textbox', { name: 'La storia di Alira dei Salici' }));

			await userEvent.keyboard('{Control>}{End}{/Control}!');

			await expect.element(page.getByRole('status')).toHaveTextContent('Da salvare');
			expect([headerHeight(), counterWidth(), controlsBottom()]).toEqual([
				savedBar,
				savedWidth,
				savedBottom
			]);
		}
	);

	it('keeps the counter as wide with a long text as with a short one', async () => {
		await page.viewport(1000, 900);
		const short = render(BackgroundScreen, {
			character,
			initial: { content: doc('Nata'), version: 3 }
		} as never);
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));
		const shortWidth = counterWidth();
		await short.unmount();

		renderScreen({ content: doc('a'.repeat(20_000)), version: 3 });
		await userEvent.click(page.getByRole('button', { name: 'Modifica' }));

		expect(counterWidth()).toBe(shortWidth);
	});
});

describe('story screen on small screens', () => {
	it.each([[320], [390], [820]])('does not scroll sideways at %i px', async (width) => {
		await page.viewport(width, 800);
		renderScreen(emptyStory());

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		const root = document.documentElement;
		expect(root.scrollWidth - root.clientWidth).toBe(0);
	});

	it('has buttons as tall as a finger', async () => {
		await page.viewport(360, 740);
		renderScreen(writtenStory());

		await expect.element(page.getByRole('button', { name: 'Modifica' })).toBeVisible();
		const heights = page
			.getByRole('button')
			.elements()
			.map((button) => button.getBoundingClientRect().height);
		expect(Math.min(...heights)).toBeGreaterThanOrEqual(44);
	});

	it('uses smaller rolls on a phone than on a wide screen', async () => {
		await page.viewport(390, 800);
		renderScreen(writtenStory());

		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
		const roll = document.querySelector<HTMLElement>('[data-roll="top"]');
		expect(Number.parseFloat(roll?.style.height ?? '99')).toBeLessThanOrEqual(66);
	});
});

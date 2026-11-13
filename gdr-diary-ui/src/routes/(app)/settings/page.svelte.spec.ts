import { page } from 'vitest/browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../layout.css';

const { api } = vi.hoisted(() => ({
	api: { PATCH: vi.fn<(...args: unknown[]) => Promise<unknown>>() }
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => api }));
vi.mock('$app/state', () => ({ page: { url: { pathname: '/settings' } } }));

import { preferences } from '#lib/settings/preferences.svelte.js';
import { required } from '#lib/test-support.js';
import { DEFAULT_PREFERENCES } from '#lib/settings/preferences.js';
import Page from './+page.svelte';

describe('settings page', () => {
	function clearCookie(): void {
		document.cookie = 'gdr-theme=; Path=/; Max-Age=0';
	}

	function storedTheme(): string | null {
		return /(?:^|; )gdr-theme=([^;]*)/.exec(document.cookie)?.[1] ?? null;
	}

	beforeEach(async () => {
		api.PATCH.mockReset();
		api.PATCH.mockResolvedValue({ data: { payload: { theme: 'viola' } } });
		clearCookie();
		preferences.value = { ...DEFAULT_PREFERENCES };
		await page.viewport(1280, 800);
	});

	afterEach(() => {
		clearCookie();
		delete document.documentElement.dataset['theme'];
	});

	it('shows the title and the three theme choices', async () => {
		render(Page);

		await expect.element(page.getByRole('heading', { level: 1 })).toHaveTextContent('Impostazioni');
		await expect.element(page.getByRole('radio', { name: /sistema/ })).toBeChecked();
		await expect.element(page.getByRole('radio', { name: /chiaro/ })).toBeVisible();
		await expect.element(page.getByRole('radio', { name: /scuro/ })).toBeVisible();
	});

	it('switches to the dark theme and remembers it', async () => {
		render(Page);

		await page.getByText('scuro', { exact: true }).click();

		await expect.element(page.getByRole('radio', { name: /scuro/ })).toBeChecked();
		expect(document.documentElement.dataset['theme']).toBe('viola');
		expect(storedTheme()).toBe('viola');
	});

	it('switches to the light theme', async () => {
		render(Page);

		await page.getByText('chiaro', { exact: true }).click();

		expect(document.documentElement.dataset['theme']).toBe('pergamena');
		expect(storedTheme()).toBe('pergamena');
	});

	it('goes back to following the device', async () => {
		document.cookie = 'gdr-theme=viola; Path=/';
		render(Page);
		await expect.element(page.getByRole('radio', { name: /scuro/ })).toBeChecked();

		await page.getByText('sistema', { exact: true }).click();

		expect(storedTheme()).toBeNull();
	});

	for (const width of [280, 320, 390, 768, 1280, 3440]) {
		it(`fits a ${String(width)} px wide screen`, async () => {
			await page.viewport(width, 800);
			render(Page);
			await expect.element(page.getByRole('radio', { name: /scuro/ })).toBeVisible();

			expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
		});
	}

	it('does not save the theme until the settings are saved', async () => {
		render(Page);

		await page.getByText('scuro', { exact: true }).click();

		await expect.element(page.getByRole('button', { name: 'Salva le impostazioni' })).toBeVisible();
		expect(api.PATCH).not.toHaveBeenCalled();
	});

	it('saves the chosen theme on the account with the other settings', async () => {
		render(Page);
		await page.getByText('scuro', { exact: true }).click();

		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.poll(() => api.PATCH.mock.calls.length).toBe(1);
		expect(api.PATCH.mock.calls[0]?.[0]).toBe('/me/settings');
		expect((api.PATCH.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			settings: { theme: 'viola' }
		});
		await expect
			.element(page.getByRole('button', { name: 'Salva le impostazioni' }))
			.not.toBeInTheDocument();
		expect(storedTheme()).toBe('viola');
	});

	it('saves the system choice too, so other devices follow it', async () => {
		render(Page);
		await page.getByText('scuro', { exact: true }).click();
		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();
		await expect.poll(() => api.PATCH.mock.calls.length).toBe(1);
		await expect
			.element(page.getByRole('button', { name: 'Salva le impostazioni' }))
			.not.toBeInTheDocument();

		await page.getByText('sistema', { exact: true }).click();
		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.poll(() => api.PATCH.mock.calls.length).toBe(2);
		expect((api.PATCH.mock.calls[1]?.[1] as { body: unknown }).body).toEqual({
			settings: { theme: 'system' }
		});
	});

	it('sends the theme together with the other changed keys', async () => {
		api.PATCH.mockResolvedValue({ data: { payload: { theme: 'pergamena', fontSize: 20 } } });
		render(Page);
		await page.getByText('chiaro', { exact: true }).click();
		await page.getByLabelText('Dimensione del testo').fill('20');

		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.poll(() => api.PATCH.mock.calls.length).toBe(1);
		expect((api.PATCH.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			settings: { fontSize: 20, theme: 'pergamena' }
		});
	});

	it('puts the previous theme back with Annulla', async () => {
		document.cookie = 'gdr-theme=pergamena; Path=/';
		render(Page);
		await expect.element(page.getByRole('radio', { name: /chiaro/ })).toBeChecked();
		await page.getByText('scuro', { exact: true }).click();
		expect(document.documentElement.dataset['theme']).toBe('viola');

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('radio', { name: /chiaro/ })).toBeChecked();
		expect(document.documentElement.dataset['theme']).toBe('pergamena');
		expect(storedTheme()).toBe('pergamena');
		expect(api.PATCH).not.toHaveBeenCalled();
		await expect
			.element(page.getByRole('button', { name: 'Salva le impostazioni' }))
			.not.toBeInTheDocument();
	});

	it('goes back to following the device with Annulla when that was the first choice', async () => {
		render(Page);
		await page.getByText('scuro', { exact: true }).click();

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('radio', { name: /sistema/ })).toBeChecked();
		expect(storedTheme()).toBeNull();
	});

	it('puts the saved theme back when the page is left without saving', async () => {
		document.cookie = 'gdr-theme=pergamena; Path=/';
		const screen = render(Page);
		await page.getByText('scuro', { exact: true }).click();
		expect(storedTheme()).toBe('viola');

		screen.unmount();

		expect(storedTheme()).toBe('pergamena');
		expect(document.documentElement.dataset['theme']).toBe('pergamena');
		expect(api.PATCH).not.toHaveBeenCalled();
	});

	it('puts the saved theme back when the page is reloaded without saving', async () => {
		document.cookie = 'gdr-theme=pergamena; Path=/';
		render(Page);
		await page.getByText('scuro', { exact: true }).click();

		window.dispatchEvent(new Event('pagehide'));

		expect(storedTheme()).toBe('pergamena');
	});

	it('keeps the choice and says why when saving the theme fails', async () => {
		api.PATCH.mockResolvedValue({ error: { title: 'Settings too large', status: 400 } });
		render(Page);
		await page.getByText('scuro', { exact: true }).click();

		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Settings too large');
		await expect.element(page.getByRole('radio', { name: /scuro/ })).toBeChecked();
		expect(storedTheme()).toBe('viola');
	});

	it('shows the writing and appearance fields from the design', async () => {
		render(Page);

		await expect.element(page.getByRole('heading', { name: 'Scrittura' })).toBeVisible();
		await expect.element(page.getByLabelText('Font dei diari')).toBeVisible();
		await expect.element(page.getByLabelText('Dimensione del testo')).toBeVisible();
		await expect.element(page.getByRole('radio', { name: 'Normale' })).toBeChecked();
		await expect
			.element(page.getByRole('switch', { name: 'Salvataggio automatico' }))
			.toBeChecked();
		await expect.element(page.getByRole('switch', { name: 'Effetto sfoglio' })).toBeChecked();
		await expect
			.element(page.getByRole('switch', { name: 'Animazioni ridotte' }))
			.not.toBeChecked();
	});

	it('keeps the autosave settings in a General section above all the others', async () => {
		render(Page);

		const sections = [...document.querySelectorAll('form h2')].map((heading) =>
			heading.textContent.trim()
		);
		expect(sections).toEqual(['Generale', 'Scrittura', 'Aspetto']);
		const general = required(document.querySelector('#generale'), 'Sezione Generale');
		expect(general.querySelector('[role="switch"]')?.getAttribute('aria-labelledby')).toBeTruthy();
		await expect
			.element(page.getByRole('switch', { name: 'Salvataggio automatico' }))
			.toBeVisible();
		expect(
			general.contains(
				required(document.querySelector('[aria-labelledby$="-auto"]'), 'Salvataggio automatico')
			)
		).toBe(true);
	});

	it('shows the autosave wait in seconds, five by default', async () => {
		render(Page);

		await expect.element(page.getByLabelText('Attesa prima di salvare')).toHaveValue('5');
		await expect.element(page.getByText('5 s', { exact: true })).toBeVisible();
	});

	it('hides the autosave wait while automatic saving is off', async () => {
		render(Page);

		await page.getByRole('switch', { name: 'Salvataggio automatico' }).click();

		expect(page.getByLabelText('Attesa prima di salvare').elements()).toHaveLength(0);

		await page.getByRole('switch', { name: 'Salvataggio automatico' }).click();

		await expect.element(page.getByLabelText('Attesa prima di salvare')).toBeVisible();
	});

	it('saves the chosen autosave wait and applies it right away', async () => {
		api.PATCH.mockResolvedValue({ data: { payload: { autoSaveDelay: 8 } } });
		render(Page);

		await page.getByLabelText('Attesa prima di salvare').fill('8');
		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.poll(() => api.PATCH.mock.calls.length).toBe(1);
		expect((api.PATCH.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			settings: { autoSaveDelay: 8 }
		});
		expect(preferences.value.autoSaveDelay).toBe(8);
	});

	it('has no save bar until something changes', async () => {
		render(Page);

		expect(page.getByRole('button', { name: 'Salva le impostazioni' }).elements()).toHaveLength(0);

		await page.getByRole('switch', { name: 'Animazioni ridotte' }).click();

		await expect.element(page.getByRole('button', { name: 'Salva le impostazioni' })).toBeVisible();
	});

	it('puts the save bar after the last section, in the flow of the page', async () => {
		render(Page);

		await page.getByRole('switch', { name: 'Animazioni ridotte' }).click();

		await expect.element(page.getByRole('button', { name: 'Salva le impostazioni' })).toBeVisible();
		const form = required(document.querySelector('form'), 'Modulo');
		const bar = required(form.lastElementChild, 'Barra di salvataggio');
		const last = required(document.querySelector('#aspetto'), 'Ultima sezione');
		expect(getComputedStyle(bar).position).toBe('static');
		expect(bar.getBoundingClientRect().top).toBeGreaterThanOrEqual(
			last.getBoundingClientRect().bottom
		);
	});

	it('sends only the changed keys when saving', async () => {
		api.PATCH.mockResolvedValue({
			data: { payload: { fontSize: 20, lineSpacing: 'wide' } }
		});
		render(Page);
		await page.getByText('Ampia', { exact: true }).click();
		await page.getByLabelText('Dimensione del testo').fill('20');

		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.poll(() => api.PATCH.mock.calls.length).toBe(1);
		expect((api.PATCH.mock.calls[0]?.[1] as { body: unknown }).body).toEqual({
			settings: { lineSpacing: 'wide', fontSize: 20 }
		});
		expect(preferences.value.fontSize).toBe(20);
		expect(preferences.value.lineSpacing).toBe('wide');
		await expect
			.element(page.getByRole('button', { name: 'Salva le impostazioni' }))
			.not.toBeInTheDocument();
	});

	it('drops the changes with Annulla', async () => {
		render(Page);
		await page.getByRole('switch', { name: 'Effetto sfoglio' }).click();

		await page.getByRole('button', { name: 'Annulla' }).click();

		await expect.element(page.getByRole('switch', { name: 'Effetto sfoglio' })).toBeChecked();
		expect(api.PATCH).not.toHaveBeenCalled();
	});

	it('keeps the changes and says why when saving fails', async () => {
		api.PATCH.mockResolvedValue({ error: { title: 'Settings too large', status: 400 } });
		render(Page);
		await page.getByRole('switch', { name: 'Animazioni ridotte' }).click();

		await page.getByRole('button', { name: 'Salva le impostazioni' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Settings too large');
		await expect.element(page.getByRole('switch', { name: 'Animazioni ridotte' })).toBeChecked();
	});

	for (const width of [320, 390, 768, 1280]) {
		it(`has no horizontal scroll at ${String(width)} px`, async () => {
			await page.viewport(width, 800);
			render(Page);

			await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();
			expect(document.documentElement.scrollWidth - document.documentElement.clientWidth).toBe(0);
		});
	}
});

describe('settings page on tablets', () => {
	beforeEach(() => {
		preferences.value = { ...DEFAULT_PREFERENCES };
	});

	function index(): HTMLElement | null {
		return document.querySelector<HTMLElement>('nav[aria-label="In questa pagina"]');
	}

	it('puts the index above the form as a row of pills below 900 px', async () => {
		await page.viewport(768, 1024);
		render(Page);

		const nav = required(index(), 'Indice');
		const form = required(document.querySelector('form'), 'Modulo');
		const links = [...nav.querySelectorAll('a')];
		const tops = links.map((link) => Math.round(link.getBoundingClientRect().top));
		const heights = links.map((link) => link.getBoundingClientRect().height);
		expect(links.length).toBeGreaterThanOrEqual(2);
		expect(nav.getBoundingClientRect().bottom).toBeLessThanOrEqual(
			form.getBoundingClientRect().top
		);
		expect(
			Math.abs(nav.getBoundingClientRect().width - form.getBoundingClientRect().width)
		).toBeLessThan(2);
		expect(new Set(tops).size).toBe(1);
		expect(Math.min(...heights)).toBeGreaterThanOrEqual(44);
	});

	it('keeps the index as a column beside the form from 900 px', async () => {
		await page.viewport(1024, 800);
		render(Page);

		const nav = required(index(), 'Indice');
		const form = required(document.querySelector('form'), 'Modulo');
		const links = [...nav.querySelectorAll('a')];
		expect(nav.getBoundingClientRect().right).toBeLessThanOrEqual(
			form.getBoundingClientRect().left
		);
		const tops = links.map((link) => Math.round(link.getBoundingClientRect().top));
		expect(new Set(tops).size).toBe(links.length);
	});
});

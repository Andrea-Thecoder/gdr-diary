import { page, userEvent } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import PageTagDialog from './PageTagDialog.svelte';

function props(overrides: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		open: true,
		page: 5,
		tag: null,
		suggestions: [],
		onsubmit: vi.fn().mockReturnValue(null),
		onremove: vi.fn(),
		ontype: vi.fn(),
		onclose: vi.fn(),
		...overrides
	};
}

function nameField(): ReturnType<typeof page.getByRole> {
	return page.getByRole('textbox', { name: 'Nome del tag' });
}

describe('PageTagDialog', () => {
	it('is a form to add a tag to the page', async () => {
		render(PageTagDialog, props());

		await expect.element(page.getByRole('heading', { name: 'Nuovo tag' })).toBeVisible();
		await expect.element(page.getByText('Pagina 5')).toBeVisible();
		await expect.element(nameField()).toHaveValue('');
		expect(page.getByRole('button', { name: 'Togli il tag' }).elements()).toHaveLength(0);
	});

	it('limits the name to 30 characters', async () => {
		render(PageTagDialog, props());

		await expect.element(nameField()).toHaveAttribute('maxlength', '30');
	});

	it('sends the name and closes when the form is accepted', async () => {
		const onsubmit = vi.fn().mockReturnValue(null);
		const onclose = vi.fn();
		render(PageTagDialog, props({ onsubmit, onclose }));

		await nameField().fill('Erebor');
		await page.getByRole('button', { name: 'Salva' }).click();

		expect(onsubmit).toHaveBeenCalledWith('Erebor');
		expect(onclose).toHaveBeenCalledTimes(1);
	});

	it('sends the form with Enter', async () => {
		const onsubmit = vi.fn().mockReturnValue(null);
		render(PageTagDialog, props({ onsubmit }));

		await nameField().fill('Erebor');
		await userEvent.keyboard('{Enter}');

		expect(onsubmit).toHaveBeenCalledWith('Erebor');
	});

	it('shows the reason and stays open when the form is refused', async () => {
		const onsubmit = vi.fn().mockReturnValue('Scrivi il nome del tag.');
		const onclose = vi.fn();
		render(PageTagDialog, props({ onsubmit, onclose }));

		await page.getByRole('button', { name: 'Salva' }).click();

		await expect.element(page.getByText('Scrivi il nome del tag.')).toBeVisible();
		expect(onclose).not.toHaveBeenCalled();
	});

	it('starts from the name of the tag it edits and can take it away', async () => {
		const onremove = vi.fn();
		render(PageTagDialog, props({ tag: 'combat', onremove }));

		await expect.element(page.getByRole('heading', { name: 'Modifica il tag' })).toBeVisible();
		await expect.element(nameField()).toHaveValue('combat');
		await page.getByRole('button', { name: 'Togli il tag' }).click();

		expect(onremove).toHaveBeenCalledTimes(1);
	});

	it('tells what is being typed so that the diary can suggest tags', async () => {
		const ontype = vi.fn();
		render(PageTagDialog, props({ ontype }));

		await nameField().fill('com');

		expect(ontype).toHaveBeenLastCalledWith('com');
	});

	it('puts a suggested tag in the field when it is clicked', async () => {
		render(PageTagDialog, props({ suggestions: ['combat', 'compagni'] }));

		await page.getByRole('button', { name: 'compagni' }).click();

		await expect.element(nameField()).toHaveValue('compagni');
	});

	it('closes on Annulla without sending anything', async () => {
		const onsubmit = vi.fn();
		const onclose = vi.fn();
		render(PageTagDialog, props({ onsubmit, onclose }));

		await page.getByRole('button', { name: 'Annulla' }).click();

		expect(onclose).toHaveBeenCalledTimes(1);
		expect(onsubmit).not.toHaveBeenCalled();
	});
});

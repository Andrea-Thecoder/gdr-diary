import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import TagField from './TagField.svelte';

const onadd = vi.fn<(text: string) => boolean>();
const onremove = vi.fn<(tag: string) => void>();
const onedit = vi.fn<(text: string) => void>();

function props(
	overrides: Record<string, unknown> = {}
): Parameters<typeof render<typeof TagField>>[1] {
	return {
		label: 'Tag della pagina',
		tags: ['combat', 'Erebor'],
		max: 10,
		maxlength: 30,
		removeLabel: (tag: string) => `Rimuovi il tag ${tag}`,
		onadd,
		onremove,
		onedit,
		...overrides
	} as never;
}

const field = (): ReturnType<typeof page.getByRole> =>
	page.getByRole('combobox', { name: 'Tag della pagina' });

beforeEach(async () => {
	onadd.mockReset();
	onadd.mockReturnValue(true);
	onremove.mockReset();
	onedit.mockReset();
	await page.viewport(1280, 900);
});

describe('TagField', () => {
	it('shows the tags as chips and the field under a label', async () => {
		render(TagField, props());

		await expect.element(page.getByText('combat', { exact: true })).toBeVisible();
		await expect.element(page.getByText('Erebor', { exact: true })).toBeVisible();
		await expect.element(field()).toBeVisible();
	});

	it('limits the text to the length it is given', async () => {
		render(TagField, props());

		await expect.element(field()).toHaveAttribute('maxlength', '30');
	});

	it('adds the text with Enter and empties the field', async () => {
		render(TagField, props());

		await field().fill('indizi');
		await userEvent.keyboard('{Enter}');

		expect(onadd).toHaveBeenCalledWith('indizi');
		await expect.element(field()).toHaveValue('');
	});

	it('adds the text before the comma and keeps what follows it', async () => {
		render(TagField, props());

		await field().fill('indizi,pn');

		expect(onadd).toHaveBeenCalledTimes(1);
		expect(onadd).toHaveBeenCalledWith('indizi');
		await expect.element(field()).toHaveValue('pn');
	});

	it('adds every piece of a pasted list', async () => {
		render(TagField, props());

		await field().fill('uno, due,tre');

		expect(onadd.mock.calls.map((call) => call[0])).toEqual(['uno', ' due']);
		await expect.element(field()).toHaveValue('tre');
	});

	it('adds the text when the field loses the focus', async () => {
		render(TagField, props());

		await field().fill('indizi');
		await userEvent.tab();

		expect(onadd).toHaveBeenCalledWith('indizi');
	});

	it('does not add an empty text', async () => {
		render(TagField, props());

		await field().click();
		await userEvent.keyboard('{Enter}');

		expect(onadd).not.toHaveBeenCalled();
	});

	it('keeps the text in the field when the tag is refused', async () => {
		onadd.mockReturnValue(false);
		render(TagField, props());

		await field().fill('indizi');
		await userEvent.keyboard('{Enter}');

		await expect.element(field()).toHaveValue('indizi');
	});

	it('keeps Enter from sending the form it may be in', async () => {
		render(TagField, props());
		await field().fill('indizi');

		const press = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
		field().element().dispatchEvent(press);

		expect(press.defaultPrevented).toBe(true);
	});

	it('tells what is being typed so that suggestions can be asked for', async () => {
		render(TagField, props());

		await field().fill('com');

		expect(onedit).toHaveBeenLastCalledWith('com');
	});

	it('lists the suggestions in the field', async () => {
		render(TagField, props({ suggestions: ['combat', 'Combattimento'] }));

		const options = document.querySelectorAll('datalist option');
		expect([...options].map((option) => option.getAttribute('value'))).toEqual([
			'combat',
			'Combattimento'
		]);
		await expect.element(field()).toHaveAttribute('list');
	});

	it('removes a tag with its own button of at least 44 px', async () => {
		render(TagField, props());

		const button = page.getByRole('button', { name: 'Rimuovi il tag Erebor' });
		await button.click();

		expect(onremove).toHaveBeenCalledWith('Erebor');
		const box = button.element().getBoundingClientRect();
		expect(box.width).toBeGreaterThanOrEqual(44);
		expect(box.height).toBeGreaterThanOrEqual(44);
	});

	it('closes the field when the limit is reached and says it', async () => {
		render(TagField, props({ max: 2, hint: 'Hai raggiunto il massimo.' }));

		await expect.element(field()).toBeDisabled();
		await expect.element(page.getByText('Hai raggiunto il massimo.')).toBeVisible();
	});

	it('shows the error under the field as an alert', async () => {
		render(TagField, props({ error: 'Un tag può avere al massimo 30 caratteri.' }));

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Un tag può avere al massimo 30 caratteri.');
		await expect.element(field()).toHaveAttribute('aria-invalid', 'true');
	});

	it('cannot be used and has no remove buttons when it is disabled', async () => {
		render(TagField, props({ disabled: true }));

		await expect.element(field()).toBeDisabled();
		expect(page.getByRole('button').elements()).toHaveLength(0);
	});
});

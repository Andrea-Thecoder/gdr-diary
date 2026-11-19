import { page } from 'vitest/browser';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import PageTagNotes from './PageTagNotes.svelte';

function props(overrides: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		page: 3,
		tag: null,
		ready: true,
		gone: false,
		onopen: vi.fn(),
		...overrides
	};
}

function empty(): ReturnType<typeof page.getByRole> {
	return page.getByRole('button', { name: 'Metti un tag, pagina 3' });
}

function note(tag: string): ReturnType<typeof page.getByRole> {
	return page.getByRole('button', { name: `Tag ${tag}, pagina 3` });
}

describe('PageTagNotes', () => {
	it('shows only the dashed post-it on a page without a tag', async () => {
		render(PageTagNotes, props());

		await expect.element(page.getByRole('group', { name: 'Tag della pagina 3' })).toBeVisible();
		await expect.element(empty()).toBeEnabled();
		expect(page.getByRole('button').elements()).toHaveLength(1);
	});

	it('shows only the post-it of the tag when the page has one', async () => {
		render(PageTagNotes, props({ tag: 'combat' }));

		await expect.element(note('combat')).toBeVisible();
		expect(empty().elements()).toHaveLength(0);
		expect(page.getByRole('button').elements()).toHaveLength(1);
	});

	it('opens the form from the dashed post-it', async () => {
		const onopen = vi.fn();
		render(PageTagNotes, props({ onopen }));

		await empty().click();

		expect(onopen).toHaveBeenCalledTimes(1);
	});

	it('opens the form from the post-it of the tag', async () => {
		const onopen = vi.fn();
		render(PageTagNotes, props({ tag: 'Erebor', onopen }));

		await note('Erebor').click();

		expect(onopen).toHaveBeenCalledTimes(1);
	});

	it('closes the dashed post-it until the page is saved', async () => {
		render(PageTagNotes, props({ ready: false }));

		await expect.element(empty()).toBeDisabled();
		await expect
			.element(empty())
			.toHaveAttribute('title', 'Appena la pagina è salvata puoi mettere il tag.');
	});

	it('closes the dashed post-it when the page was deleted elsewhere', async () => {
		render(PageTagNotes, props({ gone: true }));

		await expect.element(empty()).toBeDisabled();
	});

	it('closes the post-it of the tag when the page was deleted elsewhere', async () => {
		render(PageTagNotes, props({ gone: true, tag: 'combat' }));

		await expect.element(note('combat')).toBeDisabled();
	});
});

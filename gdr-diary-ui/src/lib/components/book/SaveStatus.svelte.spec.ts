import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import type { SaveState } from '#lib/book/book.svelte.js';
import SaveStatus from './SaveStatus.svelte';

const labels: [SaveState, string][] = [
	['saved', 'Salvato'],
	['pending', 'Da salvare'],
	['saving', 'Salvataggio…'],
	['unsaved', 'Non salvato'],
	['offline', 'Offline'],
	['conflict', 'Conflitto']
];

describe('SaveStatus', () => {
	it.each(labels)('says %s in words', async (state, label) => {
		render(SaveStatus, { state });

		await expect.element(page.getByRole('status')).toHaveTextContent(label);
	});

	it('flags offline, conflicting and unsaved text as a problem and the rest as calm', async () => {
		const offline = render(SaveStatus, { state: 'offline' });
		await expect.element(page.getByRole('status')).toHaveClass('text-danger');
		await offline.unmount();

		const pending = render(SaveStatus, { state: 'pending' });
		await expect.element(page.getByRole('status')).not.toHaveClass('text-danger');
		await pending.unmount();

		const saved = render(SaveStatus, { state: 'saved' });
		await expect.element(page.getByRole('status')).not.toHaveClass('text-danger');
		await saved.unmount();
	});
});

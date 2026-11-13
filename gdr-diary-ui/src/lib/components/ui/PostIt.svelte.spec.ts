import { page } from 'vitest/browser';
import { createRawSnippet } from 'svelte';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import PostIt from './PostIt.svelte';

const children = createRawSnippet(() => ({ render: () => '<span>combat</span>' }));

describe('PostIt', () => {
	it('is a button of at least 44 px that calls onclick', async () => {
		const onclick = vi.fn();
		render(PostIt, { color: 1, label: 'Tag combat', onclick, children });

		const button = page.getByRole('button', { name: 'Tag combat' });
		await button.click();

		expect(onclick).toHaveBeenCalledTimes(1);
		expect(button.element().getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
	});

	it('takes its colour from the post-it token with the given number', async () => {
		render(PostIt, { color: 2, label: 'Tag', onclick: vi.fn(), children });

		await expect
			.element(page.getByRole('button', { name: 'Tag' }))
			.toHaveStyle({ background: 'var(--postit-2)' });
	});

	it('is tilted by the given degrees', () => {
		render(PostIt, { color: 0, tilt: -2, label: 'Tag', onclick: vi.fn(), children });

		expect(page.getByRole('button', { name: 'Tag' }).element().style.rotate).toBe('-2deg');
	});

	it('has no colour of its own when it is the dashed one', () => {
		render(PostIt, { color: null, label: 'Tag', onclick: vi.fn(), children });

		expect(page.getByRole('button', { name: 'Tag' }).element().style.background).toBe('');
	});

	it('does not call onclick when disabled', async () => {
		const onclick = vi.fn();
		render(PostIt, { color: null, label: 'Tag', disabled: true, onclick, children });

		await expect.element(page.getByRole('button', { name: 'Tag' })).toBeDisabled();
		expect(onclick).not.toHaveBeenCalled();
	});
});

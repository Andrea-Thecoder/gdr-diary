import { page } from 'vitest/browser';
import { createRawSnippet } from 'svelte';
import { describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import PageSlide from './PageSlide.svelte';

const away = createRawSnippet(() => ({ render: () => '<p>Vecchia</p>' }));
const arrive = createRawSnippet(() => ({ render: () => '<p>Nuova</p>' }));

function frame(): Element {
	const element = document.querySelector('.page-slide-arrive')?.parentElement;
	if (element === null || element === undefined) {
		throw new Error('slide missing');
	}
	return element;
}

describe('PageSlide', () => {
	it('shows the page that goes away and the one that arrives', () => {
		render(PageSlide, { forward: true, away, arrive, onfinish: vi.fn() });

		expect(page.getByText('Vecchia').elements()).toHaveLength(1);
		expect(page.getByText('Nuova').elements()).toHaveLength(1);
	});

	it('goes towards the left when it turns forward and the other way back', () => {
		render(PageSlide, { forward: true, away, arrive, onfinish: vi.fn() });
		expect(getComputedStyle(frame()).getPropertyValue('--dir')).toBe('1');

		render(PageSlide, { forward: false, away, arrive, onfinish: vi.fn() });
		expect(
			Array.from(document.querySelectorAll('.page-slide-arrive'))
				.map((element) =>
					getComputedStyle(element.parentElement as Element).getPropertyValue('--dir')
				)
				.includes('-1')
		).toBe(true);
	});

	it('keeps what slides inside the frame and out of the way of the pointer', () => {
		render(PageSlide, { forward: true, away, arrive, onfinish: vi.fn() });

		expect(getComputedStyle(frame()).overflow).toBe('hidden');
		expect(getComputedStyle(frame()).pointerEvents).toBe('none');
	});

	it('tells when the new page has arrived', async () => {
		const onfinish = vi.fn();
		render(PageSlide, { forward: true, away, arrive, onfinish });

		await vi.waitFor(() => {
			expect(onfinish).toHaveBeenCalledTimes(1);
		});
	});
});

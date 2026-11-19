import '../../../routes/layout.css';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import ScrollIcon from './ScrollIcon.svelte';

describe('ScrollIcon', () => {
	it('is announced as a parchment scroll', () => {
		const { container } = render(ScrollIcon, {});
		expect(container.querySelector('[role="img"]')?.getAttribute('aria-label')).toBe(
			'Un rotolo di pergamena'
		);
	});

	it('keeps the size of the picture on the page', () => {
		const { container } = render(ScrollIcon, {});
		const box = container.querySelector('[role="img"]')?.getBoundingClientRect();
		expect([box?.width, box?.height]).toEqual([58, 82]);
	});

	it('has no text of its own for screen readers to read twice', () => {
		const { container } = render(ScrollIcon, {});
		expect(container.textContent.trim()).toBe('');
	});
});

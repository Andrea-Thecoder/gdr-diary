import '../../../routes/layout.css';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import ScrollRoll from './ScrollRoll.svelte';

function roll(edge: 'top' | 'bottom', diameter: number): HTMLElement {
	const { container } = render(ScrollRoll, { diameter, edge });
	const found = container.querySelector<HTMLElement>(`[data-roll="${edge}"]`);
	if (found === null) {
		throw new Error('the roll is missing');
	}
	return found;
}

describe('ScrollRoll', () => {
	it('is as tall as the diameter it is given', () => {
		expect(roll('top', 64).style.height).toBe('64px');
	});

	it('follows the diameter when it changes', () => {
		expect(roll('bottom', 31.5).style.height).toBe('31.5px');
	});

	it('is hidden from screen readers', () => {
		expect(roll('top', 40).getAttribute('aria-hidden')).toBe('true');
	});

	it('does not catch clicks meant for the text under it', () => {
		expect(getComputedStyle(roll('bottom', 40)).pointerEvents).toBe('none');
	});
});

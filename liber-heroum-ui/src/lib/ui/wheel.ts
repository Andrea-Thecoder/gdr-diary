import type { Action } from 'svelte/action';

export const wheel: Action<HTMLElement, (event: WheelEvent) => void> = (node, handler) => {
	let current = handler;
	const listener = (event: WheelEvent): void => {
		current(event);
	};
	node.addEventListener('wheel', listener, { passive: false });
	return {
		update(next) {
			current = next;
		},
		destroy() {
			node.removeEventListener('wheel', listener);
		}
	};
};

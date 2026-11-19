import { describe, expect, it, vi } from 'vitest';
import { createBackdropPress, outsideClick } from './outside.js';

function pointerdown(target: EventTarget): void {
	target.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
}

function click(target: EventTarget): void {
	target.dispatchEvent(new MouseEvent('click', { bubbles: true }));
}

describe('createBackdropPress', () => {
	const backdrop = document.createElement('div');
	const inside = document.createElement('div');
	const isBackdrop = (target: EventTarget | null): boolean => target === backdrop;

	function down(target: Element): PointerEvent {
		return { target } as unknown as PointerEvent;
	}

	function up(target: Element): MouseEvent {
		return { target } as unknown as MouseEvent;
	}

	it('closes when pressed and released on the backdrop', () => {
		const onclose = vi.fn();
		const handlers = createBackdropPress(isBackdrop, onclose);

		handlers.onpointerdown(down(backdrop));
		handlers.onclick(up(backdrop));

		expect(onclose).toHaveBeenCalledOnce();
	});

	it('stays open when the press started inside', () => {
		const onclose = vi.fn();
		const handlers = createBackdropPress(isBackdrop, onclose);

		handlers.onpointerdown(down(inside));
		handlers.onclick(up(backdrop));

		expect(onclose).not.toHaveBeenCalled();
	});

	it('ignores a click with no press before it', () => {
		const onclose = vi.fn();
		const handlers = createBackdropPress(isBackdrop, onclose);

		handlers.onclick(up(backdrop));

		expect(onclose).not.toHaveBeenCalled();
	});
});

describe('outsideClick', () => {
	function setup(): {
		node: HTMLElement;
		outside: HTMLElement;
		onoutside: () => void;
		stop: () => void;
	} {
		const node = document.createElement('div');
		const outside = document.createElement('div');
		document.body.append(node, outside);
		const onoutside = vi.fn();
		const action = outsideClick(node, onoutside);
		return {
			node,
			outside,
			onoutside,
			stop: () => {
				action.destroy();
				node.remove();
				outside.remove();
			}
		};
	}

	it('calls back when pressed and released outside', () => {
		const { outside, onoutside, stop } = setup();

		pointerdown(outside);
		click(outside);

		expect(onoutside).toHaveBeenCalledOnce();
		stop();
	});

	it('does not call back when the press started inside and ended outside', () => {
		const { node, outside, onoutside, stop } = setup();

		pointerdown(node);
		click(outside);

		expect(onoutside).not.toHaveBeenCalled();
		stop();
	});

	it('does not call back for clicks inside', () => {
		const { node, onoutside, stop } = setup();

		pointerdown(node);
		click(node);

		expect(onoutside).not.toHaveBeenCalled();
		stop();
	});

	it('stops listening after destroy', () => {
		const { outside, onoutside, stop } = setup();
		stop();

		pointerdown(outside);
		click(outside);

		expect(onoutside).not.toHaveBeenCalled();
	});
});

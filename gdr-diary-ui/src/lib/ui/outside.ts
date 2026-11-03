export interface BackdropPress {
	onpointerdown: (event: PointerEvent) => void;
	onclick: (event: MouseEvent) => void;
}

export function createBackdropPress(
	isBackdrop: (target: EventTarget | null) => boolean,
	onclose: () => void
): BackdropPress {
	let pressedOnBackdrop = false;
	return {
		onpointerdown(event) {
			pressedOnBackdrop = isBackdrop(event.target);
		},
		onclick(event) {
			const closes = pressedOnBackdrop && isBackdrop(event.target);
			pressedOnBackdrop = false;
			if (closes) {
				onclose();
			}
		}
	};
}

export interface OutsideClickHandle {
	update: (onoutside: () => void) => void;
	destroy: () => void;
}

export function outsideClick(node: HTMLElement, onoutside: () => void): OutsideClickHandle {
	let handler = onoutside;
	let pressedOutside = false;

	const isOutside = (target: EventTarget | null): boolean =>
		target instanceof Node && !node.contains(target);

	const press = (event: PointerEvent): void => {
		pressedOutside = isOutside(event.target);
	};
	const click = (event: MouseEvent): void => {
		const closes = pressedOutside && isOutside(event.target);
		pressedOutside = false;
		if (closes) {
			handler();
		}
	};

	document.addEventListener('pointerdown', press, true);
	document.addEventListener('click', click, true);

	return {
		update(next) {
			handler = next;
		},
		destroy() {
			document.removeEventListener('pointerdown', press, true);
			document.removeEventListener('click', click, true);
		}
	};
}

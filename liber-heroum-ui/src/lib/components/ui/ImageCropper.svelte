<script lang="ts">
	import {
		MAX_ZOOM,
		MIN_ZOOM,
		frameAspect,
		imagePlacement,
		panView,
		zoomView,
		type CropFrame,
		type CropView,
		type Point,
		type Size
	} from '#lib/ui/crop.js';
	import { wheel } from '#lib/ui/wheel.js';
	import RangeField from './RangeField.svelte';

	interface Props {
		src: string;
		size: Size;
		frame: CropFrame;
		view?: CropView;
		label: string;
		zoomLabel?: string;
		class?: string;
	}

	interface Touch {
		id: number;
		x: number;
		y: number;
	}

	interface Gesture {
		center: Point;
		spread: number | null;
	}

	const KEY_STEP = 0.05;
	const KEY_ZOOM = 1.1;
	const WHEEL_SPEED = 0.0015;

	let {
		src,
		size,
		frame,
		view = $bindable({ zoom: MIN_ZOOM, centerX: 0, centerY: 0 }),
		label,
		zoomLabel = 'Ingrandimento',
		class: className = ''
	}: Props = $props();

	const id = $props.id();
	const hintId = `${id}-hint`;
	const zoomId = `${id}-zoom`;

	let frameElement = $state<HTMLButtonElement | null>(null);
	let touches: Touch[] = [];

	const aspect = $derived(frameAspect(frame));
	const placement = $derived(imagePlacement(view, size, aspect));
	const keyMoves: Record<string, Point> = {
		ArrowLeft: { x: KEY_STEP, y: 0 },
		ArrowRight: { x: -KEY_STEP, y: 0 },
		ArrowUp: { x: 0, y: KEY_STEP },
		ArrowDown: { x: 0, y: -KEY_STEP }
	};

	function anchorOf(point: Point, box: DOMRect): Point {
		return {
			x: (point.x - box.left - box.width / 2) / box.width,
			y: (point.y - box.top - box.height / 2) / box.height
		};
	}

	function gestureOf(list: Touch[]): Gesture | null {
		const [first, second] = list;
		if (first === undefined) {
			return null;
		}
		if (second === undefined) {
			return { center: { x: first.x, y: first.y }, spread: null };
		}
		return {
			center: { x: (first.x + second.x) / 2, y: (first.y + second.y) / 2 },
			spread: Math.hypot(first.x - second.x, first.y - second.y)
		};
	}

	function press(event: PointerEvent & { currentTarget: HTMLButtonElement }): void {
		event.currentTarget.setPointerCapture(event.pointerId);
		touches = [
			...touches.filter((touch) => touch.id !== event.pointerId),
			{ id: event.pointerId, x: event.clientX, y: event.clientY }
		];
	}

	function move(event: PointerEvent & { currentTarget: HTMLButtonElement }): void {
		if (!touches.some((touch) => touch.id === event.pointerId)) {
			return;
		}
		const box = event.currentTarget.getBoundingClientRect();
		const before = gestureOf(touches);
		touches = touches.map((touch) =>
			touch.id === event.pointerId ? { ...touch, x: event.clientX, y: event.clientY } : touch
		);
		const after = gestureOf(touches);
		if (before === null || after === null) {
			return;
		}
		let next = panView(view, size, aspect, {
			x: (after.center.x - before.center.x) / box.width,
			y: (after.center.y - before.center.y) / box.height
		});
		if (before.spread !== null && after.spread !== null && before.spread > 0) {
			next = zoomView(
				next,
				size,
				aspect,
				next.zoom * (after.spread / before.spread),
				anchorOf(after.center, box)
			);
		}
		view = next;
	}

	function release(event: PointerEvent): void {
		touches = touches.filter((touch) => touch.id !== event.pointerId);
	}

	function zoomWithWheel(event: WheelEvent): void {
		event.preventDefault();
		if (frameElement === null) {
			return;
		}
		const lines = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? 16 : 1;
		const factor = Math.exp(-event.deltaY * lines * WHEEL_SPEED);
		view = zoomView(
			view,
			size,
			aspect,
			view.zoom * factor,
			anchorOf({ x: event.clientX, y: event.clientY }, frameElement.getBoundingClientRect())
		);
	}

	function keydown(event: KeyboardEvent): void {
		const step = keyMoves[event.key];
		if (step !== undefined) {
			event.preventDefault();
			view = panView(view, size, aspect, step);
		} else if (event.key === '+' || event.key === '=') {
			event.preventDefault();
			view = zoomView(view, size, aspect, view.zoom * KEY_ZOOM);
		} else if (event.key === '-') {
			event.preventDefault();
			view = zoomView(view, size, aspect, view.zoom / KEY_ZOOM);
		}
	}
</script>

<div class={['flex flex-col gap-3', className]}>
	<div
		class="grid place-items-center overflow-hidden rounded-[6px] border border-(color:--stage-border) p-3 surface-stage min-[560px]:p-4"
		style:--crop-max-height="min(44dvh, 420px)"
	>
		<button
			bind:this={frameElement}
			type="button"
			aria-label={label}
			aria-describedby={hintId}
			use:wheel={zoomWithWheel}
			onpointerdown={press}
			onpointermove={move}
			onpointerup={release}
			onpointercancel={release}
			onkeydown={keydown}
			style:aspect-ratio={aspect}
			style:width={`min(100%, calc(var(--crop-max-height) * ${String(aspect)}))`}
			style:touch-action="none"
			class="relative block cursor-grab border-0 bg-transparent p-0 select-none active:cursor-grabbing"
		>
			<img
				{src}
				alt=""
				draggable="false"
				style:width={`${String(placement.width)}%`}
				style:left={`${String(placement.left)}%`}
				style:top={`${String(placement.top)}%`}
				style:max-width="none"
				style:height="auto"
				class="pointer-events-none absolute"
			/>
			<span
				aria-hidden="true"
				style:box-shadow="0 0 0 100vmax var(--overlay)"
				class={[
					'pointer-events-none absolute inset-0 border-2 border-orn',
					frame.shape === 'round' && 'rounded-full'
				]}
			></span>
		</button>
	</div>
	<p id={hintId} class="sr-only">
		Trascina per spostare l’immagine. Con la tastiera usa le frecce per spostarla e più e meno per
		ingrandirla.
	</p>
	<p id={zoomId} class="m-0 text-base text-soft">{zoomLabel}</p>
	<RangeField
		value={Math.round(view.zoom * 100)}
		min={MIN_ZOOM * 100}
		max={MAX_ZOOM * 100}
		unit="%"
		labelledby={zoomId}
		onchange={(value) => {
			view = zoomView(view, size, aspect, value / 100);
		}}
	/>
</div>

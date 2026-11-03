import { MAX_PAGES, PAGES_PER_SPREAD } from './pages.js';

export type TurnDirection = 'forward' | 'backward';

export function spreadOf(page: number): number {
	return Math.ceil(page / PAGES_PER_SPREAD);
}

export function leftPageOf(spread: number): number {
	return (spread - 1) * PAGES_PER_SPREAD + 1;
}

export function clampPage(page: number, last: number): number {
	const top = Math.min(Math.max(last, 1), MAX_PAGES);
	return Math.min(Math.max(Math.trunc(page), 1), top);
}

export function parseSpread(value: string | null): number {
	const parsed = Number.parseInt(value ?? '', 10);
	if (Number.isNaN(parsed) || parsed < 1) {
		return 1;
	}
	return Math.min(parsed, spreadOf(MAX_PAGES));
}

export interface View {
	single: boolean;
	focus: number;
}

export function visiblePages(view: View): number[] {
	if (view.single) {
		return [view.focus];
	}
	const left = leftPageOf(spreadOf(view.focus));
	return [left, left + 1];
}

export function hasPrevious(view: View): boolean {
	return visiblePages(view)[0] !== undefined && (visiblePages(view)[0] ?? 1) > 1;
}

export function hasNext(view: View, last: number): boolean {
	const pages = visiblePages(view);
	const edge = pages[pages.length - 1] ?? 1;
	return edge < Math.min(last, MAX_PAGES);
}

export function nextFocus(view: View, direction: TurnDirection): number {
	const pages = visiblePages(view);
	if (view.single) {
		return view.focus + (direction === 'forward' ? 1 : -1);
	}
	const left = pages[0] ?? 1;
	return direction === 'forward' ? left + PAGES_PER_SPREAD : left - PAGES_PER_SPREAD;
}

export function counterLabel(view: View, last: number): string {
	if (last === 0) {
		return 'Nessuna pagina ancora';
	}
	const pages = visiblePages(view);
	const left = pages[0] ?? 1;
	if (view.single) {
		return left > last ? 'Nuova pagina' : `Pagina ${String(left)} di ${String(last)}`;
	}
	const right = pages[1] ?? left + 1;
	if (left > last) {
		return 'Nuova pagina';
	}
	if (right > last) {
		return `Pagina ${String(left)} di ${String(last)}`;
	}
	return `Pagine ${String(left)} e ${String(right)} di ${String(last)}`;
}

export function thickness(page: number, last: number): { before: number; after: number } {
	const total = Math.max(last, 1);
	const steps = (count: number): number => Math.min(6, Math.ceil((count / MAX_PAGES) * 6 * 4));
	return { before: steps(Math.min(page - 1, total)), after: steps(Math.max(total - page, 0)) };
}

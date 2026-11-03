import { tick } from 'svelte';

export function required<T>(value: T | null | undefined, what = 'Valore'): T {
	if (value === null || value === undefined) {
		throw new Error(`${what} mancante.`);
	}
	return value;
}

export async function settle(): Promise<void> {
	await tick();
	await new Promise((resolve) => setTimeout(resolve, 0));
}

import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
	PENDING_ACTION_MAX_AGE_MS,
	rememberPendingAction,
	takePendingAction
} from './pending-action.js';

function memoryStorage(): void {
	const data = new Map<string, string>();
	vi.stubGlobal('sessionStorage', {
		getItem: (key: string) => data.get(key) ?? null,
		setItem: (key: string, value: string) => {
			data.set(key, value);
		},
		removeItem: (key: string) => {
			data.delete(key);
		}
	});
}

describe('pending action', () => {
	beforeEach(() => {
		memoryStorage();
	});

	it('is found once after it was remembered, then never again', () => {
		rememberPendingAction('export-data', 1000);

		const takes = [takePendingAction('export-data', 2000), takePendingAction('export-data', 3000)];

		expect(takes).toEqual([true, false]);
	});

	it('is not found when nothing was remembered, as after a reload', () => {
		expect(takePendingAction('export-data')).toBe(false);
	});

	it('is left alone when another action asks for it', () => {
		rememberPendingAction('delete-account', 1000);

		const takes = [
			takePendingAction('export-data', 2000),
			takePendingAction('delete-account', 3000)
		];

		expect(takes).toEqual([false, true]);
	});

	it('expires when the return from the password takes too long', () => {
		rememberPendingAction('export-data', 1000);

		expect(takePendingAction('export-data', 1001 + PENDING_ACTION_MAX_AGE_MS)).toBe(false);
	});
});

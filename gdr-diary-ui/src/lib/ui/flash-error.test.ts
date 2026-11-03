import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { recallError, rememberError } from '#lib/ui/flash-error.js';

function fakeStorage(): Storage {
	const data = new Map<string, string>();
	return {
		get length() {
			return data.size;
		},
		clear: () => {
			data.clear();
		},
		getItem: (key) => data.get(key) ?? null,
		key: (index) => [...data.keys()][index] ?? null,
		removeItem: (key) => {
			data.delete(key);
		},
		setItem: (key, value) => {
			data.set(key, value);
		}
	};
}

describe('flash error', () => {
	beforeEach(() => {
		vi.stubGlobal('sessionStorage', fakeStorage());
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('hands the message to the next page load, once', () => {
		rememberError('Il server non risponde.');

		expect(recallError()).toBe('Il server non risponde.');
		expect(recallError()).toBeNull();
	});

	it('has nothing to say when nothing was remembered', () => {
		expect(recallError()).toBeNull();
	});

	it('does not break when the storage is not available', () => {
		vi.stubGlobal('sessionStorage', {
			getItem: () => {
				throw new Error('blocked');
			},
			setItem: () => {
				throw new Error('blocked');
			},
			removeItem: () => {
				throw new Error('blocked');
			}
		});

		expect(() => {
			rememberError('x');
		}).not.toThrow();
		expect(recallError()).toBeNull();
	});
});

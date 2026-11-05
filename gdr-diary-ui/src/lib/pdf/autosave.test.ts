import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AUTOSAVE_MAX_WAIT, AutoSaver, retryDelay } from '#lib/pdf/autosave.js';

describe('AutoSaver', () => {
	const run = vi.fn<() => Promise<void>>();
	let enabled = true;
	let delay = 5000;
	let saver: AutoSaver;

	beforeEach(() => {
		vi.useFakeTimers();
		run.mockReset();
		run.mockResolvedValue(undefined);
		enabled = true;
		delay = 5000;
		saver = new AutoSaver({ enabled: () => enabled, delay: () => delay, run });
	});

	afterEach(() => {
		saver.cancel();
		vi.useRealTimers();
	});

	it('saves after the pause chosen by the user', () => {
		saver.touch();

		vi.advanceTimersByTime(4999);
		expect(run).not.toHaveBeenCalled();
		vi.advanceTimersByTime(1);
		expect(run).toHaveBeenCalledOnce();
	});

	it('waits again at every change', () => {
		saver.touch();
		vi.advanceTimersByTime(4000);
		saver.touch();
		vi.advanceTimersByTime(4000);

		expect(run).not.toHaveBeenCalled();
		vi.advanceTimersByTime(1000);
		expect(run).toHaveBeenCalledOnce();
	});

	it('does not wait forever while the user keeps writing', () => {
		delay = 10000;
		saver.touch();
		vi.advanceTimersByTime(9000);
		saver.touch();
		vi.advanceTimersByTime(9000);
		saver.touch();

		vi.advanceTimersByTime(AUTOSAVE_MAX_WAIT - 18000);

		expect(run).toHaveBeenCalledOnce();
	});

	it('never goes below the pause when the pause is longer than the limit', () => {
		delay = 30000;
		saver.touch();

		vi.advanceTimersByTime(29999);
		expect(run).not.toHaveBeenCalled();
		vi.advanceTimersByTime(1);
		expect(run).toHaveBeenCalledOnce();
	});

	it('does nothing when the autosave is off', () => {
		enabled = false;

		saver.touch();
		vi.advanceTimersByTime(60000);

		expect(run).not.toHaveBeenCalled();
	});

	it('does not save after being cancelled', () => {
		saver.touch();
		saver.cancel();

		vi.advanceTimersByTime(60000);

		expect(run).not.toHaveBeenCalled();
	});

	it('starts counting the limit again after a save', () => {
		saver.touch();
		vi.advanceTimersByTime(5000);
		saver.touch();

		vi.advanceTimersByTime(4999);
		expect(run).toHaveBeenCalledOnce();
		vi.advanceTimersByTime(1);
		expect(run).toHaveBeenCalledTimes(2);
	});
});

describe('retryDelay', () => {
	it.each([
		[0, 5000],
		[1, 10000],
		[2, 20000],
		[3, 30000],
		[10, 30000]
	])('waits %i attempts: %i ms', (attempts, expected) => {
		expect(retryDelay(attempts)).toBe(expected);
	});
});

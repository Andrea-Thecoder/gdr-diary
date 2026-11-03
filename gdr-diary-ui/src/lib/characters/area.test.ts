import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

function memoryStorage(initial: Record<string, string> = {}): void {
	const data = new Map(Object.entries(initial));
	vi.stubGlobal('sessionStorage', {
		getItem: (key: string) => data.get(key) ?? null,
		setItem: (key: string, value: string) => {
			data.set(key, value);
		}
	});
}

describe('areaChoice', () => {
	beforeAll(async () => {
		memoryStorage();
		await import('./area.svelte');
	});

	beforeEach(() => {
		vi.resetModules();
	});

	it('starts on the characters', async () => {
		memoryStorage();
		const { areaChoice } = await import('./area.svelte');

		expect(areaChoice.value).toBe('characters');
	});

	it('keeps the chosen area while the user is elsewhere in the app', async () => {
		memoryStorage();
		const { areaChoice } = await import('./area.svelte');

		areaChoice.select('campaigns');

		expect(areaChoice.value).toBe('campaigns');
	});

	it('remembers the choice after a reload and ignores anything unknown', async () => {
		memoryStorage({ 'gdr.characters-area': 'campaigns' });
		expect((await import('./area.svelte')).areaChoice.value).toBe('campaigns');

		vi.resetModules();
		memoryStorage({ 'gdr.characters-area': 'altro' });
		expect((await import('./area.svelte')).areaChoice.value).toBe('characters');
	});

	it('works when the storage is not available', async () => {
		vi.stubGlobal('sessionStorage', undefined);
		const { areaChoice } = await import('./area.svelte');

		areaChoice.select('campaigns');

		expect(areaChoice.value).toBe('campaigns');
	});
});

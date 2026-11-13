import { describe, expect, it, vi } from 'vitest';

const { loadCharacter } = vi.hoisted(() => ({
	loadCharacter: vi.fn<(id: string) => Promise<unknown>>()
}));

vi.mock('#lib/characters/load.js', () => ({ loadCharacter }));

import { load } from './+page.js';

describe('character tab load', () => {
	it('loads the character of the route', async () => {
		const loaded = { character: { id: 'c1', name: 'Alira' } };
		loadCharacter.mockResolvedValue(loaded);

		await expect(
			(load as unknown as (event: unknown) => Promise<unknown>)({ params: { characterId: 'c1' } })
		).resolves.toBe(loaded);
		expect(loadCharacter).toHaveBeenCalledWith('c1');
	});
});

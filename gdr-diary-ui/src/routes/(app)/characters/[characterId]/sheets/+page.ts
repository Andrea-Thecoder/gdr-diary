import { loadCharacter } from '#lib/characters/load.js';
import type { PageLoad } from './$types';

export const load: PageLoad = ({ params }) => loadCharacter(params.characterId);

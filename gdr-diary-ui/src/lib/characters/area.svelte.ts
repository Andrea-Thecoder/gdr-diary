export type Area = 'characters' | 'campaigns';

const STORAGE_KEY = 'gdr.characters-area';

function isArea(value: string | null): value is Area {
	return value === 'characters' || value === 'campaigns';
}

function readStored(): Area {
	try {
		const value = sessionStorage.getItem(STORAGE_KEY);
		return isArea(value) ? value : 'characters';
	} catch {
		return 'characters';
	}
}

function writeStored(area: Area): void {
	try {
		sessionStorage.setItem(STORAGE_KEY, area);
	} catch {
		return;
	}
}

class AreaChoice {
	private chosen = $state<Area | null>(null);

	get value(): Area {
		return this.chosen ?? readStored();
	}

	select(area: Area): void {
		this.chosen = area;
		writeStored(area);
	}
}

export const areaChoice = new AreaChoice();

import { resolve } from '$app/paths';
import type { PathnameWithSearchOrHash } from '$app/types';

const STORAGE_KEY = 'gdr.last-place';
const FALLBACK_LABEL = 'Tutti i personaggi';

function accountPaths(): string[] {
	return [resolve('/(app)/profile'), resolve('/(app)/settings')];
}

function isLocal(target: string): boolean {
	return target.startsWith('/') && !target.startsWith('//');
}

function readStored(): string | null {
	try {
		const value = sessionStorage.getItem(STORAGE_KEY);
		return value !== null && isLocal(value) ? value : null;
	} catch {
		return null;
	}
}

function writeStored(target: string): void {
	try {
		sessionStorage.setItem(STORAGE_KEY, target);
	} catch {
		return;
	}
}

export function isAccountPage(pathname: string): boolean {
	return accountPaths().some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

export function placeLabel(target: string): string {
	const path = target.split(/[?#]/)[0] ?? target;
	const root = resolve('/').replace(/\/$/, '');
	if (path.startsWith(`${resolve('/(app)/characters')}/`)) {
		return 'Torna al personaggio';
	}
	if (path.startsWith(`${root}/diaries/`)) {
		return 'Torna al diario';
	}
	return FALLBACK_LABEL;
}

class LastPlace {
	private current = $state<string | null>(null);

	record(url: URL): void {
		if (isAccountPage(url.pathname)) {
			return;
		}
		const target = `${url.pathname}${url.search}`;
		this.current = target;
		writeStored(target);
	}

	get href(): PathnameWithSearchOrHash {
		return (this.current ?? readStored() ?? '/') as PathnameWithSearchOrHash;
	}

	get label(): string {
		return placeLabel(this.href);
	}
}

export const lastPlace = new LastPlace();

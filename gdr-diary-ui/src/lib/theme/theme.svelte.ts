import { getThemeCookieDomain } from '#lib/env.js';
import {
	applyPreference,
	nextPreference,
	readStoredPreference,
	storePreference,
	type ThemePreference
} from './theme.js';

class ThemeState {
	preference = $state<ThemePreference>('system');

	init(): void {
		this.preference = readStoredPreference();
	}

	cycle(): void {
		this.select(nextPreference(this.preference));
	}

	select(preference: ThemePreference): void {
		this.preference = preference;
		storePreference(preference, getThemeCookieDomain());
		applyPreference(preference);
	}
}

export const theme = new ThemeState();

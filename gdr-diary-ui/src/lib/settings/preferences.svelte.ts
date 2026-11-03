import { DEFAULT_PREFERENCES, preferencesFromSettings, type Preferences } from './preferences.js';
import type { AccountSettings } from './settings.js';

class PreferencesState {
	value = $state<Preferences>({ ...DEFAULT_PREFERENCES });

	apply(settings: AccountSettings | null | undefined): void {
		this.value = preferencesFromSettings(settings);
	}
}

export const preferences = new PreferencesState();

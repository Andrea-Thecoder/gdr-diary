import { auth } from './auth.svelte.js';
import { appHomeUrl } from './login.js';

export function registerToApp(): void {
	void auth.register(appHomeUrl());
}

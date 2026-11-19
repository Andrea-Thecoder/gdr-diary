import { resolve } from '$app/paths';
import { auth } from './auth.svelte.js';

export function appHomeUrl(): string {
	return new URL(resolve('/(app)/characters'), window.location.origin).href;
}

export function loginToApp(): void {
	void auth.login(appHomeUrl());
}

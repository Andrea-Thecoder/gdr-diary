import { auth } from '#lib/auth/auth.svelte.js';
import { getEnv } from '#lib/env.js';
import { createApiClient, type ApiClient } from '#lib/api/client.js';

let instance: ApiClient | undefined;

export function getApi(): ApiClient {
	instance ??= createApiClient({
		baseUrl: getEnv().VITE_API_BASE_URL,
		getToken: () => auth.getToken()
	});
	return instance;
}

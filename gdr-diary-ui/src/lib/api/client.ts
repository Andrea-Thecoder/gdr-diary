import createClient, { type Client, type Middleware } from 'openapi-fetch';
import { translateMessage, translateTitle } from './messages.js';
import type { components, paths } from './schema.js';

export type ApiClient = Client<paths>;
export type ExceptionResponse = components['schemas']['ExceptionResponse'];

export const NETWORK_MESSAGE =
	'Non riesco a raggiungere il server: controlla la connessione e riprova.';

export class NetworkError extends TypeError {
	constructor(cause: unknown) {
		super(NETWORK_MESSAGE, { cause });
		this.name = 'NetworkError';
	}
}

export interface ApiClientOptions {
	baseUrl: string;
	getToken: () => Promise<string>;
	fetch?: typeof globalThis.fetch;
}

export function createApiClient(options: ApiClientOptions): ApiClient {
	const send = options.fetch ?? globalThis.fetch.bind(globalThis);
	const client = createClient<paths>({
		baseUrl: options.baseUrl,
		fetch: async (request: Request): Promise<Response> => {
			try {
				return await send(request);
			} catch (cause) {
				if (cause instanceof TypeError) {
					throw new NetworkError(cause);
				}
				throw cause;
			}
		}
	});
	const bearerAuth: Middleware = {
		async onRequest({ request }) {
			request.headers.set('Authorization', `Bearer ${await options.getToken()}`);
			return request;
		}
	};
	client.use(bearerAuth);
	return client;
}

export function describeApiError(error: ExceptionResponse | undefined, fallback: string): string {
	if (error === undefined) {
		return fallback;
	}
	const violations = (error.violations ?? []).flatMap((violation) =>
		typeof violation.message !== 'string' || violation.message === ''
			? []
			: [translateMessage(violation.message, violation.field)]
	);
	if (violations.length > 0) {
		return violations.join('. ');
	}
	return error.title === undefined ? fallback : translateTitle(error.title);
}

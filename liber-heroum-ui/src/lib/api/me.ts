import { getEnv } from '#lib/env.js';
import { describeApiError } from '#lib/api/client.js';
import { getApi } from '#lib/api/index.js';

let pending: Promise<string> | undefined;

async function serverIsThere(): Promise<boolean> {
	try {
		await fetch(getEnv().VITE_API_BASE_URL, { mode: 'no-cors', cache: 'no-store' });
		return true;
	} catch {
		return false;
	}
}

async function explain(cause: unknown): Promise<Error> {
	if (cause instanceof TypeError) {
		if (await serverIsThere()) {
			console.warn(
				'Il server risponde ma il browser ha bloccato la richiesta, di solito è il CORS: controlla app.cors.allowed-origins nel backend.'
			);
		}
		return new Error('Non riesco a raggiungere il server. Riprova tra poco.', { cause });
	}
	return cause instanceof Error ? cause : new Error('Errore sconosciuto.');
}

async function signIn(): Promise<string> {
	const { data, error, response } = await getApi()
		.POST('/me')
		.catch(async (cause: unknown) => {
			throw await explain(cause);
		});
	const id = data?.payload;
	if (id === undefined) {
		const message = describeApiError(error, 'Impossibile registrare l’utente sul server.');
		throw new Error(`${message} (errore ${String(response.status)})`);
	}
	return id;
}

export async function syncAppUser(): Promise<string> {
	const id = await signIn();
	pending = Promise.resolve(id);
	return id;
}

export function ensureAppUser(): Promise<string> {
	pending ??= signIn().catch((cause: unknown) => {
		pending = undefined;
		throw cause;
	});
	return pending;
}

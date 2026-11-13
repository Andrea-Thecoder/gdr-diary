import {
	describeApiError,
	NETWORK_MESSAGE,
	NetworkError,
	type ExceptionResponse
} from '#lib/api/client.js';

export type FeedbackType = 'idea' | 'bug' | 'love';

export interface FeedbackKind {
	value: FeedbackType;
	title: string;
	hint: string;
}

export const FEEDBACK_KINDS: readonly FeedbackKind[] = [
	{
		value: 'idea',
		title: 'Un’idea',
		hint: 'Una cosa che vorresti poter fare.'
	},
	{
		value: 'bug',
		title: 'Un problema',
		hint: 'Qualcosa non funziona come dovrebbe.'
	},
	{
		value: 'love',
		title: 'Un complimento',
		hint: 'Una cosa che ti è piaciuta.'
	}
];

export const PLACEHOLDERS: Record<FeedbackType, string> = {
	idea: 'Cosa vorresti poter fare e perché ti servirebbe?',
	bug: 'Cosa stavi facendo, cosa ti aspettavi e cosa è successo?',
	love: 'Cosa ti è piaciuto di più?'
};

export const MESSAGE_MAX = 2000;
export const REDIRECT_SECONDS = 6;
export const SHOTS_MAX = 3;
export const SHOT_MAX_BYTES = 5 * 1024 * 1024;
export const SHOTS_TOTAL_MAX_BYTES = 5 * 1024 * 1024;
export const SHOT_TYPES: readonly string[] = ['image/png', 'image/jpeg', 'image/webp'];

export const EMPTY_MESSAGE = 'Scrivi almeno qualche parola: il messaggio è obbligatorio.';
export const NO_CONSENT_MESSAGE = 'Serve la spunta per poter inviare il messaggio.';
export const EMPTY_EMAIL_MESSAGE = 'Scrivi la tua email: mi serve per risponderti.';
export const BAD_EMAIL_MESSAGE = 'Questo indirizzo non sembra un’email: controllalo.';
export const SERVER_MESSAGE = 'Il messaggio non è partito: riprova tra poco.';
export const TOO_MANY_MESSAGE = 'Hai inviato troppi messaggi in poco tempo: riprova più tardi.';

export interface FeedbackDraft {
	type: FeedbackType;
	message: string;
	email: string;
	shots: readonly File[];
	technical: boolean;
	consent: boolean;
	website: string;
	from: string;
}

export interface FeedbackErrors {
	message: string | null;
	email: string | null;
	consent: string | null;
}

export function emptyDraft(): FeedbackDraft {
	return {
		type: 'bug',
		message: '',
		email: '',
		shots: [],
		technical: false,
		consent: false,
		website: '',
		from: ''
	};
}

function checkEmail(value: string): string | null {
	const email = value.trim();
	if (email === '') {
		return EMPTY_EMAIL_MESSAGE;
	}
	return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? null : BAD_EMAIL_MESSAGE;
}

export function checkFeedback(draft: FeedbackDraft): FeedbackErrors {
	return {
		message: draft.message.trim() === '' ? EMPTY_MESSAGE : null,
		email: checkEmail(draft.email),
		consent: draft.consent ? null : NO_CONSENT_MESSAGE
	};
}

export function hasErrors(errors: FeedbackErrors): boolean {
	return errors.message !== null || errors.email !== null || errors.consent !== null;
}

export function secondsLabel(count: number): string {
	return count === 1 ? '1 secondo' : `${String(count)} secondi`;
}

export function formatSize(bytes: number): string {
	if (bytes < 1024 * 1024) {
		return `${String(Math.max(1, Math.round(bytes / 1024)))} KB`;
	}
	return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
}

function totalSize(files: readonly File[]): number {
	return files.reduce((sum, file) => sum + file.size, 0);
}

export interface AddedShots {
	shots: File[];
	rejected: string | null;
}

export function addShots(current: readonly File[], incoming: readonly File[]): AddedShots {
	const shots = [...current];
	const problems: string[] = [];
	for (const file of incoming) {
		if (!SHOT_TYPES.includes(file.type)) {
			problems.push(`«${file.name}» non è un’immagine PNG, JPEG o WebP`);
		} else if (file.size > SHOT_MAX_BYTES) {
			problems.push(`«${file.name}» pesa più di 5 MB`);
		} else if (shots.length >= SHOTS_MAX) {
			problems.push(`«${file.name}» non c’è posto: al massimo ${String(SHOTS_MAX)} schermate`);
		} else if (totalSize(shots) + file.size > SHOTS_TOTAL_MAX_BYTES) {
			problems.push(
				`«${file.name}» non ci sta: hai già ${formatSize(totalSize(shots))} e le schermate insieme possono pesare al massimo 5 MB`
			);
		} else {
			shots.push(file);
		}
	}
	return { shots, rejected: problems.length === 0 ? null : `${problems.join('. ')}.` };
}

export function screenOf(routeId: string | null | undefined): string {
	return (routeId ?? '').replace(/\/\([^/)]+\)/g, '');
}

export function technicalInfo(from: string): string {
	return JSON.stringify({
		userAgent: navigator.userAgent,
		screen: `${String(window.innerWidth)}x${String(window.innerHeight)}`,
		from
	});
}

export function feedbackForm(draft: FeedbackDraft, technical: string | null): FormData {
	const form = new FormData();
	form.append('type', draft.type);
	form.append('message', draft.message.trim());
	form.append('email', draft.email.trim());
	if (technical !== null) {
		form.append('technicalInfo', technical);
	}
	form.append('consent', 'true');
	form.append('website', draft.website);
	for (const shot of draft.shots) {
		form.append('files', shot);
	}
	return form;
}

async function problemOf(response: Response): Promise<string> {
	if (response.status === 429) {
		return TOO_MANY_MESSAGE;
	}
	try {
		return describeApiError((await response.json()) as ExceptionResponse, SERVER_MESSAGE);
	} catch {
		return SERVER_MESSAGE;
	}
}

export async function sendFeedback(
	baseUrl: string,
	draft: FeedbackDraft,
	options: { fetch?: typeof globalThis.fetch } = {}
): Promise<void> {
	const send = options.fetch ?? globalThis.fetch.bind(globalThis);
	const form = feedbackForm(draft, draft.technical ? technicalInfo(draft.from) : null);
	let response: Response;
	try {
		response = await send(`${baseUrl}/feedback`, { method: 'POST', body: form });
	} catch (cause) {
		throw new NetworkError(cause);
	}
	if (!response.ok) {
		throw new Error(await problemOf(response));
	}
}

export { NETWORK_MESSAGE };

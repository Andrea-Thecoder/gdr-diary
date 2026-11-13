const FIELD_LABELS: Readonly<Record<string, string>> = {
	name: 'Nome',
	title: 'Titolo',
	description: 'Descrizione',
	campaignName: 'Campagna',
	backgroundColor: 'Colore',
	coverColor: 'Colore',
	font: 'Carattere',
	fontSize: 'Dimensione del carattere',
	file: 'File',
	settings: 'Impostazioni',
	size: 'Pagina',
	page: 'Pagina',
	tag: 'Tag',
	message: 'Messaggio',
	email: 'Email'
};

const TITLES: Readonly<Record<string, string>> = {
	BAD_REQUEST: 'Richiesta non valida',
	UNAUTHORIZED: 'Devi accedere per continuare',
	FORBIDDEN: 'Non hai il permesso di farlo',
	NOT_FOUND: 'Elemento non trovato',
	CONFLICT: 'I dati sono cambiati nel frattempo: riprova',
	CONTENT_TOO_LARGE: 'Il file è troppo grande',
	INTERNAL_ERROR: 'Si è verificato un errore imprevisto: riprova più tardi',
	'A diary can have at most 10 different tags':
		'Un diario può avere al massimo 10 tag diversi: usa uno dei tag già usati.'
};

const SENTENCES: Readonly<Record<string, string>> = {
	'Malformed JSON request': 'La richiesta non è leggibile: riprova',
	'Malformed multipart request': 'Il file non è stato inviato correttamente: riprova',
	'File too large': 'Il file è troppo grande',
	'Unsupported file type': 'Tipo di file non supportato',
	'Empty file': 'Il file è vuoto',
	'Declared content type is missing or does not match the file content':
		'Il tipo del file non corrisponde al suo contenuto',
	'File extension does not match the file content':
		'L’estensione del file non corrisponde al suo contenuto',
	'Cannot read the file': 'Non riesco a leggere il file',
	'Storage not available': 'L’archivio dei file non è disponibile al momento: riprova più tardi',
	'Page content too large': 'Il testo della pagina è troppo lungo',
	'Settings too large': 'Le impostazioni sono troppo grandi',
	'Settings contain characters that cannot be stored':
		'Le impostazioni contengono caratteri che non si possono salvare',
	'Another page was added at the same time, try again':
		'Un’altra pagina è stata aggiunta nello stesso momento: riprova',
	'The page must be 1 or greater': 'Il numero della pagina deve essere almeno 1',
	'The page was modified elsewhere, reload it':
		'La pagina è stata modificata altrove: ricarica il diario',
	'The resource was modified elsewhere, reload it':
		'I dati sono stati modificati altrove: ricarica e riprova',
	'The character changed while it was being deleted, try again':
		'Il personaggio è cambiato mentre lo eliminavi: riprova',
	'The diary changed while it was being deleted, try again':
		'Il diario è cambiato mentre lo eliminavi: riprova',
	'The account changed while it was being deleted, try again':
		'L’account è cambiato mentre lo eliminavi: riprova',
	'Technical info too large':
		'Le informazioni tecniche sono troppo lunghe: invia il messaggio senza',
	'Technical info is not a valid JSON object':
		'Le informazioni tecniche non sono valide: invia il messaggio senza',
	'Technical info contains characters that cannot be stored':
		'Le informazioni tecniche contengono caratteri che non si possono salvare',
	'the privacy policy must be accepted': 'Serve la spunta per poter inviare il messaggio',
	'must be idea, bug or love': 'Scegli il tipo di messaggio: idea, problema o complimento',
	'Too many feedback submissions, try again later':
		'Hai inviato troppi messaggi in poco tempo: riprova più tardi',
	'Diary not found': 'Diario non trovato',
	'Character not found': 'Personaggio non trovato',
	'Page not found': 'Pagina non trovata',
	'Sheet not found': 'Scheda non trovata',
	'Feedback not found': 'Messaggio non trovato',
	'Not Found': 'Questa pagina non esiste.',
	'A diary can have at most 10 different tags':
		'Un diario può avere al massimo 10 tag diversi: usa uno dei tag già usati.',
	'User not found': 'Utente non trovato',
	'The token has no email address': 'Il tuo account non ha un indirizzo email',
	'Authentication required': 'Devi accedere per continuare',
	'Recent authentication required': 'Per continuare devi accedere di nuovo',
	'A more recent authentication is required': 'Per continuare devi accedere di nuovo',
	'Access denied': 'Non hai il permesso di farlo',
	'The account could not be deleted on the authentication server, try again later':
		'Non riesco a chiudere il tuo accesso in questo momento e non ho cancellato niente: riprova tra poco.',
	'An unexpected error occurred': 'Si è verificato un errore imprevisto: riprova più tardi'
};

const CONSTRAINTS: Readonly<Record<string, string>> = {
	'must not be blank': 'non può essere vuoto',
	'must not be empty': 'non può essere vuoto',
	'must not be null': 'non può essere vuoto',
	'Required part is missing': 'è obbligatorio',
	'Invalid value': 'non è valido',
	'contains characters that cannot be stored': 'contiene caratteri che non si possono salvare',
	'must be a well-formed email address': 'non è un indirizzo email valido'
};

const COLOR_PATTERN = '#[0-9A-Fa-f]{6}';

function sentenceFor(message: string): string | undefined {
	const exact = SENTENCES[message];
	if (exact !== undefined) {
		return exact;
	}
	const full = /^The diary is full \((\d+) pages\)$/.exec(message);
	if (full?.[1] !== undefined) {
		return `Il diario è pieno: ${full[1]} pagine su ${full[1]}`;
	}
	const sheets = /^The character has too many sheets \((\d+)\)$/.exec(message);
	if (sheets?.[1] !== undefined) {
		return `Questo personaggio ha già ${sheets[1]} schede, il massimo: elimina quella che non ti serve più per caricarne un’altra`;
	}
	const files = /^Too many files \(maximum (\d+)\)$/.exec(message);
	if (files?.[1] !== undefined) {
		return `Puoi allegare al massimo ${files[1]} immagini`;
	}
	const total = /^Files too large \(maximum total (\d+) MB\)$/.exec(message);
	if (total?.[1] !== undefined) {
		return `Le immagini insieme superano ${total[1]} MB: togline qualcuna`;
	}
	if (message.startsWith('Invalid page content at ')) {
		return 'Il contenuto della pagina non è valido';
	}
	const parameter = /^Invalid value for parameter: (.+)$/.exec(message);
	if (parameter?.[1] !== undefined) {
		return `Il parametro ${parameter[1]} non è valido`;
	}
	return undefined;
}

function constraintFor(message: string): string | undefined {
	const exact = CONSTRAINTS[message];
	if (exact !== undefined) {
		return exact;
	}
	const size = /^size must be between (\d+) and (\d+)$/.exec(message);
	if (size?.[1] !== undefined && size[2] !== undefined) {
		return size[1] === '0'
			? `deve essere lungo al massimo ${size[2]} caratteri`
			: `deve essere lungo da ${size[1]} a ${size[2]} caratteri`;
	}
	const pattern = /^must match "(.*)"$/.exec(message);
	if (pattern?.[1] !== undefined) {
		return pattern[1].includes(COLOR_PATTERN)
			? 'deve essere un colore come #3B2A6B'
			: 'non ha un formato valido';
	}
	const greater = /^must be greater than (-?\d+)$/.exec(message);
	if (greater?.[1] !== undefined) {
		return `deve essere maggiore di ${greater[1]}`;
	}
	const atLeast = /^must be greater than or equal to (-?\d+)$/.exec(message);
	if (atLeast?.[1] !== undefined) {
		return `deve essere almeno ${atLeast[1]}`;
	}
	const less = /^must be less than (-?\d+)$/.exec(message);
	if (less?.[1] !== undefined) {
		return `deve essere minore di ${less[1]}`;
	}
	const atMost = /^must be less than or equal to (-?\d+)$/.exec(message);
	if (atMost?.[1] !== undefined) {
		return `deve essere al massimo ${atMost[1]}`;
	}
	return undefined;
}

export function fieldLabel(field: string | null | undefined): string | undefined {
	if (field === undefined || field === null) {
		return undefined;
	}
	const base = field.replace(/\[[^\]]*\]/g, '');
	return FIELD_LABELS[base] ?? FIELD_LABELS[base.split('.')[0] ?? ''];
}

export function translateMessage(message: string, field?: string | null): string {
	const sentence = sentenceFor(message);
	if (sentence !== undefined) {
		return sentence;
	}
	const constraint = constraintFor(message);
	if (constraint === undefined) {
		return message;
	}
	const label = fieldLabel(field);
	return label === undefined ? `Il campo ${constraint}` : `${label}: ${constraint}`;
}

export function translateTitle(title: string): string {
	return TITLES[title] ?? title;
}

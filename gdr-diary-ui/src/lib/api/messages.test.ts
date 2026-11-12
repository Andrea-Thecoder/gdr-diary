import { describe, expect, it } from 'vitest';
import { fieldLabel, translateMessage, translateTitle } from './messages.js';

describe('translateMessage validation constraints', () => {
	it.each([
		['must not be blank', 'name', 'Nome: non può essere vuoto'],
		['must not be empty', 'settings', 'Impostazioni: non può essere vuoto'],
		['must not be null', 'title', 'Titolo: non può essere vuoto'],
		[
			'size must be between 0 and 255',
			'title',
			'Titolo: deve essere lungo al massimo 255 caratteri'
		],
		['size must be between 2 and 9', 'name', 'Nome: deve essere lungo da 2 a 9 caratteri'],
		['must match "^#[0-9A-Fa-f]{6}$"', 'coverColor', 'Colore: deve essere un colore come #3B2A6B'],
		['must match "^[a-z]+$"', 'font', 'Carattere: non ha un formato valido'],
		['must be greater than 0', 'fontSize', 'Dimensione del carattere: deve essere maggiore di 0'],
		['must be greater than or equal to 1', 'page', 'Pagina: deve essere almeno 1'],
		['must be less than or equal to 100', 'size', 'Pagina: deve essere al massimo 100'],
		['Invalid value', 'description', 'Descrizione: non è valido'],
		[
			'contains characters that cannot be stored',
			'campaignName',
			'Campagna: contiene caratteri che non si possono salvare'
		],
		['Required part is missing', 'file', 'File: è obbligatorio'],
		[
			'A diary can have at most 10 different tags',
			null,
			'Un diario può avere al massimo 10 tag diversi: usa uno dei tag già usati.'
		],
		['size must be between 1 and 30', 'tag', 'Tag: deve essere lungo da 1 a 30 caratteri'],
		['must not be blank', 'tag', 'Tag: non può essere vuoto'],
		['size must be between 0 and 30', 'tag', 'Tag: deve essere lungo al massimo 30 caratteri']
	])('turns "%s" on %s into Italian', (message, field, expected) => {
		expect(translateMessage(message, field)).toBe(expected);
	});

	it('speaks of the field in general when there is no label for it', () => {
		expect(translateMessage('must not be blank', 'colour')).toBe('Il campo non può essere vuoto');
	});

	it('speaks of the field in general when there is no field', () => {
		expect(translateMessage('Invalid value')).toBe('Il campo non è valido');
	});
});

describe('translateMessage sentences', () => {
	it.each([
		['Malformed JSON request', 'La richiesta non è leggibile: riprova'],
		['Malformed multipart request', 'Il file non è stato inviato correttamente: riprova'],
		['File too large', 'Il file è troppo grande'],
		['Unsupported file type', 'Tipo di file non supportato'],
		['Empty file', 'Il file è vuoto'],
		['Page content too large', 'Il testo della pagina è troppo lungo'],
		['Settings too large', 'Le impostazioni sono troppo grandi'],
		['The diary is full (100 pages)', 'Il diario è pieno: 100 pagine su 100'],
		[
			'The character has too many sheets (10)',
			'Questo personaggio ha già 10 schede, il massimo: elimina quella che non ti serve più per caricarne un’altra'
		],
		[
			'Another page was added at the same time, try again',
			'Un’altra pagina è stata aggiunta nello stesso momento: riprova'
		],
		['The token has no email address', 'Il tuo account non ha un indirizzo email'],
		['Invalid value for parameter: page', 'Il parametro page non è valido'],
		[
			'Invalid page content at content[0]: unknown node type',
			'Il contenuto della pagina non è valido'
		],
		['Too many files (maximum 3)', 'Puoi allegare al massimo 3 immagini'],
		['Files too large (maximum total 5 MB)', 'Le immagini insieme superano 5 MB: togline qualcuna'],
		[
			'Technical info too large',
			'Le informazioni tecniche sono troppo lunghe: invia il messaggio senza'
		],
		[
			'Technical info is not a valid JSON object',
			'Le informazioni tecniche non sono valide: invia il messaggio senza'
		],
		[
			'Technical info contains characters that cannot be stored',
			'Le informazioni tecniche contengono caratteri che non si possono salvare'
		],
		['the privacy policy must be accepted', 'Serve la spunta per poter inviare il messaggio'],
		['must be idea, bug or love', 'Scegli il tipo di messaggio: idea, problema o complimento'],
		[
			'Too many feedback submissions, try again later',
			'Hai inviato troppi messaggi in poco tempo: riprova più tardi'
		]
	])('turns "%s" into Italian', (message, expected) => {
		expect(translateMessage(message)).toBe(expected);
	});

	it('does not prefix the consent sentence with the field', () => {
		expect(translateMessage('the privacy policy must be accepted', 'consent')).toBe(
			'Serve la spunta per poter inviare il messaggio'
		);
	});

	it('does not prefix a sentence with the field', () => {
		expect(translateMessage('File too large', 'file')).toBe('Il file è troppo grande');
	});
});

describe('translateMessage not found and conflicts', () => {
	it.each([
		['Diary not found', 'Diario non trovato'],
		['Character not found', 'Personaggio non trovato'],
		['Page not found', 'Pagina non trovata'],
		['User not found', 'Utente non trovato'],
		[
			'The diary changed while it was being deleted, try again',
			'Il diario è cambiato mentre lo eliminavi: riprova'
		],
		[
			'The character changed while it was being deleted, try again',
			'Il personaggio è cambiato mentre lo eliminavi: riprova'
		],
		[
			'The account changed while it was being deleted, try again',
			'L’account è cambiato mentre lo eliminavi: riprova'
		],
		[
			'The page was modified elsewhere, reload it',
			'La pagina è stata modificata altrove: ricarica il diario'
		],
		['Authentication required', 'Devi accedere per continuare'],
		['Access denied', 'Non hai il permesso di farlo'],
		['An unexpected error occurred', 'Si è verificato un errore imprevisto: riprova più tardi']
	])('turns "%s" into Italian', (message, expected) => {
		expect(translateMessage(message)).toBe(expected);
	});
});

describe('translateMessage unknown text', () => {
	it('passes through a message it does not know, even with a known field', () => {
		expect(translateMessage('Testo già in italiano', 'name')).toBe('Testo già in italiano');
	});
});

describe('fieldLabel', () => {
	it('uses the first part of a nested field', () => {
		expect(fieldLabel('settings.theme')).toBe('Impostazioni');
	});

	it('knows nothing of a missing field', () => {
		expect(fieldLabel(undefined)).toBeUndefined();
	});
});

describe('translateTitle', () => {
	it('turns a status code into Italian and keeps other titles', () => {
		expect([translateTitle('CONFLICT'), translateTitle('Altro')]).toEqual([
			'I dati sono cambiati nel frattempo: riprova',
			'Altro'
		]);
	});

	it('turns a business message sent as the title into Italian', () => {
		expect(translateTitle('A diary can have at most 10 different tags')).toBe(
			'Un diario può avere al massimo 10 tag diversi: usa uno dei tag già usati.'
		);
	});
});

import { describe, expect, it, vi } from 'vitest';
import { NETWORK_MESSAGE } from '#lib/api/client.js';
import {
	addShots,
	BAD_EMAIL_MESSAGE,
	checkFeedback,
	EMPTY_EMAIL_MESSAGE,
	EMPTY_MESSAGE,
	emptyDraft,
	feedbackForm,
	formatSize,
	hasErrors,
	NO_CONSENT_MESSAGE,
	PLACEHOLDERS,
	screenOf,
	secondsLabel,
	sendFeedback,
	SERVER_MESSAGE,
	SHOT_MAX_BYTES,
	TOO_MANY_MESSAGE,
	type FeedbackDraft
} from './feedback.js';

function png(name = 'a.png', size = 1000): File {
	return new File([new Uint8Array(size)], name, { type: 'image/png' });
}

function valid(): FeedbackDraft {
	return { ...emptyDraft(), message: 'Ciao', email: 'nome@esempio.it', consent: true };
}

describe('checkFeedback', () => {
	it('accepts a message with the consent', () => {
		expect(hasErrors(checkFeedback(valid()))).toBe(false);
	});

	it('asks for a message', () => {
		expect(checkFeedback({ ...valid(), message: '   ' }).message).toBe(EMPTY_MESSAGE);
	});

	it('asks for the consent', () => {
		expect(checkFeedback({ ...valid(), consent: false }).consent).toBe(NO_CONSENT_MESSAGE);
	});

	it('asks for the email', () => {
		expect(checkFeedback({ ...valid(), email: '' }).email).toBe(EMPTY_EMAIL_MESSAGE);
	});

	it('asks for the email when there are only spaces', () => {
		expect(checkFeedback({ ...valid(), email: '   ' }).email).toBe(EMPTY_EMAIL_MESSAGE);
	});

	it('accepts a real looking email', () => {
		expect(checkFeedback({ ...valid(), email: ' nome@esempio.it ' }).email).toBeNull();
	});

	it('refuses an email without the at sign', () => {
		expect(checkFeedback({ ...valid(), email: 'nome.esempio.it' }).email).toBe(BAD_EMAIL_MESSAGE);
	});

	it('reports both problems of an empty draft', () => {
		expect(hasErrors(checkFeedback(emptyDraft()))).toBe(true);
		expect(checkFeedback(emptyDraft())).toEqual({
			message: EMPTY_MESSAGE,
			email: EMPTY_EMAIL_MESSAGE,
			consent: NO_CONSENT_MESSAGE
		});
	});
});

describe('formatSize', () => {
	it('writes small files in KB, at least 1', () => {
		expect(formatSize(10)).toBe('1 KB');
	});

	it('rounds to the KB', () => {
		expect(formatSize(480 * 1024)).toBe('480 KB');
	});

	it('writes big files in MB with a comma', () => {
		expect(formatSize(1.5 * 1024 * 1024)).toBe('1,5 MB');
	});
});

describe('addShots', () => {
	it('adds a good image', () => {
		const result = addShots([], [png()]);

		expect(result.shots).toHaveLength(1);
		expect(result.rejected).toBeNull();
	});

	it('refuses a file that is not a PNG, JPEG or WebP', () => {
		const pdf = new File(['x'], 'scheda.pdf', { type: 'application/pdf' });

		const result = addShots([], [pdf]);

		expect(result.shots).toHaveLength(0);
		expect(result.rejected).toContain('«scheda.pdf» non è un’immagine PNG, JPEG o WebP');
	});

	it('refuses an image over 5 MB', () => {
		const result = addShots([], [png('grande.png', SHOT_MAX_BYTES + 1)]);

		expect(result.shots).toHaveLength(0);
		expect(result.rejected).toContain('«grande.png» pesa più di 5 MB');
	});

	it('refuses the fourth image', () => {
		const result = addShots([png('1.png'), png('2.png'), png('3.png')], [png('4.png')]);

		expect(result.shots).toHaveLength(3);
		expect(result.rejected).toContain('«4.png»');
	});

	it('refuses an image that takes the total over 5 MB', () => {
		const result = addShots([png('1.png', 3 * 1024 * 1024)], [png('2.png', 3 * 1024 * 1024)]);

		expect(result.rejected).toContain('«2.png» non ci sta');
	});

	it('says how much the images already chosen weigh when the next one does not fit', () => {
		const result = addShots([png('1.png', 3 * 1024 * 1024)], [png('2.png', 3 * 1024 * 1024)]);

		expect(result.rejected).toContain('hai già 3,0 MB');
	});

	it('keeps the images already chosen when the next one does not fit', () => {
		const result = addShots([png('1.png', 3 * 1024 * 1024)], [png('2.png', 3 * 1024 * 1024)]);

		expect(result.shots.map((shot) => shot.name)).toEqual(['1.png']);
	});

	it('accepts images that are exactly 5 MB in total', () => {
		const result = addShots([png('1.png', 2 * 1024 * 1024)], [png('2.png', 3 * 1024 * 1024)]);

		expect(result.shots).toHaveLength(2);
	});

	it('adds a smaller image after one that did not fit', () => {
		const result = addShots(
			[png('1.png', 4 * 1024 * 1024)],
			[png('2.png', 2 * 1024 * 1024), png('3.png', 500 * 1024)]
		);

		expect(result.shots.map((shot) => shot.name)).toEqual(['1.png', '3.png']);
	});

	it('keeps the good ones when another is refused', () => {
		const pdf = new File(['x'], 'a.pdf', { type: 'application/pdf' });

		expect(addShots([], [png(), pdf]).shots).toHaveLength(1);
	});

	it('does not change the list it was given', () => {
		const current = [png()];

		addShots(current, [png('b.png')]);

		expect(current).toHaveLength(1);
	});
});

describe('feedbackForm', () => {
	it('sends the type, the trimmed message, the consent and the empty trap', () => {
		const form = feedbackForm({ ...valid(), type: 'idea', message: '  Ciao  ' }, null);

		expect([
			form.get('type'),
			form.get('message'),
			form.get('consent'),
			form.get('website')
		]).toEqual(['idea', 'Ciao', 'true', '']);
	});

	it('sends the email trimmed', () => {
		expect(feedbackForm({ ...valid(), email: ' a@b.it ' }, null).get('email')).toBe('a@b.it');
	});

	it('leaves out the technical information when it was not asked', () => {
		expect(feedbackForm(valid(), null).has('technicalInfo')).toBe(false);
	});

	it('sends the technical information when it was asked', () => {
		expect(feedbackForm(valid(), '{"a":1}').get('technicalInfo')).toBe('{"a":1}');
	});

	it('sends every screenshot in the files part', () => {
		const form = feedbackForm({ ...valid(), shots: [png('1.png'), png('2.png')] }, null);

		expect(form.getAll('files')).toHaveLength(2);
	});
});

describe('sendFeedback', () => {
	it('posts the form to /feedback without any login', async () => {
		const send = vi.fn(() => Promise.resolve(new Response(null, { status: 201 })));

		await sendFeedback('http://api.test', valid(), { fetch: send });

		expect(send).toHaveBeenCalledWith('http://api.test/feedback', {
			method: 'POST',
			body: expect.any(FormData) as FormData
		});
	});

	it('says there are too many messages on a 429', async () => {
		const send = vi.fn(() => Promise.resolve(new Response(null, { status: 429 })));

		await expect(sendFeedback('http://api.test', valid(), { fetch: send })).rejects.toThrow(
			TOO_MANY_MESSAGE
		);
	});

	it('uses the message of the server on a 400', async () => {
		const send = vi.fn(() =>
			Promise.resolve(
				new Response(JSON.stringify({ title: 'Qualcosa non va' }), {
					status: 400,
					headers: { 'Content-Type': 'application/json' }
				})
			)
		);

		await expect(sendFeedback('http://api.test', valid(), { fetch: send })).rejects.toThrow(
			'Qualcosa non va'
		);
	});

	it('translates the reason of a 400 sent by the API', async () => {
		const send = vi.fn(() =>
			Promise.resolve(
				new Response(
					JSON.stringify({
						status: 400,
						title: 'BAD_REQUEST',
						violations: [{ field: null, message: 'Files too large (maximum total 5 MB)' }]
					}),
					{ status: 400, headers: { 'Content-Type': 'application/json' } }
				)
			)
		);

		await expect(sendFeedback('http://api.test', valid(), { fetch: send })).rejects.toThrow(
			'Le immagini insieme superano 5 MB: togline qualcuna'
		);
	});

	it('says the images are too big on a 413', async () => {
		const send = vi.fn(() =>
			Promise.resolve(
				new Response(
					JSON.stringify({
						status: 413,
						title: 'CONTENT_TOO_LARGE',
						violations: [{ field: null, message: 'File too large' }]
					}),
					{ status: 413, headers: { 'Content-Type': 'application/json' } }
				)
			)
		);

		await expect(sendFeedback('http://api.test', valid(), { fetch: send })).rejects.toThrow(
			'Il file è troppo grande'
		);
	});

	it('uses a general message when the answer is not readable', async () => {
		const send = vi.fn(() => Promise.resolve(new Response('boom', { status: 500 })));

		await expect(sendFeedback('http://api.test', valid(), { fetch: send })).rejects.toThrow(
			SERVER_MESSAGE
		);
	});

	it('says the network is down', async () => {
		const send = vi.fn(() => Promise.reject(new TypeError('Failed to fetch')));

		await expect(sendFeedback('http://api.test', valid(), { fetch: send })).rejects.toThrow(
			NETWORK_MESSAGE
		);
	});
});

describe('PLACEHOLDERS', () => {
	it('has a different suggestion for each type', () => {
		expect(new Set(Object.values(PLACEHOLDERS)).size).toBe(3);
	});
});

describe('screenOf', () => {
	it('drops the route groups and keeps the placeholders of the numbers', () => {
		expect(screenOf('/(app)/diaries/[diaryId]')).toBe('/diaries/[diaryId]');
	});

	it('keeps a public route as it is', () => {
		expect(screenOf('/faq')).toBe('/faq');
	});

	it('gives nothing when the visitor came in straight from outside', () => {
		expect(screenOf(null)).toBe('');
		expect(screenOf(undefined)).toBe('');
	});
});

describe('secondsLabel', () => {
	it('writes one second in the singular', () => {
		expect(secondsLabel(1)).toBe('1 secondo');
	});

	it('writes the other counts in the plural', () => {
		expect(secondsLabel(6)).toBe('6 secondi');
		expect(secondsLabel(0)).toBe('0 secondi');
	});
});

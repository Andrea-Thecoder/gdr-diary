import { describe, expect, it, vi } from 'vitest';
import { emptyDraft, sendFeedback, technicalInfo } from './feedback.js';

describe('technicalInfo', () => {
	it('writes the browser, the size of the window and the screen of origin', () => {
		const info = JSON.parse(technicalInfo('/diaries/[diaryId]')) as Record<string, string>;

		expect(Object.keys(info)).toEqual(['userAgent', 'screen', 'from']);
		expect(info['screen']).toMatch(/^\d+x\d+$/);
		expect(info['from']).toBe('/diaries/[diaryId]');
	});

	it('does not write the address of the previous page', () => {
		const info = JSON.parse(technicalInfo('')) as Record<string, string>;

		expect(info['from']).toBe('');
	});
});

describe('sendFeedback with the technical information', () => {
	it('adds it when it was asked', async () => {
		const send = vi.fn<typeof globalThis.fetch>(() =>
			Promise.resolve(new Response(null, { status: 201 }))
		);

		await sendFeedback(
			'http://api.test',
			{ ...emptyDraft(), message: 'Ciao', consent: true, technical: true, from: '/faq' },
			{ fetch: send }
		);

		const body = send.mock.calls[0]?.[1]?.body as FormData;
		expect(body.get('technicalInfo')).toContain('userAgent');
		expect(body.get('technicalInfo')).toContain('"from":"/faq"');
	});

	it('leaves it out when it was not asked', async () => {
		const send = vi.fn<typeof globalThis.fetch>(() =>
			Promise.resolve(new Response(null, { status: 201 }))
		);

		await sendFeedback(
			'http://api.test',
			{ ...emptyDraft(), message: 'Ciao', consent: true },
			{ fetch: send }
		);

		const body = send.mock.calls[0]?.[1]?.body as FormData;
		expect(body.has('technicalInfo')).toBe(false);
	});
});

import { describe, expect, it } from 'vitest';
import { accountName } from '#lib/auth/display.js';

describe('accountName', () => {
	it('prefers the nickname', () => {
		expect(accountName({ nickname: 'lyra', name: 'Lyra Belacqua', email: 'l@e.it' })).toBe('lyra');
	});

	it('falls back to the name and then to the email', () => {
		expect(accountName({ nickname: null, name: 'Lyra Belacqua', email: 'l@e.it' })).toBe(
			'Lyra Belacqua'
		);
		expect(accountName({ nickname: ' ', name: null, email: 'l@e.it' })).toBe('l@e.it');
	});

	it('is empty without a user', () => {
		expect(accountName(null)).toBe('');
		expect(accountName({ nickname: null, name: null, email: null })).toBe('');
	});
});

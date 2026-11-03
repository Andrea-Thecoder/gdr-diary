import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const emailDirectory = path.join(
	path.dirname(fileURLToPath(import.meta.url)),
	'themes',
	'gdr-diary',
	'email'
);

const SUBJECTS = {
	'email-verification': 'emailVerificationSubject',
	'password-reset': 'passwordResetSubject',
	'email-update-confirmation': 'emailUpdateConfirmationSubject',
	executeActions: 'executeActionsSubject',
	'email-test': 'emailTestSubject'
};

function read(...parts) {
	return fs.readFileSync(path.join(emailDirectory, ...parts), 'utf8');
}

function names(kind) {
	return fs
		.readdirSync(path.join(emailDirectory, kind))
		.filter((file) => file.endsWith('.ftl') && file !== 'template.ftl')
		.map((file) => file.replace(/\.ftl$/, ''))
		.sort();
}

function messages() {
	const result = new Map();
	for (const line of read('messages', 'messages_it.properties').split('\n')) {
		const separator = line.indexOf('=');
		if (line.trim() !== '' && !line.startsWith('#') && separator > 0) {
			result.set(line.slice(0, separator), line.slice(separator + 1));
		}
	}
	return result;
}

function usedKeys(source) {
	return [...source.matchAll(/msg\("([^"$]+)"/g)].map((match) => match[1]);
}

describe('email theme', () => {
	it('has a text version for every html email and the other way round', () => {
		expect(names('text')).toEqual(names('html'));
		expect(names('html')).toEqual(Object.keys(SUBJECTS).sort());
	});

	it('inherits everything it does not redefine from the base theme, in Italian', () => {
		const properties = read('theme.properties');

		expect(properties).toContain('parent=base');
		expect(properties).toContain('locales=it');
	});

	it('defines every message the templates use', () => {
		const defined = messages();
		const sources = [
			read('html', 'template.ftl'),
			...names('html').map((name) => read('html', `${name}.ftl`)),
			...names('text').map((name) => read('text', `${name}.ftl`))
		];

		for (const source of sources) {
			for (const key of usedKeys(source)) {
				expect(defined.has(key), key).toBe(true);
			}
		}
	});

	it('gives every email a subject', () => {
		const defined = messages();

		for (const subject of Object.values(SUBJECTS)) {
			expect(defined.get(subject)?.trim() ?? '', subject).not.toBe('');
		}
	});

	it('never uses a straight apostrophe, which the message formatter would swallow', () => {
		for (const [key, value] of messages()) {
			expect(value.includes("'"), key).toBe(false);
		}
	});

	it('escapes what it prints in the html and keeps the text plain', () => {
		for (const name of names('html')) {
			expect(read('html', `${name}.ftl`).startsWith('<#ftl output_format="HTML">'), name).toBe(
				true
			);
		}
		expect(read('html', 'template.ftl').startsWith('<#ftl output_format="HTML">')).toBe(true);
		for (const name of names('text')) {
			expect(read('text', `${name}.ftl`).startsWith('<#ftl output_format="plainText">'), name).toBe(
				true
			);
		}
	});

	it('does not switch the escaping off anywhere', () => {
		for (const name of names('html')) {
			expect(read('html', `${name}.ftl`), name).not.toContain('?no_esc');
		}
		expect(read('html', 'template.ftl')).not.toContain('?no_esc');
	});

	it('has a dark version and a layout for narrow screens', () => {
		const layout = read('html', 'template.ftl');

		expect(layout).toContain('prefers-color-scheme: dark');
		expect(layout).toContain('max-width: 620px');
		expect(layout).toContain('name="color-scheme"');
	});

	it('shows the link as a button in the html and in full in the plain text, never as a long printed address', () => {
		for (const name of names('html').filter((entry) => entry !== 'email-test')) {
			const html = read('html', `${name}.ftl`);

			expect(html, name).toContain('<@layout.action href=link');
			expect(html, name).not.toContain('${link}');
			expect(read('text', `${name}.ftl`), name).toContain('${link}');
		}
	});
});

import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const login = new URL('./themes/gdr-diary/login/', import.meta.url);
const read = (name) => readFileSync(new URL(name, login), 'utf8');

describe('login theme: email change page', () => {
	const page = read('update-email.ftl');

	it('does not offer to sign out the other devices', () => {
		expect(page).not.toContain('logout-sessions');
		expect(page).not.toContain('logoutOtherSessions');
	});

	it('can be cancelled only when the user asked for it, and the cancel button comes after the submit', () => {
		expect(page).toContain('<#if isAppInitiatedAction??>');
		expect(page).toContain('name="cancel-aia"');
		expect(page.indexOf('type="submit" class')).toBeLessThan(page.indexOf('name="cancel-aia"'));
	});

	it('posts to the login action with the profile fields of the realm', () => {
		expect(page).toContain('action="${url.loginAction}"');
		expect(page).toContain('userProfileCommons.userProfileFormFields');
	});
});

describe('login theme: username line', () => {
	it('hides the restart link while Keycloak asks for the password for an action', () => {
		const layout = read('template.ftl');
		const guard = layout.indexOf('<#if !isAppInitiatedAction??>');

		expect(guard).toBeGreaterThan(-1);
		expect(guard).toBeLessThan(layout.indexOf('id="reset-login"'));
	});

	it('words the restart link so that it is understood', () => {
		expect(read('messages/messages_it.properties')).toContain('restartLoginTooltip=Non sei tu?');
	});
});

describe('login theme: information page', () => {
	const page = read('info.ftl');

	it('goes back to the app by itself after the email change was asked and after a confirmation', () => {
		expect(page).toContain('isAppInitiatedAction??');
		expect(page).toContain('emailUpdateConfirmationSentTitle');
		expect(page).toContain('data-gdr-redirect=');
		expect(page).toContain('(message.type)!""');
		expect(page).toContain('== "success"');
	});

	it('never leaves a page that lists actions the user still has to do', () => {
		expect(page).toContain('!requiredActions??');
	});

	it('always offers a button to go back when Keycloak knows where the app is', () => {
		expect(page).toContain('client.baseUrl');
		expect(page).toContain('gdrBackToApp');
		expect(read('messages/messages_it.properties')).toContain('gdrBackToApp=Torna al diario');
	});

	it('words the old link page without the part about having verified the email', () => {
		expect(read('messages/messages_it.properties')).toContain(
			'staleEmailVerificationLink=Il link che hai cliccato è scaduto e non è più valido.\n'
		);
	});

	it('has the script that follows the redirect', () => {
		expect(read('resources/js/gdr.js')).toContain('dataset.gdrRedirect');
	});

	it('finds the way back to the app in the flow when the client has no home URL', () => {
		expect(page).toContain('data-gdr-redirect-from-flow');
		expect(page).toContain('data-gdr-flow-redirect');
		const script = read('resources/js/gdr.js');
		expect(script).toContain("get('client_data')");
		expect(script).toContain('.ru');
	});
});

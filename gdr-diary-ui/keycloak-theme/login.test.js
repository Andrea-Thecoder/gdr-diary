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

describe('login theme: delete account page', () => {
	const page = read('delete-account-confirm.ftl');
	const messages = read('messages/messages_it.properties');
	const script = read('resources/js/gdr.js');

	it('posts to the login action with the name Keycloak expects for confirming', () => {
		expect(page).toContain('action="${url.loginAction}"');
		expect(page).toContain('<input type="hidden" name="accept" value="true">');
	});

	it('asks nothing a second time: no cancel button and no warning to confirm', () => {
		expect(page).not.toContain('cancel-aia');
		expect(page).not.toContain('kc-decline');
		expect(messages).not.toContain('gdrDeleteIrreversible');
		expect(messages).not.toContain('gdrDeleteCancelNote');
	});

	it('sends the form by itself, once, and keeps a button for browsers without scripts', () => {
		expect(page).toContain('data-gdr-auto-submit');
		expect(script).toContain('form[data-gdr-auto-submit]');
		expect(script).toContain('form.submit()');
		expect(page).toMatch(/<noscript>[\s\S]*type="submit"[\s\S]*<\/noscript>/);
	});

	it('tells assistive technology that something is happening', () => {
		expect(page).toContain('aria-labelledby="kc-page-title"');
		expect(page).toContain('role="status" aria-live="polite"');
	});

	it('uses only messages that exist', () => {
		const keys = [...page.matchAll(/msg\("([^"]+)"\)/g)].map((match) => match[1]);
		expect(keys.length).toBeGreaterThan(0);
		for (const key of keys.filter((name) => name.startsWith('gdr'))) {
			expect(messages).toContain(`${key}=`);
		}
	});
});

describe('login theme: register page', () => {
	const page = read('register.ftl');

	it('always asks for the password and its confirmation, or the user is born without one', () => {
		expect(page).not.toContain('<#if passwordRequired??>');
		expect(page).toContain('name="password"');
		expect(page).toContain('name="password-confirm"');
		expect(page).toMatch(/id="password" name="password" required/);
		expect(page).toMatch(/id="password-confirm" name="password-confirm" required/);
		expect(page).toContain('<@layout.passwordToggle target="password"/>');
		expect(page).toContain('<@layout.passwordToggle target="password-confirm"/>');
	});
});

describe('login theme: update password page', () => {
	const page = read('login-update-password.ftl');
	const theme = read('theme.properties');

	it('has the eye on both password fields, like the login and register pages', () => {
		expect(page).toContain('<@layout.passwordToggle target="password-new"/>');
		expect(page).toContain('<@layout.passwordToggle target="password-confirm"/>');
	});

	it('posts the names Keycloak expects and starts on the new password', () => {
		expect(page).toContain('action="${url.loginAction}"');
		expect(page).toContain('name="password-new"');
		expect(page).toContain('name="password-confirm"');
		expect(page).toMatch(/id="password-new"[^>]*autofocus/);
		expect(page).toContain('autocomplete="new-password"');
	});

	it('links each error to its field for screen readers', () => {
		expect(page).toContain('aria-describedby="input-error-password"');
		expect(page).toContain('aria-describedby="input-error-password-confirm"');
	});

	it('offers to sign out the other devices only when the app asked for the change, away from the buttons', () => {
		expect(page.indexOf('<#if isAppInitiatedAction??>')).toBeLessThan(
			page.indexOf('logout-sessions')
		);
		expect(page.indexOf('logout-sessions')).toBeLessThan(page.indexOf('id="kc-form-buttons"'));
		expect(page).toContain('name="cancel-aia"');
	});

	it('keeps a focus ring that can be read on the paper of the fields, not gold on parchment', () => {
		expect(theme).toMatch(
			/kcFormPasswordVisibilityButtonClass = [^\n]*focus-visible:outline-\(color:--page-ink\)/
		);
		expect(theme).toMatch(/kcInputClass = [^\n]*focus-visible:border-\(color:--page-ink\)/);
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

	it('sends a deleted account back to the home, not to a diary that no longer exists', () => {
		expect(page).toContain('msg("accountDeletedMessage")');
		expect(page).toContain('accountDeleted?then("gdrBackToHome", "gdrBackToApp")');
		expect(page).toContain('accountDeleted?then("gdrRedirectingHome", "gdrRedirecting")');
		expect(page).not.toContain('msg("gdrBackToApp")');
		const messages = read('messages/messages_it.properties');
		expect(messages).toContain('gdrBackToHome=Torna alla home');
		expect(messages).toContain('gdrRedirectingHome=Ti riporto alla home');
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

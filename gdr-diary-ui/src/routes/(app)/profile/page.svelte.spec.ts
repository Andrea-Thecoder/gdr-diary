import { page, userEvent } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../layout.css';

const { logout, close, recent, reauthenticate, navigate } = vi.hoisted(() => ({
	logout: vi.fn(() => Promise.resolve()),
	close: vi.fn(() => Promise.resolve()),
	recent: vi.fn<() => boolean>(),
	reauthenticate: vi.fn<(returnTo: string) => Promise<void>>(),
	navigate: vi.fn<(...args: unknown[]) => void>()
}));

vi.mock('$app/navigation', () => ({ goto: navigate }));
vi.mock('#lib/profile/account.js', () => ({ closeAccount: close }));

vi.mock('$app/state', () => ({ page: { url: { pathname: '/profile' } } }));
const { change, startEmail, finishEmail, resendEmail, removeAccount, session } = vi.hoisted(() => ({
	session: { pending: null as string | null },
	change: vi.fn<(...args: unknown[]) => Promise<string | null>>(),
	startEmail: vi.fn<(returnTo: string) => Promise<void>>(),
	finishEmail: vi.fn<(shown: string) => Promise<unknown>>(),
	resendEmail: vi.fn<() => Promise<string>>(),
	removeAccount: vi.fn<(...args: unknown[]) => Promise<void>>()
}));

vi.mock('#lib/profile/email.js', () => ({
	startEmailChange: startEmail,
	finishEmailChange: finishEmail,
	resendEmailChange: resendEmail
}));

const { requestPassword } = vi.hoisted(() => ({
	requestPassword: vi.fn<(returnTo: string) => Promise<string>>()
}));

vi.mock('#lib/profile/password.js', async (importOriginal) => ({
	...(await importOriginal<Record<string, unknown>>()),
	requestPasswordChange: requestPassword
}));

vi.mock('#lib/api/index.js', () => ({ getApi: () => ({}) }));
vi.mock('#lib/profile/api.js', async (importOriginal) => ({
	...(await importOriginal<Record<string, unknown>>()),
	changeAvatar: change,
	deleteAccount: removeAccount
}));
vi.mock('#lib/auth/auth.svelte.js', () => ({
	auth: {
		logout,
		recentlyAuthenticated: recent,
		reauthenticate,
		get user() {
			return {
				nickname: 'lyra',
				name: null,
				email: 'lyra@esempio.it',
				pendingEmail: session.pending
			};
		}
	}
}));

import { RecentLoginRequiredError } from '#lib/profile/api.js';
import { profileAvatar } from '#lib/profile/avatar.svelte.js';
import { lastPlace } from '#lib/ui/last-place.svelte.js';
import { required } from '#lib/test-support.js';
import { chooseAndCrop, samplePng } from '#lib/ui/test-image.js';
import Page from './+page.svelte';

const id = '3f2b8c1e-5a47-4d0e-9b6a-1c2d3e4f5a6b';

function renderPage(premium = false): void {
	render(Page, {
		data: { profile: { email: 'lyra@esempio.it', premium }, id },
		params: {}
	} as never);
}

describe('profile page', () => {
	beforeEach(async () => {
		logout.mockClear();
		close.mockClear();
		recent.mockReset();
		recent.mockReturnValue(true);
		reauthenticate.mockReset();
		reauthenticate.mockResolvedValue();
		navigate.mockReset();
		window.history.replaceState(null, '', '/');
		lastPlace.record(new URL('/', window.location.origin));
		session.pending = null;
		startEmail.mockReset();
		startEmail.mockResolvedValue();
		finishEmail.mockReset();
		resendEmail.mockReset();
		resendEmail.mockResolvedValue('sent');
		finishEmail.mockResolvedValue({ status: 'none' });
		requestPassword.mockReset();
		requestPassword.mockResolvedValue('sent');
		removeAccount.mockReset();
		removeAccount.mockResolvedValue();
		change.mockReset();
		change.mockResolvedValue('http://img.test/new.png');
		profileAvatar.url = null;
		await page.viewport(1280, 800);
	});

	it('shows the title and a way back to the characters', async () => {
		renderPage();

		await expect
			.element(page.getByRole('heading', { level: 1 }))
			.toHaveTextContent('Il tuo profilo');
		await expect
			.element(page.getByRole('link', { name: 'Tutti i personaggi' }))
			.toHaveAttribute('href', '/');
		expect(page.getByRole('navigation', { name: 'Area personale' }).elements()).toHaveLength(0);
	});

	it('leads back to the page the user was on before opening the profile', async () => {
		lastPlace.record(new URL('/diaries/d1?vista=2', window.location.origin));
		lastPlace.record(new URL('/settings', window.location.origin));
		renderPage();

		await expect
			.element(page.getByRole('link', { name: 'Torna al diario' }))
			.toHaveAttribute('href', '/diaries/d1?vista=2');
	});

	it('greets the account by nickname, not by email', async () => {
		renderPage();

		await expect.element(page.getByText('lyra', { exact: true })).toBeVisible();
	});

	it('shows email, plan and identifier as read-only values', async () => {
		renderPage();

		await expect.element(page.getByLabelText('Email')).toHaveTextContent('lyra@esempio.it');
		await expect.element(page.getByLabelText('Piano')).toHaveTextContent('Gratuito');
		await expect.element(page.getByLabelText('Identificativo')).toHaveTextContent(id);
	});

	it('shows the premium plan', async () => {
		renderPage(true);

		await expect.element(page.getByLabelText('Piano')).toHaveTextContent('Premium');
		await expect.element(page.getByText('Piano premium')).toBeVisible();
	});

	it('logs out from the summary card', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Esci', exact: true }).click();

		expect(logout).toHaveBeenCalledOnce();
	});

	it('does not scroll horizontally on a phone', async () => {
		await page.viewport(390, 844);
		renderPage();
		await expect.element(page.getByRole('heading', { level: 1 })).toBeVisible();

		expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(390);
	});
	it('does not show the email next to the photo', async () => {
		renderPage();

		await expect.element(page.getByLabelText('Email')).toHaveTextContent('lyra@esempio.it');
		const summary = page.getByRole('region', { name: 'Riepilogo dell’account' }).element();
		expect(summary.textContent).not.toContain('lyra@esempio.it');
	});

	it('has a quill on the photo that opens the image dialog', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Cambia l’immagine del profilo' }).click();

		await expect.element(page.getByRole('dialog')).toBeVisible();
		await expect.element(page.getByRole('heading', { name: 'Immagine del profilo' })).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Salva l’immagine' })).toBeDisabled();
	});

	it('uploads the chosen image, shows it and says so', async () => {
		renderPage();
		await page.getByRole('button', { name: 'Cambia l’immagine del profilo' }).click();
		await chooseAndCrop(await samplePng('io.png'));

		await page.getByRole('button', { name: 'Salva l’immagine' }).click();

		await expect.poll(() => change.mock.calls.length).toBe(1);
		const sent = change.mock.calls[0]?.[1] as { type: string; file: File };
		expect(sent.type).toBe('replace');
		expect(sent.file.name).toBe('io.webp');
		expect(profileAvatar.url).toBe('http://img.test/new.png');
		await expect.element(page.getByText('Immagine del profilo salvata.')).toBeVisible();
	});

	it('removes the current image', async () => {
		profileAvatar.url = 'http://img.test/old.png';
		change.mockResolvedValue(null);
		renderPage();
		await page.getByRole('button', { name: 'Cambia l’immagine del profilo' }).click();

		await page.getByRole('button', { name: 'Rimuovi' }).click();
		await page.getByRole('button', { name: 'Salva l’immagine' }).click();

		await expect.poll(() => change.mock.calls.length).toBe(1);
		expect(change.mock.calls[0]?.[1]).toEqual({ type: 'remove' });
		expect(profileAvatar.url).toBeNull();
	});

	describe('while the image is being uploaded', () => {
		let finish: (value: string | null) => void = () => undefined;

		async function savePending(): Promise<void> {
			change.mockImplementation(
				() =>
					new Promise<string | null>((resolve) => {
						finish = resolve;
					})
			);
			renderPage();
			await page.getByRole('button', { name: 'Cambia l’immagine del profilo' }).click();
			await chooseAndCrop(await samplePng('io.png'));
			await page.getByRole('button', { name: 'Salva l’immagine' }).click();
			await vi.waitFor(() => {
				expect(change).toHaveBeenCalledTimes(1);
			});
		}

		it('stays open when Escape is pressed', async () => {
			await savePending();

			await userEvent.keyboard('{Escape}');

			await expect.element(page.getByRole('dialog')).toBeVisible();
		});

		it('stays open when the backdrop is pressed and released', async () => {
			await savePending();
			const dialog = page.getByRole('dialog').element();

			dialog.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
			dialog.dispatchEvent(new MouseEvent('click', { bubbles: true }));

			await expect.element(page.getByRole('dialog')).toBeVisible();
		});

		it('closes as usual after the upload has finished', async () => {
			await savePending();
			await userEvent.keyboard('{Escape}');

			finish('http://img.test/new.png');

			await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
		});
	});

	it('keeps the dialog open and shows the server message when the upload fails', async () => {
		change.mockRejectedValue(new Error('File troppo grande'));
		renderPage();
		await page.getByRole('button', { name: 'Cambia l’immagine del profilo' }).click();
		await chooseAndCrop(await samplePng('io.png'));

		await page.getByRole('button', { name: 'Salva l’immagine' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('File troppo grande');
		await expect.element(page.getByRole('dialog')).toBeVisible();
	});
	it('sends the user to Keycloak to change the email, coming back to the profile', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Cambia email' }).click();

		await expect.poll(() => startEmail.mock.calls.length).toBe(1);
		expect(startEmail.mock.calls[0]?.[0]).toMatch(/\/profile$/);
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('writes to the user to change the password, which stays as it was until the link is opened', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Cambia password' }).click();

		await expect.poll(() => requestPassword.mock.calls.length).toBe(1);
		expect(requestPassword.mock.calls[0]?.[0]).toMatch(/\/profile$/);
		await expect
			.element(page.getByText(/apri il link per scegliere la nuova password/))
			.toBeVisible();
		await expect
			.element(page.getByRole('button', { name: /Rinvia email tra \d+ s/ }))
			.toBeDisabled();
	});

	it('says so when the email could not be sent and lets the user try again', async () => {
		requestPassword.mockResolvedValue('failed');
		renderPage();

		await page.getByRole('button', { name: 'Cambia password' }).click();

		await expect
			.element(page.getByText('Non riesco a mandare l’email. Riprova tra poco.'))
			.toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Cambia password' })).toBeEnabled();
	});

	it('asks to wait when the last email was sent less than a minute ago', async () => {
		requestPassword.mockResolvedValue('too-soon');
		renderPage();

		await page.getByRole('button', { name: 'Cambia password' }).click();

		await expect
			.element(page.getByText('Aspetta un minuto prima di chiederla di nuovo.'))
			.toBeVisible();
	});

	it('says the password changed when Keycloak brings the user back after the link', async () => {
		window.history.replaceState(null, '', '/profile?password=cambiata');
		renderPage();

		await expect.element(page.getByText('Password cambiata.')).toBeVisible();
		expect(navigate).toHaveBeenCalled();
	});

	it('shows the new email when coming back from Keycloak with the change done', async () => {
		finishEmail.mockResolvedValue({
			status: 'changed',
			email: 'nuova@esempio.it'
		});
		renderPage();

		await expect.element(page.getByLabelText('Email')).toHaveTextContent('nuova@esempio.it');
		await expect.element(page.getByText('Email cambiata.')).toBeVisible();
		expect(finishEmail).toHaveBeenCalledWith('lyra@esempio.it');
	});

	it('asks to open the link when Keycloak waits for the confirmation', async () => {
		finishEmail.mockResolvedValue({ status: 'pending' });
		renderPage();

		await expect.element(page.getByText(/apri il link per confermarlo/)).toBeVisible();
		await expect.element(page.getByLabelText('Email')).toHaveTextContent('lyra@esempio.it');
	});

	it('shows why the change failed', async () => {
		finishEmail.mockResolvedValue({
			status: 'failed',
			message: 'Keycloak non ha cambiato.'
		});
		renderPage();

		await expect.element(page.getByText('Keycloak non ha cambiato.')).toBeVisible();
	});

	it('shows the new address with a warning while Keycloak waits for its confirmation', async () => {
		session.pending = 'nuova@esempio.it';
		renderPage();

		await expect.element(page.getByLabelText('Email')).toHaveTextContent('nuova@esempio.it');
		await expect.element(page.getByRole('status')).toHaveTextContent('Da verificare');
		await expect.element(page.getByRole('status')).toHaveTextContent('lyra@esempio.it');
		expect(document.querySelectorAll('svg.text-warn')).toHaveLength(1);
	});

	it('resends the confirmation mail without asking the address again', async () => {
		session.pending = 'nuova@esempio.it';
		renderPage();

		await page.getByRole('button', { name: 'Rinvia email' }).click();

		await expect.element(page.getByText(/Ti abbiamo scritto di nuovo/)).toBeVisible();
		expect(resendEmail).toHaveBeenCalledOnce();
		expect(startEmail).not.toHaveBeenCalled();
	});

	it('turns the resend button off and counts down after sending', async () => {
		session.pending = 'nuova@esempio.it';
		renderPage();

		await page.getByRole('button', { name: 'Rinvia email' }).click();

		const button = page.getByRole('button', { name: /Rinvia email tra \d+ s/ });
		await expect.element(button).toBeDisabled();
		await expect.element(button).toHaveTextContent('tra 60 s');
		await expect.poll(() => document.body.textContent, { timeout: 3000 }).toMatch(/tra (59|58) s/);
	});

	it('asks to wait when the mail was sent less than a minute ago', async () => {
		session.pending = 'nuova@esempio.it';
		resendEmail.mockResolvedValue('too-soon');
		renderPage();

		await page.getByRole('button', { name: 'Rinvia email' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Aspetta un minuto');
	});

	it('says so when the mail could not be resent', async () => {
		session.pending = 'nuova@esempio.it';
		resendEmail.mockResolvedValue('failed');
		renderPage();

		await page.getByRole('button', { name: 'Rinvia email' }).click();

		await expect.element(page.getByRole('alert')).toHaveTextContent('Non riesco a rinviare');
	});

	it('has no resend button when no change is waiting', async () => {
		renderPage();

		await expect
			.element(page.getByRole('button', { name: 'Rinvia email' }))
			.not.toBeInTheDocument();
	});

	it('shows no warning when no change is waiting', async () => {
		renderPage();

		await expect.element(page.getByText('Da verificare')).not.toBeInTheDocument();
		expect(document.querySelectorAll('svg.text-warn')).toHaveLength(0);
	});

	it('says nothing when the user did not come back from the email page', async () => {
		renderPage();

		await expect.element(page.getByLabelText('Email')).toHaveTextContent('lyra@esempio.it');
		await expect.element(page.getByText('Email cambiata.')).not.toBeInTheDocument();
	});

	it('asks for confirmation before deleting the account and says it cannot be undone', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Elimina account' }).click();

		await expect.element(page.getByRole('dialog')).toBeVisible();
		await expect.element(page.getByRole('heading', { name: 'Eliminare l’account?' })).toBeVisible();
		await expect
			.element(page.getByText('Non si può annullare e non c’è modo di recuperare i dati.'))
			.toBeVisible();
		await expect.element(page.getByText('tutti i tuoi personaggi;')).toBeVisible();
		expect(removeAccount).not.toHaveBeenCalled();
	});

	it('asks for the password again before the dialog when the login is not recent', async () => {
		recent.mockReturnValue(false);
		renderPage();

		await page.getByRole('button', { name: 'Elimina account' }).click();

		await expect.poll(() => reauthenticate.mock.calls.length).toBe(1);
		const back = new URL(reauthenticate.mock.calls[0]?.[0] ?? '');
		expect(back.pathname).toBe('/profile');
		expect(back.searchParams.get('elimina')).toBe('1');
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('does not ask for the password twice when it was just typed', async () => {
		renderPage();

		await page.getByRole('button', { name: 'Elimina account' }).click();

		await expect.element(page.getByRole('dialog')).toBeVisible();
		expect(reauthenticate).not.toHaveBeenCalled();
	});

	it('opens the dialog when coming back from the password page, and cleans the address', async () => {
		window.history.replaceState(null, '', '/?elimina=1');
		renderPage();

		await expect.element(page.getByRole('heading', { name: 'Eliminare l’account?' })).toBeVisible();
		expect(navigate).toHaveBeenCalledWith('/profile', {
			shallow: true,
			replace: true
		});
	});

	it('says in advance that the password will be asked', async () => {
		renderPage();

		await expect.element(page.getByText(/ti chiederemo di nuovo la password/)).toBeVisible();
	});

	it('does nothing when the deletion is cancelled', async () => {
		renderPage();
		await page.getByRole('button', { name: 'Elimina account' }).click();

		await page.getByRole('button', { name: 'Annulla' }).click();

		expect(removeAccount).not.toHaveBeenCalled();
		expect(close).not.toHaveBeenCalled();
		await expect.element(page.getByRole('dialog')).not.toBeInTheDocument();
	});

	it('deletes the data and then sends the user to Keycloak to close the account once confirmed', async () => {
		renderPage();
		await page.getByRole('button', { name: 'Elimina account' }).click();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.poll(() => removeAccount.mock.calls.length).toBe(1);
		await expect.poll(() => close.mock.calls.length).toBe(1);
		expect(logout).not.toHaveBeenCalled();
	});

	it('asks for the password again when the server wants a more recent login', async () => {
		removeAccount.mockRejectedValue(new RecentLoginRequiredError());
		renderPage();
		await page.getByRole('button', { name: 'Elimina account' }).click();
		recent.mockReturnValue(false);

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect.poll(() => reauthenticate.mock.calls.length).toBe(1);
		expect(reauthenticate.mock.calls[0]?.[0]).toContain('/profile?elimina=1');
		expect(close).not.toHaveBeenCalled();
	});

	it('does not loop when the server refuses even after a recent login', async () => {
		removeAccount.mockRejectedValue(new RecentLoginRequiredError());
		renderPage();
		await page.getByRole('button', { name: 'Elimina account' }).click();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Non riesco a confermare il tuo accesso');
		expect(reauthenticate).not.toHaveBeenCalled();
		expect(close).not.toHaveBeenCalled();
	});

	it('keeps the account and shows the problem when the deletion fails', async () => {
		removeAccount.mockRejectedValue(
			new Error('Non riesco a eliminare l’account. Riprova più tardi.')
		);
		renderPage();
		await page.getByRole('button', { name: 'Elimina account' }).click();

		await page.getByRole('button', { name: 'Elimina per sempre' }).click();

		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent('Non riesco a eliminare l’account. Riprova più tardi.');
		expect(close).not.toHaveBeenCalled();
		await expect.element(page.getByRole('button', { name: 'Elimina per sempre' })).toBeEnabled();
	});
});

describe('profile page on tablets', () => {
	beforeEach(() => {
		session.pending = null;
	});

	function rect(element: Element): DOMRect {
		return element.getBoundingClientRect();
	}

	function summarySection(): Element {
		return required(document.querySelector('section[aria-label="Riepilogo dell’account"]'));
	}

	function accountSection(): Element {
		return required(
			[...document.querySelectorAll('section')].find(
				(section) => section !== summarySection() && section.textContent.includes('Il tuo account')
			)
		);
	}

	function logoutIn(summary: Element): Element {
		return required(
			[...summary.querySelectorAll('button')].find((button) => button.textContent.trim() === 'Esci')
		);
	}

	it('puts the summary above the cards at full width below 900 px', async () => {
		await page.viewport(768, 1024);
		renderPage();

		const summary = summarySection();
		const account = accountSection();

		expect(rect(summary).bottom).toBeLessThanOrEqual(rect(account).top);
		expect(Math.abs(rect(summary).width - rect(account).width)).toBeLessThan(2);
	});

	it('lays the summary out as a row between 641 and 899 px', async () => {
		await page.viewport(768, 1024);
		renderPage();

		const summary = summarySection();
		const avatar = required(summary.querySelector('[class*="size-[104px]"]'));
		const logoutButton = logoutIn(summary);

		expect(rect(avatar).left).toBeLessThan(rect(logoutButton).left);
		expect(
			Math.abs(
				rect(avatar).top +
					rect(avatar).height / 2 -
					(rect(logoutButton).top + rect(logoutButton).height / 2)
			)
		).toBeLessThan(60);
		expect(rect(logoutButton).width).toBeLessThan(rect(summary).width / 2);
	});

	it('keeps the summary as a centred column on a phone', async () => {
		await page.viewport(390, 844);
		renderPage();

		const summary = summarySection();
		const logoutButton = logoutIn(summary);

		expect(rect(logoutButton).width).toBeGreaterThan(rect(summary).width * 0.7);
	});

	it('shows the summary next to the cards from 900 px', async () => {
		await page.viewport(1024, 800);
		renderPage();

		const summary = summarySection();
		const account = accountSection();

		expect(rect(summary).right).toBeLessThanOrEqual(rect(account).left);
		expect(rect(summary).width).toBeLessThanOrEqual(341);
	});
});

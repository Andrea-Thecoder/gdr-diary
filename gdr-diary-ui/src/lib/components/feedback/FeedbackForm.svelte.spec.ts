import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { FeedbackDraft } from '#lib/feedback/feedback.js';
import FeedbackForm from './FeedbackForm.svelte';

const send = vi.fn<(draft: FeedbackDraft) => Promise<void>>();
const leave = vi.fn<() => void>();

async function fillValid(): Promise<void> {
	await page.getByLabelText('Il tuo messaggio').fill('Il segnalibro non si sposta.');
	await page.getByLabelText('La tua email').fill('nome@esempio.it');
	await page.getByRole('checkbox', { name: /Ho letto/ }).click();
}

describe('FeedbackForm', () => {
	beforeEach(async () => {
		send.mockReset();
		leave.mockReset();
		send.mockResolvedValue();
		await page.viewport(1280, 900);
	});

	it('offers the three kinds, with a problem already chosen', async () => {
		render(FeedbackForm, { send });

		await expect.element(page.getByRole('radio', { name: /Un’idea/ })).not.toBeChecked();
		await expect.element(page.getByRole('radio', { name: /Un problema/ })).toBeChecked();
		await expect.element(page.getByRole('radio', { name: /Un complimento/ })).not.toBeChecked();
	});

	it('draws the three kinds as cards of the same size', () => {
		render(FeedbackForm, { send });
		const cards = Array.from(document.querySelectorAll('input[type="radio"] + span')).map(
			(card) => {
				const box = card.getBoundingClientRect();
				return [Math.round(box.width), Math.round(box.height)];
			}
		);

		expect(cards).toHaveLength(3);
		expect(new Set(cards.map(([width]) => width)).size).toBe(1);
		expect(new Set(cards.map(([, height]) => height)).size).toBe(1);
	});

	it('keeps the cards of the same size on a narrow screen', async () => {
		await page.viewport(390, 900);
		render(FeedbackForm, { send });
		const heights = Array.from(document.querySelectorAll('input[type="radio"] + span')).map(
			(card) => Math.round(card.getBoundingClientRect().height)
		);

		expect(new Set(heights).size).toBe(1);
	});

	it('changes the suggestion in the message when the kind changes', async () => {
		render(FeedbackForm, { send });

		await page.getByText('Un complimento').click();

		await expect
			.element(page.getByLabelText('Il tuo messaggio'))
			.toHaveAttribute('placeholder', 'Cosa ti è piaciuto di più?');
	});

	it('counts the characters of the message', async () => {
		render(FeedbackForm, { send });

		await page.getByLabelText('Il tuo messaggio').fill('Ciao');

		await expect.element(page.getByText('4 / 2000')).toBeVisible();
	});

	it('does not send an empty form and says what is missing', async () => {
		render(FeedbackForm, { send });

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send).not.toHaveBeenCalled();
		await expect
			.element(page.getByText('Scrivi almeno qualche parola: il messaggio è obbligatorio.'))
			.toBeVisible();
		await expect
			.element(page.getByText('Serve la spunta per poter inviare il messaggio.'))
			.toBeVisible();
	});

	it('asks for the email when it is missing', async () => {
		render(FeedbackForm, { send });
		await page.getByLabelText('Il tuo messaggio').fill('Ciao');
		await page.getByRole('checkbox', { name: /Ho letto/ }).click();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send).not.toHaveBeenCalled();
		await expect
			.element(page.getByText('Scrivi la tua email: mi serve per risponderti.'))
			.toBeVisible();
	});

	it('shows a banner above the form when something is missing', async () => {
		render(FeedbackForm, { send });

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect
			.element(page.getByText('Il messaggio non è partito: controlla i campi segnati qui sotto.'))
			.toBeVisible();
	});

	it('does not send without the consent', async () => {
		render(FeedbackForm, { send });
		await page.getByLabelText('Il tuo messaggio').fill('Ciao');

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send).not.toHaveBeenCalled();
	});

	it('refuses an email that is not an email', async () => {
		render(FeedbackForm, { send });
		await fillValid();
		await page.getByLabelText('La tua email').fill('non-una-email');

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send).not.toHaveBeenCalled();
		await expect.element(page.getByText(/non sembra un’email/)).toBeVisible();
	});

	it('sends the message with the kind and the consent', async () => {
		render(FeedbackForm, { send });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send).toHaveBeenCalledOnce();
		expect(send.mock.calls[0]?.[0]).toMatchObject({
			type: 'bug',
			message: 'Il segnalibro non si sposta.',
			consent: true,
			technical: false,
			website: ''
		});
	});

	it('hands over the screen the visitor came from', async () => {
		render(FeedbackForm, { send, from: '/faq' });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send.mock.calls[0]?.[0].from).toBe('/faq');
	});

	it('sends the technical information only when its box is checked', async () => {
		render(FeedbackForm, { send });
		await fillValid();
		await page.getByRole('checkbox', { name: /Allega i dati tecnici/ }).click();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		expect(send.mock.calls[0]?.[0].technical).toBe(true);
	});

	it('thanks the visitor once the message is sent', async () => {
		render(FeedbackForm, { send });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect
			.element(page.getByRole('heading', { name: 'Grazie, messaggio ricevuto.' }))
			.toBeVisible();
		expect(page.getByRole('form', { name: 'Modulo di feedback' }).elements()).toHaveLength(0);
	});

	it('shows the error of the server and keeps what was written', async () => {
		send.mockRejectedValue(new Error('Troppi invii.'));
		render(FeedbackForm, { send });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect.element(page.getByRole('alert').first()).toHaveTextContent('Troppi invii.');
		await expect
			.element(page.getByLabelText('Il tuo messaggio'))
			.toHaveValue('Il segnalibro non si sposta.');
	});

	it('disables the button while the message is on its way', async () => {
		send.mockReturnValue(new Promise(() => undefined));
		render(FeedbackForm, { send });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect.element(page.getByRole('button', { name: 'Invio in corso' })).toBeDisabled();
	});

	it('offers only the way back to the home page once the message is sent', async () => {
		render(FeedbackForm, { send });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect.element(page.getByRole('link', { name: 'Torna alla home' })).toBeVisible();
		expect(page.getByRole('button', { name: 'Scrivi un altro feedback' }).elements()).toHaveLength(
			0
		);
	});

	it('keeps every screenshot when more are added one after the other', async () => {
		render(FeedbackForm, { send });
		const picker = page.getByLabelText('Scegli le schermate da allegare');

		await picker.upload(new File([new Uint8Array(10)], 'prima.png', { type: 'image/png' }));
		await picker.upload(new File([new Uint8Array(10)], 'seconda.png', { type: 'image/png' }));
		await picker.upload(new File([new Uint8Array(10)], 'terza.png', { type: 'image/png' }));

		await expect.element(page.getByText('prima.png')).toBeVisible();
		await expect.element(page.getByText('seconda.png')).toBeVisible();
		await expect.element(page.getByText('terza.png')).toBeVisible();
	});

	it('keeps screenshots with the same name as separate rows', async () => {
		render(FeedbackForm, { send });
		const picker = page.getByLabelText('Scegli le schermate da allegare');

		await picker.upload(new File([new Uint8Array(10)], 'image.png', { type: 'image/png' }));
		await picker.upload(new File([new Uint8Array(20)], 'image.png', { type: 'image/png' }));

		expect(page.getByText('image.png').elements()).toHaveLength(2);
	});

	it('says how much room is left when a screenshot does not fit', async () => {
		render(FeedbackForm, { send });
		const picker = page.getByLabelText('Scegli le schermate da allegare');

		await picker.upload(
			new File([new Uint8Array(3 * 1024 * 1024)], 'grande.png', { type: 'image/png' })
		);
		await picker.upload(
			new File([new Uint8Array(3 * 1024 * 1024)], 'altra.png', { type: 'image/png' })
		);

		await expect.element(page.getByRole('alert')).toHaveTextContent('hai già 3,0 MB');
		await expect.element(page.getByText('grande.png')).toBeVisible();
	});

	it('warns that it goes back to the home page in six seconds', async () => {
		render(FeedbackForm, { send, leave });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await expect.element(page.getByText('Teletrasporto alla Home in 6 secondi…')).toBeVisible();
		expect(leave).not.toHaveBeenCalled();
	});

	it('goes back to the home page by itself when the time is over', async () => {
		render(FeedbackForm, { send, leave, redirectIn: 3, tickMs: 10 });
		await fillValid();

		await page.getByRole('button', { name: 'Invia il feedback' }).click();

		await vi.waitFor(() => {
			expect(leave).toHaveBeenCalledOnce();
		});
	});

	it('does not leave a form that was not sent', async () => {
		render(FeedbackForm, { send, leave, redirectIn: 1, tickMs: 10 });

		await new Promise((done) => setTimeout(done, 80));

		expect(leave).not.toHaveBeenCalled();
	});

	it('links the privacy page from the consent', async () => {
		render(FeedbackForm, { send });

		await expect
			.element(page.getByRole('link', { name: 'informativa privacy' }))
			.toHaveAttribute('href', '/privacy');
	});

	it('hides the trap field from people and from the keyboard', () => {
		render(FeedbackForm, { send });

		const trap = document.querySelector<HTMLInputElement>('input[name="website"]');

		expect(trap?.tabIndex).toBe(-1);
		expect(trap?.closest('[aria-hidden="true"]')).not.toBeNull();
	});
});

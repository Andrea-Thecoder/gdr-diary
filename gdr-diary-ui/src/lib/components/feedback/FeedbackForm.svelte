<script lang="ts">
	import { untrack } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { getEnv } from '#lib/env.js';
	import Button from '#lib/components/ui/Button.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import CheckField from '#lib/components/ui/CheckField.svelte';
	import ChoiceCards from '#lib/components/ui/ChoiceCards.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import {
		addShots,
		checkFeedback,
		FEEDBACK_KINDS,
		hasErrors,
		MESSAGE_MAX,
		PLACEHOLDERS,
		REDIRECT_SECONDS,
		secondsLabel,
		sendFeedback,
		type FeedbackDraft,
		type FeedbackType
	} from '#lib/feedback/feedback.js';
	import ShotPicker from './ShotPicker.svelte';

	interface Props {
		sent?: boolean;
		from?: string;
		knownEmail?: string;
		send?: (draft: FeedbackDraft) => Promise<void>;
		leave?: () => void;
		redirectIn?: number;
		tickMs?: number;
	}

	let {
		sent = $bindable(false),
		from = '',
		knownEmail = '',
		send = (draft) => sendFeedback(getEnv().VITE_API_BASE_URL, draft),
		leave = () => void goto(resolve('/')),
		redirectIn = REDIRECT_SECONDS,
		tickMs = 1000
	}: Props = $props();

	let type = $state<FeedbackType>('bug');
	let message = $state('');
	let email = $state('');
	let shots = $state<File[]>([]);
	let shotsProblem = $state<string | null>(null);
	let technical = $state(false);
	let consent = $state(false);
	let website = $state('');
	let submitted = $state(false);
	let sending = $state(false);
	let failure = $state<string | null>(null);
	let elapsed = $state(0);

	$effect(() => {
		if (knownEmail !== '' && untrack(() => email) === '') {
			email = knownEmail;
		}
	});

	const draft = $derived<FeedbackDraft>({
		type,
		message,
		email,
		shots,
		technical,
		consent,
		website,
		from
	});
	const errors = $derived(checkFeedback(draft));
	const showErrors = $derived(submitted && hasErrors(errors));

	function addMore(files: File[]): void {
		const result = addShots(shots, files);
		shots = result.shots;
		shotsProblem = result.rejected;
	}

	function removeShot(index: number): void {
		shots = shots.filter((_, position) => position !== index);
		shotsProblem = null;
	}

	async function submit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		submitted = true;
		failure = null;
		if (hasErrors(errors) || sending) {
			return;
		}
		sending = true;
		try {
			await send(draft);
			sent = true;
		} catch (error) {
			failure = error instanceof Error ? error.message : String(error);
		} finally {
			sending = false;
		}
	}

	$effect(() => {
		if (!sent) {
			return;
		}
		let passed = 0;
		elapsed = 0;
		const timer = setInterval(() => {
			passed += 1;
			elapsed = passed;
			if (passed >= redirectIn) {
				clearInterval(timer);
				leave();
			}
		}, tickMs);
		return () => {
			clearInterval(timer);
		};
	});
</script>

{#if sent}
	<SettingsSection
		title="Grazie, messaggio ricevuto."
		intro="Lo leggo con attenzione e ti rispondo all’email che hai indicato."
	>
		<div class="flex flex-wrap items-center gap-x-5 gap-y-3">
			<a href={resolve('/')} class={[buttonClasses('solid', 'md'), 'no-underline']}>
				Torna alla home
			</a>
			<p aria-hidden="true" class="m-0 text-[18px] text-soft">
				Teletrasporto alla Home in {secondsLabel(redirectIn - elapsed)}…
			</p>
			<p class="sr-only" role="status">Teletrasporto alla Home in {secondsLabel(redirectIn)}.</p>
		</div>
	</SettingsSection>
{:else}
	<form
		aria-label="Modulo di feedback"
		novalidate
		onsubmit={submit}
		class="flex flex-col gap-6 rounded-[8px] border border-(color:--card-border) p-[clamp(20px,3vw,32px)] outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
	>
		{#if showErrors || failure !== null}
			<div role="alert" class="rounded-[6px] border border-(color:--danger) px-4 py-3 text-danger">
				{failure ?? 'Il messaggio non è partito: controlla i campi segnati qui sotto.'}
			</div>
		{/if}
		<ChoiceCards
			legend="Di cosa si tratta? *"
			capitalize={false}
			stacked
			options={FEEDBACK_KINDS}
			value={type}
			onselect={(value: FeedbackType) => {
				type = value;
			}}
		>
			{#snippet icon(value)}
				<Icon size={26} strokeWidth={1.6}>
					{#if value === 'idea'}
						<path
							d="M9 18h6M10 21h4M12 3a6 6 0 00-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0012 3z"
						/>
					{:else if value === 'bug'}
						<path
							d="M12 8v5M12 16.5v.5M10.3 3.9L2.6 17.5A2 2 0 004.3 20.5h15.4a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z"
						/>
					{:else}
						<path d="M12 20s-7-4.4-7-10a4 4 0 017-2.6A4 4 0 0119 10c0 5.6-7 10-7 10z" />
					{/if}
				</Icon>
			{/snippet}
		</ChoiceCards>
		<div>
			<TextField
				label="Il tuo messaggio"
				required
				multiline
				rows={6}
				maxlength={MESSAGE_MAX}
				placeholder={PLACEHOLDERS[type]}
				bind:value={message}
				error={submitted ? errors.message : null}
			/>
			<p class="mt-1.5 mb-0 text-right text-[14px] text-soft">
				{message.length} / {MESSAGE_MAX}
			</p>
		</div>
		<TextField
			label="La tua email"
			required
			type="email"
			autocomplete="email"
			placeholder="nome@esempio.it"
			bind:value={email}
			error={submitted ? errors.email : null}
		/>
		<ShotPicker {shots} problem={shotsProblem} onadd={addMore} onremove={removeShot} />
		<div aria-hidden="true" class="absolute -left-[9999px] h-0 w-0 overflow-hidden">
			<label>
				Non compilare questo campo
				<input type="text" name="website" bind:value={website} tabindex="-1" autocomplete="off" />
			</label>
		</div>
		<div class="flex flex-col gap-2">
			<CheckField bind:checked={technical}>
				Allega i dati tecnici del tuo dispositivo.
				<span class="block text-[15px]">
					Il browser che usi, la dimensione della finestra e la pagina da cui arrivi: mi servono per
					capire dove si è rotto qualcosa.
				</span>
			</CheckField>
			<CheckField bind:checked={consent} error={submitted ? errors.consent : null}>
				Ho letto l’<a href={resolve('/privacy')} class="text-ink underline underline-offset-4"
					>informativa privacy</a
				>
				e accetto che questo messaggio sia usato per rispondermi e migliorare l’app. *
			</CheckField>
		</div>
		<div class="flex flex-wrap items-center gap-x-5 gap-y-3">
			<Button type="submit" size="form" disabled={sending}>
				{sending ? 'Invio in corso' : 'Invia il feedback'}
			</Button>
		</div>
	</form>
{/if}

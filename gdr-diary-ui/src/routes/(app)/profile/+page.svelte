<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { accountName } from '#lib/auth/display.js';
	import AccountHeader from '#lib/components/app/AccountHeader.svelte';
	import DeleteAccountDialog from '#lib/components/app/DeleteAccountDialog.svelte';
	import AvatarDialog from '#lib/components/app/AvatarDialog.svelte';
	import ProfileSummary from '#lib/components/app/ProfileSummary.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import FieldRow from '#lib/components/ui/FieldRow.svelte';
	import ReadOnlyValue from '#lib/components/ui/ReadOnlyValue.svelte';
	import NoticePopup from '#lib/components/ui/NoticePopup.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import { closeAccount } from '#lib/profile/account.js';
	import { finishEmailChange, resendEmailChange, startEmailChange } from '#lib/profile/email.js';
	import { Notice } from '#lib/ui/notice.svelte.js';
	import { onDestroy, onMount, untrack } from 'svelte';
	import type { PageProps } from './$types';

	const DELETE_PARAM = 'elimina';
	const RESEND_COOLDOWN_SECONDS = 60;

	let { data }: PageProps = $props();

	const ids = $props.id();
	const notice = new Notice();
	let email = $state(untrack(() => data.profile.email));
	let editing = $state(false);
	let deleting = $state(false);
	let resending = $state(false);
	let cooldown = $state(0);
	let countdown: ReturnType<typeof setInterval> | undefined;

	const pendingEmail = $derived(auth.user?.pendingEmail ?? null);
	const name = $derived(accountName(auth.user) || email);
	const rows = $derived([
		{
			key: 'email',
			label: 'Email',
			help: 'L’indirizzo con cui accedi.',
			value: pendingEmail ?? email
		},
		{
			key: 'plan',
			label: 'Piano',
			help: 'Lo cambia solo il servizio, non puoi modificarlo da qui.',
			value: data.profile.premium ? 'Premium' : 'Gratuito'
		},
		{
			key: 'id',
			label: 'Identificativo',
			help: 'Il codice del tuo account, utile se devi chiedere assistenza.',
			value: data.id
		}
	]);

	function logout(): void {
		void auth.logout();
	}

	function closeAccountNow(): void {
		void closeAccount();
	}

	function reauthenticateToDelete(): void {
		const back = new URL(resolve('/(app)/profile'), window.location.origin);
		back.searchParams.set(DELETE_PARAM, '1');
		void auth.reauthenticate(back.href);
	}

	function askToDelete(): void {
		if (auth.recentlyAuthenticated()) {
			deleting = true;
			return;
		}
		reauthenticateToDelete();
	}

	function reloginToDelete(): boolean {
		if (auth.recentlyAuthenticated()) {
			return false;
		}
		reauthenticateToDelete();
		return true;
	}

	function profileUrl(): string {
		return new URL(resolve('/(app)/profile'), window.location.origin).href;
	}

	function changeEmail(): void {
		void startEmailChange(profileUrl());
	}

	function startCooldown(): void {
		clearInterval(countdown);
		cooldown = RESEND_COOLDOWN_SECONDS;
		countdown = setInterval(() => {
			cooldown -= 1;
			if (cooldown <= 0) {
				clearInterval(countdown);
			}
		}, 1000);
	}

	async function resendEmail(): Promise<void> {
		resending = true;
		const outcome = await resendEmailChange(profileUrl());
		resending = false;
		if (outcome === 'sent' || outcome === 'too-soon') {
			startCooldown();
		}
		if (outcome === 'sent') {
			notice.show('Ti abbiamo scritto di nuovo: apri il link per confermare.');
		} else if (outcome === 'too-soon') {
			notice.show('Aspetta un minuto prima di chiederla di nuovo.', true);
		} else if (outcome === 'nothing-pending') {
			notice.show('Non c’è nessun cambio in attesa.', true);
		} else {
			notice.show('Non riesco a rinviare l’email. Riprova tra poco.', true);
		}
	}

	onDestroy(() => {
		clearInterval(countdown);
	});

	onMount(() => {
		void finishEmailChange(email).then((outcome) => {
			if (outcome.status === 'changed') {
				email = outcome.email;
				notice.show('Email cambiata.');
			} else if (outcome.status === 'pending') {
				notice.show(
					'Ti abbiamo scritto al nuovo indirizzo: apri il link per confermarlo. Finché non lo fai resta valido quello attuale.'
				);
			} else if (outcome.status === 'failed') {
				notice.show(outcome.message, true);
			}
		});
		if (new URL(window.location.href).searchParams.get(DELETE_PARAM) === '1') {
			deleting = true;
			void goto(resolve('/(app)/profile'), { shallow: true, replace: true });
		}
	});
</script>

<svelte:head>
	<title>Profilo · Diario GDR</title>
</svelte:head>

<AccountHeader
	title="Il tuo profilo"
	subtitle="Qui trovi i dati con cui accedi. I personaggi e i diari stanno nelle loro pagine."
/>

<Container
	class="flex flex-wrap items-start gap-x-[clamp(24px,4vw,48px)] gap-y-8 pt-[clamp(36px,5vw,56px)] pb-[clamp(64px,9vw,112px)]"
>
	<div
		class="w-full min-w-0 flex-[1_1_280px] max-[899px]:max-w-none max-[899px]:basis-full min-[900px]:max-w-[340px]"
	>
		<ProfileSummary
			{name}
			premium={data.profile.premium}
			onedit={() => {
				editing = true;
			}}
			onlogout={logout}
		/>
	</div>
	<div class="flex min-w-0 flex-[2_1_360px] flex-col gap-8">
		<SettingsSection title="Il tuo account" intro="Dati in sola lettura.">
			{#each rows as row (row.key)}
				<FieldRow label={row.label} help={row.help} labelId={`${ids}-${row.key}`}>
					<ReadOnlyValue
						value={row.value}
						labelledby={`${ids}-${row.key}`}
						warning={row.key === 'email' && pendingEmail !== null}
					/>
					{#if row.key === 'email'}
						{#if pendingEmail !== null}
							<div
								class="mt-3 flex flex-col gap-4 rounded-[4px] border border-warn/40 bg-warn/10 p-4"
							>
								<p role="status" class="m-0 text-[17px] text-ink">
									Da verificare: apri il link che ti abbiamo scritto. Fino ad allora entri ancora
									con <strong class="[overflow-wrap:anywhere]">{email}</strong>.
								</p>
								<div class="flex flex-wrap gap-3">
									<Button
										variant="outline"
										size="sm"
										class="min-w-[180px] flex-1"
										onclick={changeEmail}
									>
										Cambia email
									</Button>
									<Button
										variant="outline"
										size="sm"
										class="min-w-[180px] flex-1"
										disabled={resending || cooldown > 0}
										onclick={resendEmail}
									>
										{cooldown > 0 ? `Rinvia email tra ${cooldown} s` : 'Rinvia email'}
									</Button>
								</div>
							</div>
						{:else}
							<Button variant="outline" size="sm" class="mt-3" onclick={changeEmail}>
								Cambia email
							</Button>
						{/if}
					{/if}
				</FieldRow>
			{/each}
		</SettingsSection>
		<SettingsSection title="Elimina l’account" intro="Una scelta senza ritorno.">
			{#snippet action()}
				<Button variant="dangerOutline" size="sm" onclick={askToDelete}>Elimina account</Button>
			{/snippet}
			<p class="m-0 text-[17px] text-soft">
				Cancella per sempre l’account, i personaggi, i diari, le pagine e le immagini. Non si può
				annullare. Per sicurezza ti chiederemo di nuovo la password.
			</p>
		</SettingsSection>
	</div>
</Container>

<AvatarDialog
	open={editing}
	{name}
	onclose={() => {
		editing = false;
	}}
	onsaved={() => {
		editing = false;
		notice.show('Immagine del profilo salvata.');
	}}
/>
<NoticePopup {notice} />

<DeleteAccountDialog
	open={deleting}
	onclose={() => {
		deleting = false;
	}}
	ondeleted={closeAccountNow}
	onrelogin={reloginToDelete}
/>

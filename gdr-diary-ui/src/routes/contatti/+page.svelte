<script lang="ts">
	import { resolve } from '$app/paths';
	import CopyEmailButton from '#lib/components/info/CopyEmailButton.svelte';
	import InfoPage from '#lib/components/info/InfoPage.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import { getPrivacyContact } from '#lib/env.js';

	const contact = getPrivacyContact();

	const linkClass =
		'flex min-h-[56px] items-center justify-between gap-4 border-t border-(color:--rule) py-2.5 text-[20px] text-ink no-underline first:border-t-0 hover:opacity-80';
</script>

<svelte:head>
	<title>Contatti · Diario GDR</title>
	<meta name="description" content="Come scrivere a chi gestisce Diario GDR." />
</svelte:head>

<InfoPage
	eyebrow="Contatti"
	title="Parliamone."
	lead="Una persona vera legge quello che scrivi. Scrivimi all’indirizzo qui sotto."
>
	<div class="grid items-start gap-7 min-[901px]:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
		<SettingsSection
			title="Scrivimi una lettera"
			intro="Per domande, richieste sui tuoi dati o qualsiasi altra cosa: scrivi a questo indirizzo."
		>
			{#if contact !== undefined}
				<div class="flex flex-wrap items-center gap-4">
					<a
						href={`mailto:${contact.email}`}
						class="text-[clamp(18px,2.4vw,22px)] [overflow-wrap:anywhere] text-ink underline underline-offset-4"
					>
						{contact.email}
					</a>
					<CopyEmailButton text={contact.email} />
				</div>
			{:else}
				<p class="m-0 text-soft">
					L’indirizzo sarà indicato qui prima dell’apertura del servizio al pubblico.
				</p>
			{/if}
		</SettingsSection>
		<div class="flex flex-col gap-7">
			<SettingsSection
				title="Un’idea o un problema?"
				intro="Per segnalare un errore o proporre una novità è più comodo il modulo: puoi anche allegare uno screenshot."
			>
				<a href={resolve('/feedback')} class={[buttonClasses('solid', 'md'), 'no-underline']}>
					Lascia un feedback
				</a>
			</SettingsSection>
			<SettingsSection title="Prima di scrivere">
				<nav aria-label="Pagine utili" class="flex flex-col">
					<a href={resolve('/faq')} class={linkClass}>
						Le domande frequenti
						<ArrowIcon size={18} />
					</a>
					<a href={resolve('/piani')} class={linkClass}>
						Piani gratuito e Premium
						<ArrowIcon size={18} />
					</a>
					<a href={resolve('/privacy')} class={linkClass}>
						Privacy e i tuoi diritti sui dati
						<ArrowIcon size={18} />
					</a>
				</nav>
			</SettingsSection>
		</div>
	</div>
	{#if contact !== undefined}
		<SettingsSection
			title="Chi c’è dietro"
			intro="Diario GDR è gestito dal titolare indicato nella pagina Privacy. Per le richieste sui dati personali scrivi allo stesso indirizzo."
			class="mt-7"
		>
			<p class="m-0">
				<span
					class="block font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
				>
					Titolare
				</span>
				{contact.name}
			</p>
		</SettingsSection>
	{/if}
</InfoPage>

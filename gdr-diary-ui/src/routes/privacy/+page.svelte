<script lang="ts">
	import { resolve } from '$app/paths';
	import PolicyContact from '#lib/components/privacy/PolicyContact.svelte';
	import PolicyFacts from '#lib/components/privacy/PolicyFacts.svelte';
	import PolicyList from '#lib/components/privacy/PolicyList.svelte';
	import PolicySection from '#lib/components/privacy/PolicySection.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import BrandLink from '#lib/components/ui/BrandLink.svelte';
	import { backLinkClasses } from '#lib/components/ui/button.js';
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import OrnamentRule from '#lib/components/ui/OrnamentRule.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import ThemeToggle from '#lib/components/ui/ThemeToggle.svelte';
	import { getPrivacyContact } from '#lib/env.js';

	const UPDATED_ISO = '2026-10-30';
	const UPDATED_LABEL = '30 ottobre 2026';
	const GARANTE_URL = 'https://www.garanteprivacy.it';

	const titles = {
		titolare: 'Chi è il titolare',
		dati: 'Quali dati tratto e perché',
		dispositivo: 'Cookie e dati nel tuo dispositivo',
		destinatari: 'A chi arrivano i dati',
		conservazione: 'Per quanto tempo li conservo',
		diritti: 'I tuoi diritti',
		sicurezza: 'Come li proteggo',
		eta: 'Età minima',
		modifiche: 'Modifiche a questa pagina'
	} as const;

	type SectionId = keyof typeof titles;

	const sectionIds = Object.keys(titles) as SectionId[];
	const contact = getPrivacyContact();

	const summary = [
		'Raccolgo solo ciò che serve a farti scrivere i diari: i dati dell’account, quello che scrivi e le immagini che carichi.',
		'Niente pubblicità, niente strumenti di analisi, niente cookie di terze parti. Non vendo e non cedo i tuoi dati a nessuno.',
		'Personaggi, diari e pagine li vedi solo tu. Non sono cifrati da un capo all’altro: potrei leggerli soltanto per un problema tecnico o se me lo chiedi tu.',
		'Dal tuo profilo puoi eliminare l’account: personaggi, diari, pagine e immagini spariscono per sempre.',
		'Per qualsiasi richiesta sui tuoi dati scrivimi: i recapiti sono nella prima sezione.'
	];

	const data = [
		{
			term: 'Il tuo account',
			detail:
				'Indirizzo email, nome utente, nome e cognome e la password, che non conosco: ne conservo solo un’impronta cifrata. Servono a registrarti, a farti entrare, a verificare l’email e a recuperare la password. Li gestisce Keycloak, il sistema di accesso che uso.'
		},
		{
			term: 'Il tuo profilo nell’app',
			detail:
				'Un codice che identifica il tuo account, l’email, l’immagine del profilo se la carichi e le impostazioni che scegli (tema, carattere e dimensione del testo e simili). Servono a riconoscerti e a ritrovare l’app come l’hai lasciata.'
		},
		{
			term: 'Personaggi, diari e pagine',
			detail:
				'Nome, descrizione, campagna, colore e tipo (giocatore o master) dei personaggi; titolo, descrizione, colori e carattere dei diari; il testo delle pagine; le immagini di avatar e copertina. Sono contenuti tuoi: li uso solo per mostrarteli e non li analizzo né li uso per altro.'
		},
		{
			term: 'Dati tecnici',
			detail:
				'Indirizzo IP, data e ora delle richieste e tipo di browser finiscono nei registri tecnici del server e del sistema di accesso. Servono a far funzionare il servizio, a difenderlo dagli abusi e a trovare i guasti. La base è il mio legittimo interesse a tenere il servizio sicuro (art. 6, par. 1, lett. f, GDPR).'
		},
		{
			term: 'Email di servizio',
			detail:
				'Ti scrivo solo per cose che riguardano il tuo account: verifica dell’indirizzo, cambio email e recupero della password. Niente newsletter e niente pubblicità.'
		}
	];

	const device = [
		{
			term: 'Sessione di accesso',
			detail:
				'Keycloak tiene un cookie di sessione per riconoscerti quando torni, senza chiederti di nuovo la password. Con «Ricordami» dura più a lungo. Il token con cui l’app parla con il server resta solo nella memoria della pagina: non lo salvo da nessun’altra parte.'
		},
		{
			term: 'Tema chiaro o scuro',
			detail:
				'Il cookie gdr-theme ricorda il tema che preferisci. Non contiene dati personali e lo leggono anche le pagine di accesso.'
		},
		{
			term: 'Bozze delle pagine',
			detail:
				'Mentre scrivi, una copia di lavoro della pagina resta nel browser (IndexedDB) per non farti perdere il testo se la connessione cade. Rimane sul tuo dispositivo e si cancella quando la pagina è salvata o quando esci con «Esci». Se invece vieni disconnesso per un errore, la copia resta, così al prossimo accesso ritrovi il testo.'
		},
		{
			term: 'Note temporanee nella scheda',
			detail:
				'Una nota temporanea nella scheda (sessionStorage) ricorda da dove sei arrivato, per il pulsante «Torna a…», e, se un errore ti riporta alla home, il messaggio da mostrarti. Non contiene dati personali e sparisce quando chiudi la scheda.'
		}
	];

	const retention = [
		'Finché hai l’account: i dati dell’account, il profilo e i contenuti restano finché non li elimini o non elimini l’account.',
		'Quando elimini l’account: spariscono l’utente di accesso, il profilo, i personaggi, i diari, le pagine e le immagini. Le immagini si cancellano subito anche dallo spazio di archiviazione; se un guasto lo impedisce, le rimuovo appena possibile.',
		'Copie di sicurezza: se ne faccio, contengono ancora i dati per un periodo limitato e poi vengono sovrascritte. Non le uso per ripristinare un account che hai eliminato.',
		'Dati tecnici: i registri si conservano per il tempo strettamente necessario alla sicurezza e al funzionamento del servizio, poi si cancellano.'
	];

	const rights = [
		'accedere ai tuoi dati e averne una copia;',
		'correggerli: l’email la cambi dal profilo, il resto direttamente nell’app;',
		'farli cancellare: dal profilo, con «Elimina account»;',
		'chiedere di limitare il trattamento;',
		'riceverli in un formato leggibile da un computer (portabilità): per ora non c’è un pulsante, scrivimi e te li mando;',
		'opporti al trattamento che si basa sul mio legittimo interesse, cioè ai dati tecnici.'
	];

	const security = [
		'La connessione al servizio è cifrata (HTTPS).',
		'La password non la conosco: Keycloak ne conserva solo un’impronta.',
		'Ogni richiesta controlla che personaggi, diari e pagine siano tuoi.',
		'Le immagini stanno in uno spazio privato e si vedono solo con link temporanei.'
	];
</script>

<svelte:head>
	<title>Privacy · Diario GDR</title>
	<meta
		name="description"
		content="Quali dati raccoglie Diario GDR, perché, per quanto tempo li conserva e come puoi controllarli."
	/>
</svelte:head>

<div class="min-h-dvh font-body text-[20px] leading-[1.55] text-ink surface-page">
	<div class="surface-hero">
		<Container as="header" class="flex items-center justify-between gap-x-4 py-[22px]">
			<BrandLink />
			<ThemeToggle />
		</Container>
		<Container as="section" aria-labelledby="page-title" class="pb-[clamp(24px,4vw,48px)]">
			<div class="mx-auto max-w-[44rem] pt-[clamp(8px,2vw,24px)]">
				<Eyebrow class="text-label">Informativa sulla privacy</Eyebrow>
				<h1
					id="page-title"
					class="m-0 mt-3 font-display text-[clamp(34px,5vw,60px)] leading-[1.04] font-bold text-ink"
				>
					Come tratto i tuoi dati
				</h1>
				<p class="mx-0 mt-5 mb-0 text-[clamp(19px,2vw,22px)] text-soft">
					Qui trovi, in parole semplici, quali dati raccoglie Diario GDR, perché, per quanto tempo
					li conservo e come puoi controllarli. Il resto della pagina è l’informativa completa
					prevista dall’articolo 13 del GDPR.
				</p>
				<p class="mx-0 mt-4 mb-0 text-[17px] text-soft">
					Ultimo aggiornamento: <time datetime={UPDATED_ISO}>{UPDATED_LABEL}</time>
				</p>
			</div>
		</Container>
	</div>

	<main>
		<Container class="pt-[clamp(32px,5vw,56px)] pb-[clamp(56px,9vw,112px)]">
			<div class="mx-auto flex max-w-[44rem] flex-col gap-10">
				<SettingsSection title="In breve" intro="Le cinque cose da sapere.">
					<PolicyList items={summary} />
				</SettingsSection>

				<nav aria-label="Indice della pagina">
					<Eyebrow class="text-label">In questa pagina</Eyebrow>
					<ol class="m-0 mt-2 flex list-none flex-col p-0">
						{#each sectionIds as id, index (id)}
							<li class="border-t border-(color:--rule) first:border-t-0">
								<a
									href={`#${id}`}
									class="flex min-h-[44px] items-center gap-3 text-ink no-underline hover:opacity-80"
								>
									<span class="w-6 flex-none font-label text-[14px] text-label">{index + 1}</span>
									{titles[id]}
								</a>
							</li>
						{/each}
					</ol>
				</nav>

				<PolicySection id="titolare" title={titles.titolare}>
					<p class="m-0">
						Il titolare del trattamento, cioè chi decide come e perché si usano i tuoi dati, sono
						io:
					</p>
					<PolicyContact {contact} />
					<p class="m-0">
						Non ho nominato un responsabile della protezione dei dati (DPO) perché non è
						obbligatorio per un servizio di queste dimensioni.
					</p>
				</PolicySection>

				<PolicySection id="dati" title={titles.dati}>
					<p class="m-0">
						Ti spiego dato per dato cosa tengo e a cosa serve. Per tutti i dati di questa sezione,
						tranne quelli tecnici, la base di legge è l’esecuzione del servizio che hai chiesto
						registrandoti (art. 6, par. 1, lett. b, GDPR): senza non potrei farti scrivere un
						diario.
					</p>
					<PolicyFacts items={data} />
					<p class="m-0">
						Il diario è tuo e ci scrivi quello che vuoi, ma ti chiedo di evitare dati particolari
						(come salute, religione o orientamento) e informazioni su persone reali: un diario di
						gioco non ne ha bisogno.
					</p>
					<p class="m-0">
						Per avere un account servono l’email e la password; tutto il resto lo scrivi tu. Non
						faccio profilazione e non prendo decisioni automatizzate sul tuo conto.
					</p>
				</PolicySection>

				<PolicySection id="dispositivo" title={titles.dispositivo}>
					<p class="m-0">
						Uso solo strumenti tecnici o di preferenza, che non richiedono il tuo consenso: per
						questo non vedi nessun banner dei cookie.
					</p>
					<PolicyFacts items={device} />
					<p class="m-0">
						Il sito non carica font, script o immagini da servizi esterni e non usa strumenti di
						analisi di terze parti. Le immagini che carichi arrivano dallo spazio di archiviazione
						del servizio, con link temporanei.
					</p>
				</PolicySection>

				<PolicySection id="destinatari" title={titles.destinatari}>
					<p class="m-0">
						Non vendo i tuoi dati e non li cedo a nessuno per scopi commerciali. Per far funzionare
						il servizio mi appoggio a fornitori di infrastruttura (server, archiviazione dei file e
						invio delle email): trattano i dati soltanto per conto mio, secondo le mie istruzioni e
						con un contratto che li vincola alla riservatezza (art. 28 GDPR).
					</p>
					<p class="m-0">
						Scelgo server nell’Unione europea. Se un fornitore dovesse trasferire dati fuori dallo
						Spazio economico europeo, lo farebbe solo con le garanzie previste dal GDPR: una
						decisione di adeguatezza o le clausole contrattuali standard. Comunico dati ad autorità
						pubbliche solo se la legge lo impone.
					</p>
				</PolicySection>

				<PolicySection id="conservazione" title={titles.conservazione}>
					<PolicyList items={retention} />
				</PolicySection>

				<PolicySection id="diritti" title={titles.diritti}>
					<p class="m-0">Hai il diritto di:</p>
					<PolicyList items={rights} />
					<p class="m-0">
						Per esercitarli scrivimi ai recapiti della prima sezione. Rispondo entro un mese: se la
						richiesta è complessa il GDPR permette di prorogare di due mesi, e in quel caso ti
						avviso. Per sicurezza posso chiederti di confermare che l’account è tuo.
					</p>
					<p class="m-0">
						Se pensi che io abbia trattato i tuoi dati in modo scorretto puoi presentare reclamo al
						<a
							href={GARANTE_URL}
							target="_blank"
							rel="noopener noreferrer"
							class="text-ink underline underline-offset-4"
						>
							Garante per la protezione dei dati personali
						</a>.
					</p>
				</PolicySection>

				<PolicySection id="sicurezza" title={titles.sicurezza}>
					<PolicyList items={security} />
					<p class="m-0">
						Nessun sistema è sicuro al cento per cento: se avvenisse una violazione che mette a
						rischio i tuoi dati, ti avviso e avviso il Garante nei casi e nei tempi previsti dalla
						legge.
					</p>
				</PolicySection>

				<PolicySection id="eta" title={titles.eta}>
					<p class="m-0">
						Diario GDR è pensato per chi ha almeno 14 anni, l’età dalla quale in Italia si può dare
						da soli il consenso per i servizi online. Non verifico l’età: se hai meno di 14 anni,
						chiedi prima a un genitore o a chi ne fa le veci. Se scopro che un account è di una
						persona più giovane e senza autorizzazione, lo elimino.
					</p>
				</PolicySection>

				<PolicySection id="modifiche" title={titles.modifiche}>
					<p class="m-0">
						Se cambio qualcosa di importante aggiorno la data in cima e te lo dico con un avviso
						nell’app o per email.
					</p>
				</PolicySection>

				<OrnamentRule />

				<a href={resolve('/')} class={backLinkClasses}>
					<ArrowIcon direction="left" size={18} />
					Torna alla home
				</a>
			</div>
		</Container>
	</main>
</div>

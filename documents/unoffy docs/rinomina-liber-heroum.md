# Nome del prodotto: Liber Heroum

Deciso l'11 novembre 2026 (Andrea). Il prodotto si chiama **Liber Heroum** ("il libro degli eroi", in latino).

**Stato (19 novembre 2026):** fatta la rinomina dei nomi visibili (fase A): testi e titoli dell'app, landing, Privacy, tema e email di Keycloak (sigillo "LH"), nomi visualizzati nel realm, titolo di Swagger, nome del file dell'export, documenti. **Fase B fatta (19 novembre 2026):** nomi tecnici rinominati ovunque (vedi CHANGELOG): package `it.liberheroum`, cartelle `liber-heroum-api`/`liber-heroum-ui`, realm `liber-heroum-dev` rinominato sul posto (utenti e password restano), client `liber-heroum-api` e `liber-heroum-backend-admin`, audience `liber-heroum-backend`, database `liber-heroum-api`, tema ed estensione `liber-heroum`, prefisso interno `lh`. Restano ad Andrea il nome del repository su GitHub e la cartella principale `gdr-diary`.

## Perché questo nome

Cercavamo un nome più accattivante di "Gdr Diary", in latino o inglese e con anima fantasy, coerente con il design: diario di pergamena, sigillo di cera, scriba. Tra le finaliste (Liber Heroum, Chronicon, Mythwright) è l'unica senza conflitti trovati. Il nome promette quello che l'app è: il libro di un eroe.

## Verifica fatta (11 novembre 2026, solo ricerca sul web)

- **Liber Heroum**: nessuna app, gioco o azienda con questo nome. Compaiono solo libri storici (il "Book of Heroes" tedesco) e i manuali Warhammer *Liber Hereticus*, che sono un'altra cosa.
- **Chronicon** (scartato, rischioso): esiste un action RPG su Steam di Subworld (2020) e un gioco su App Store di datasound srl, un'azienda italiana, sui Sanniti in Molise.
- **Mythwright** (scartato): Mythwright Limited ha depositato il marchio MYTHWRIGHT il 10 ottobre 2024 per software di gioco scaricabile su computer e dispositivi mobili.

Una ricerca sul web **non** è un controllo legale. Prima di pubblicare con il nuovo nome:

1. Cercare nei registri dei marchi: EUIPO TMview (Europa) e UIBM (Italia), anche per nomi simili e per le classi software e giochi (classi 9, 41, 42).
2. Verificare i domini (per esempio `.it`, `.com`, `.app`) e i nomi sugli store e sui social.
3. Far guardare la cosa a un professionista se il progetto si apre al pubblico o diventa commerciale.

## Cosa cambierà con la rinomina (non adesso)

Elenco dei posti dove compare il nome attuale, ricavato dagli altri documenti del Project. Da controllare nei repository al momento di farlo.

**Visibile all'utente (da fare per primo)**

- Frontend: logo e testo "Diario GDR" nell'header e nel footer, titolo della pagina, landing, favicon (oggi ancora quella predefinita di SvelteKit), testi della pagina Privacy (nome del servizio; la regola del `CLAUDE.md` del frontend dice che ogni modifica alla Privacy aggiorna anche la sua data).
- Tema di Keycloak (`gdr-diary`): titoli e testi delle pagine di login, registrazione ed email.
- Design: logo vero al posto della penna generica (era già tra le cose da decidere in `landing-design.md`, sezione 8), con il sigillo come favicon. Le tele di design (landing e schermate) hanno ancora il nome vecchio.

**Tecnico (da decidere se e quando)**

- Repository `liber-heroum-api` e `liber-heroum-ui`, package `it.liberheroum`, realm `liber-heroum-dev`, client `liber-heroum-api`, audience `liber-heroum-backend` (in due posti: property del backend e mapper di Keycloak), estensione `liber-heroum-kc-extension`, cookie `lh-theme`, database IndexedDB `liber-heroum-drafts`, titolo di Swagger.
- Proposta, non decisa: cambiare subito solo i nomi visibili e lasciare quelli tecnici com'erano, rinominandoli (se serve) al momento di andare online. Il realm di produzione si crea a mano su un Keycloak nuovo, quindi il nome si sceglie in quel momento; rinominare realm e audience in sviluppo costringe a reimportare il realm e a rifare i token.
- Il cambio di audience richiede di cambiare backend e mapper insieme; il cambio di nome del cookie fa perdere il tema salvato e quello dell'IndexedDB fa perdere le bozze locali non ancora salvate (vedi `autosave-diario.md`).

**Documenti**

- `diario-gdr.md`, `architettura-arc42.md` (e il doc vero che questo file riassume): hanno già una nota sul nome futuro.
- `landing-design.md`, `da-fare-diario-ui.md`, `stato-lavori.md`, `privacy-informativa.md` e gli altri usano ancora il nome attuale: aggiornarli quando si rinomina.
- Nel repository: `README.md`, `RUNBOOK.md`, `CLAUDE.md` e `DESIGN.md`.
- Licenza PolyForm Noncommercial: controllare se il nome del prodotto compare negli avvisi di copyright.

## Decisioni del 19 novembre 2026

- Grafia: "Liber Heroum", in due parole. Le email usano sempre il nome completo.
- "LH" solo dove serve una forma cortissima (sigillo, favicon, icona), non come nome.
- Sottotitolo: "Le cronache dei tuoi eroi" (al posto di "Il diario di ogni avventuriero").
- Stile: scritta in Grenze Gotisch con la L iniziale miniata, sigillo di ceralacca con "LH" in gotico; niente blackletter pesante, illeggibile sul telefono. Il logo vero è ancora da disegnare.

## Da decidere (storico)

- Come si scrive: "Liber Heroum" in due parole, con le maiuscole come qui. Se serve una versione corta per l'icona o lo store, valutarla insieme al logo.
- Se la frase della landing "Il diario di ogni avventuriero" resta come sottotitolo (suggerimento: sì, spiega il nome latino).
- Quando fare la rinomina dei nomi visibili: non ci sono scadenze, si può fare quando arriva il logo.

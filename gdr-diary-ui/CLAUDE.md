# gdr-diary-ui

Frontend di "Diario GDR": diari per personaggi di giochi di ruolo (utente > personaggio > diario > pagine, max 100 per diario). Il documento di progetto completo è nel Project "Personali 2 - Gdr Diary" (`claude/diario-gdr.md`). Il design della home è in `claude/landing-design.md`; tutte le decisioni di stile sono riassunte in [`DESIGN.md`](./DESIGN.md).

**Per qualsiasi lavoro sull'aspetto (colori, font, spaziature, componenti visivi, temi, responsive) leggi prima `DESIGN.md`.** Non si scrivono valori di stile a mano nei componenti: si usano i token descritti lì.

## Vincolo principale: solo frontend

Il progetto è una **SPA pura**. SvelteKit si usa solo per router, build e struttura, mai per la parte server.

- Vietati: `+page.server.ts`, `+layout.server.ts`, `+server.ts`, `hooks.server.ts`, `$env/*/private`, `src/lib/server`, form actions.
- `ssr = false` e `prerender = false` in `src/routes/+layout.ts`; adapter statico con fallback `index.html`.
- Il browser parla direttamente con Keycloak (`keycloak-js`, client pubblico con PKCE) e con l'API Spring Boot (`gdr-diary-api`).
- Il token di accesso resta **solo in memoria**: mai `localStorage`, `sessionStorage` o cookie scritti dal frontend. Unica eccezione: il cookie `gdr-theme` con la scelta del tema (chiaro/scuro), che non contiene dati personali e viene letto anche dalle pagine di login di Keycloak.

## Stack

SvelteKit 3 con Svelte 5 (runes obbligatorie), TypeScript strict, Tailwind CSS 4 + DaisyUI 5, `keycloak-js`, `openapi-fetch` + `openapi-typescript`, `zod`, Tiptap (editor delle pagine del diario; lo sfoglio è un'animazione CSS di nostra scrittura, niente `page-flip`), Vitest (unit e componenti), Playwright (e2e), ESLint e Prettier.

## Come lavoriamo

- Italiano, risposte pratiche e dirette.
- Tappe piccole, una funzione completa alla volta (percorso intero: API, schermata, test), poi si passa alla successiva.
- Lo sviluppatore guida il backend e progetta database ed entity. Claude non scrive SQL, migrazioni o codice backend se non richiesto: prima si chiede.
- Il frontend lo scrive Claude e lo corregge lo sviluppatore: deve restare semplice e leggibile.
- Quando lo sviluppatore chiede un file, lo si crea (non basta spiegare in chat).
- Una funzione si considera finita solo se `npm run verify` passa.

## Componenti riutilizzabili

Il riuso è una regola, non un'ottimizzazione finale.

- Prima di scrivere markup nuovo guarda in `src/lib/components/ui` (mattoni generici) e nella cartella dell'area (`components/landing`, ...). Se esiste qualcosa di simile, si estende.
- Se la stessa struttura o la stessa stringa di classi compare una seconda volta, si estrae un componente subito, non "dopo".
- Stesso ruolo visivo, stesso componente: card, righe, pannelli, stati vuoti o "in arrivo" che l'utente vede come la stessa cosa usano un solo componente, e cambia solo il contenuto (props e snippet). Se una nuova schermata ha qualcosa che somiglia a un elemento esistente, si generalizza quello e non se ne scrive uno parallelo. Se la richiesta dice "uguale" o "identico", è un unico componente, non due che si somigliano.
- Tutto ciò che si apre sopra altro (dialog, popup, menu, pannelli) si chiude con il clic fuori solo se pressione e rilascio sono entrambi fuori: usare `createBackdropPress` o `outsideClick` da `lib/ui/outside.ts` (mai un `pointerdown` da solo). Gli avvisi usano `Popup` (centrato, 4 secondi; `placement="corner"` per gli errori in basso a destra, vedi `errorNotice`).
- Ogni cancellazione chiede conferma con `ConfirmDeleteDialog` (`components/ui`): si passano titolo, elenco di cosa si perde e `onconfirm`; mai un altro popup di conferma. Il cestino delle card è `CardDeleteButton` dentro `CardActions`, quello della pagina è `PageTrash`.
- Un elenco con ricerca e card (personaggi, diari, in futuro altri) usa `PagedList` (`lib/list`) per lo stato, `ListToolbar` per i filtri e `CardList` per gli stati e la griglia: si scrive solo la card e il suo form.
- Prima di scrivere markup per un elemento visivo si cita (anche solo nella risposta) il componente esistente che si riusa o si generalizza, oppure perché non ce n'è nessuno.
- Componenti **generici** in `components/ui` (`Container`, `BrandLink`, `SectionHeading`, `SubHeading`, `Eyebrow`, `Icon`, `QuillIcon`, `IconButton`, `BurgerButton`, `ThemeToggle`, `Tabs`, `TabNav`, `ChoiceCards`, `SegmentedControl`, `SwitchField`, `RangeField`, `SaveBar`, `ToggleButton`, `ProgressBar`, `StepIndicator`, `PortraitCard`, `GhostCard`, `SettingsSection`, `FieldRow`, `ReadOnlyValue`, `Popup`, `NoticePopup`, `CardList`, `ListToolbar`, `DialogHeader`, `ColorField`, `ImageUpload`, `SelectField`, `OrnamentRule`, `CardEditButton`, `CardDeleteButton`, `CardActions`, `TrashIcon`, `ConfirmDeleteDialog`, `NewCard`, `ArrowIcon`): niente testi o concetti del dominio dentro, tutto arriva da props e snippet.
- Componenti **di dominio** in `components/<area>` (per esempio `landing`, poi `characters`, `diaries`): compongono quelli generici.
- Props tipizzate con un'`interface Props`, valori di default espliciti, varianti con unioni di stringhe (`size: 'sm' | 'md'`), non con booleani multipli.
- Ogni componente accetta `class` per le estensioni di layout e usa gli snippet (`children`, snippet con nome) per i contenuti. Mai `<slot>`.
- Gli elementi nativi si passano con `...rest` (`HTMLAttributes`) quando serve (id, `aria-*`), come in `Container`.
- Nessun colore, font o ombra scritti a mano: solo token e utility di `DESIGN.md`.
- Un componente per file, nome in PascalCase, con la sola responsabilità che dice il nome.
- Dopo un refactor su componenti visivi si controlla che la pagina non sia cambiata (screenshot prima e dopo, light e dark, desktop e mobile).

## Responsive: ogni schermo, sempre

Tutto deve essere reattivo e adattarsi a qualsiasi schermo: telefono piccolo, telefono in orizzontale, tablet, portatile, monitor grande, ultra-wide. Non è una rifinitura finale: vale per ogni schermata e ogni componente, fin dalla prima riga.

- Si parte dal telefono più stretto (320 px) e si arriva a 3440 px. Il contenuto non va mai tagliato e la pagina non scorre mai in orizzontale.
- Layout fluidi: `flex-wrap`, `grid` con `minmax()`, `clamp()`, `min()`, `max-w-*`, `min-w-0` sui figli flessibili. Niente larghezze fisse in `px` per contenitori e colonne: al massimo un `max-w` accanto a una larghezza fluida.
- Altezze con `min-h-dvh` (mai `100vh` o `min-h-screen`: sul telefono la barra del browser le falsa). Un blocco non ha altezza fissa se contiene testo.
- Testi con `clamp()` per i titoli. Il layout deve reggere anche l'ingrandimento del testo e lo zoom del browser.
- Bersagli di tocco da almeno 44 px, e comandi raggiungibili anche con il pollice.
- Tabelle, liste lunghe, editor e immagini si adattano: scorrono dentro il loro contenitore, mai nella pagina intera.
- Si controlla anche l'orientamento orizzontale con altezza piccola (es. 667 × 375) e l'ultra-wide.
- Ogni schermata nuova ha un controllo automatico (Playwright) su più larghezze: lo schema è in `src/routes/responsive.e2e.ts`, si aggiunge la rotta alla lista.
- Dettagli e misure in `DESIGN.md`, sezione "Layout, spaziature, responsive".

## Pronto per i cellulari

L'app si usa soprattutto dal telefono: ogni schermata nasce per il telefono e poi si allarga, non il contrario.

- Il telefono è il caso principale, non un'eccezione. Prima si disegna e si prova a 360-390 px, poi si guarda il desktop.
- Tutto si usa con il pollice: comandi importanti in basso o ben raggiungibili, bersagli da almeno 44 px, spazio tra i bersagli.
- Niente funzioni solo con il mouse: `hover` è solo un di più (Tailwind lo attiva solo sui dispositivi che lo hanno), mai l'unico modo per vedere o fare qualcosa. Niente trascinamenti senza alternativa a tocco.
- Campi di testo da almeno 16 px, altrimenti iPhone ingrandisce la pagina da solo (nel progetto è già impostato in `tokens.css`).
- Tastiera a schermo: i campi non devono finire sotto la tastiera. Tipo di campo giusto (`type="email"`, `inputmode`, `autocomplete`).
- Bordi dello schermo: il layout usa `viewport-fit=cover` e i margini rispettano `env(safe-area-inset-*)` (tacca e barra di casa). Lo fa già `Container`: non toglierlo e riusalo.
- Il colore della barra del browser (`theme-color`) segue il tema scelto: lo gestiscono `app.html` e `src/lib/theme`, non va toccato a mano.
- Peso leggero: niente immagini enormi, niente librerie pesanti per cose piccole. Il telefono può essere su rete lenta.
- Ogni schermata nuova ha un test con telefono emulato (tocco, schermo piccolo): lo schema è in `src/routes/mobile.e2e.ts`.

## Convenzioni di codice

- TypeScript strict: `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `noImplicitOverride`, `noUnusedLocals` e le altre opzioni in `tsconfig.json` non si allentano. Niente `any`, niente `!` (non-null), tipo di ritorno esplicito sulle funzioni, `import type` per i soli tipi.
- Import interni con l'alias `#lib/...` **e l'estensione `.js`**, anche per i file `.ts` e `.svelte.ts` (`#lib/auth/auth.svelte.js`). `$lib` non esiste in SvelteKit 3.
- Svelte 5: `$props`, `$state`, `$derived`; `$effect` solo per effetti collaterali veri. Eventi con `onclick`, mai `on:click`. Lo stato condiviso sta in file `.svelte.ts`.
- Link interni sempre con `resolve()` di `$app/paths`. Le rotte sono tipizzate e includono il gruppo (`resolve('/(app)/characters')`).
- Mai `{@html}` per mostrare contenuti dell'utente (ESLint lo vieta). Le pagine del diario si mostrano con Tiptap (`components/book/BookPage.svelte`), non come HTML grezzo.
- Elementi interattivi sempre veri (`<button>`, `<a href>`), mai `div` con `onclick`.
- Titoli in Grenze Gotisch: interlinea mai sotto 1,04 (vedi `DESIGN.md`, "Selezione del testo dei titoli"). Il font è dichiarato in `src/lib/styles/fonts.css`, non importato da `@fontsource`.
- Niente commenti nel codice (stessa regola del backend).
- Nomi di package, `groupId` e simili (Java, Maven, Docker, estensioni di Keycloak): è un progetto personale di Andrea, **mai** il nome della sua azienda o di un cliente (niente `digitaliasistemi`, `digitalia` e simili, nemmeno dall'indirizzo email dell'utente). Il backend usa direttamente `it.gdr.diary`, le estensioni di Keycloak `it.gdr.diary.keycloak`.
- Testi dell'interfaccia in italiano. Nomi di variabili, file e funzioni in inglese.

## Autenticazione e API

- Il login è in `src/lib/auth/auth.svelte.ts` (`auth.init()`, `auth.login()`, `auth.register()`, `auth.logout()`, `auth.getToken()`, `auth.recentlyAuthenticated()`, `auth.reauthenticate()`). All'avvio `init()` fa `check-sso` silenzioso (iframe nascosto su `static/silent-check-sso.html`, senza pagina di login): se Keycloak riconosce il browser dà un token nuovo, altrimenti l'utente è anonimo. Token e refresh token stanno solo in memoria, mai in `localStorage`, `sessionStorage` o cookie scritti da noi: tra una ricarica e l'altra ricorda l'utente solo il cookie di sessione di Keycloak. Accesso e registrazione non hanno pagine nostre: le fanno entrambe Keycloak. I pulsanti usano `AuthButton` (`action="login"` o `"register"`) o `LinkButton` con `loginToApp` / `registerToApp`. Le rotte protette stanno nel gruppo `src/routes/(app)`, il cui `+layout.ts` fa login, controlla i ruoli e chiama `POST /me` una volta per sessione. Se questo fallisce (o l'account non ha ruoli) l'utente viene **disconnesso**, mai lasciato dentro a metà: `signOutWith` salva il messaggio del backend (`violations[].message`, vedi `describeApiError`) con `rememberError` (`ui/flash-error.ts`) e fa `auth.logout({ keepDrafts: true })`; dopo il ritorno alla home il `+layout.svelte` radice lo mostra in `errorNotice` (`ui/error-notice.svelte.ts`, `NoticePopup placement="corner"`, in basso a destra). La landing rimanda a `/characters` chi è già dentro. `+error.svelte` resta per 404 e errori imprevisti. Per mostrare un errore in basso a destra basta `errorNotice.show(testo, true)`.
- Ruoli Keycloak (realm role): `user` e `admin`, gli stessi dell'API; senza nessuno dei due l'API risponde 403 e il frontend mostra la pagina 403. "Giocatore" e "master" **non sono ruoli di login**: sono la parte che una persona ha in una campagna, e la stessa persona può giocare in una e fare il master in un'altra. Chi si registra riceve `user` dal gruppo predefinito `users` del realm; `admin` si assegna a mano dalla console.
- Le impostazioni dell'utente sono un JSON libero: si leggono con `GET /me` (`settings`) e si scrivono con `PATCH /me/settings` con corpo `{ settings: { … } }` e risposta `{ payload }` (merge: una chiave sostituisce, `null` la toglie). Oggi c'è solo `theme`; il codice è in `src/lib/settings` (`api.ts`, `account.ts`) e l'ingresso nell'app le legge una volta (`accountSync`, che prende da `GET /me` anche `avatarUrl`). Il cambio email lo fa Keycloak con la sua azione `UPDATE_EMAIL` (redirect con `kc_action`, mail di conferma al nuovo indirizzo); al ritorno il profilo rinnova il token e, se l'email è cambiata, chiama `POST /me` (`lib/profile/email.ts`, vedi `DESIGN.md`). L'immagine del profilo non sta nelle impostazioni: ha `PUT/DELETE /me/avatar` (`lib/profile`). Altre impostazioni (font e dimensione di default dei diari) seguono lo stesso schema.
- Le chiamate all'API passano solo da `getApi()` (`src/lib/api`): aggiunge il bearer token e lo rinnova. Mai `fetch` a mano verso l'API.
- Gli errori dell'API hanno forma `ExceptionResponse { title, status, violations[] }`: si mostrano con `describeApiError`. `describeApiError` traduce i messaggi del backend in italiano (`src/lib/api/messages.ts`, vedi `DESIGN.md` sezione 19): un messaggio nuovo dell'API va aggiunto lì. Cancellando un personaggio o un diario, un 404 vale come già cancellato e un 409 chiede di riprovare.
- Le liste sono `PagedResultDTO` (`list`, `totalRows`, `totalPages`, `pageSize`, `page`). Le pagine di un diario arrivano due alla volta (`GET /diaries/{id}/pages?page=N`), senza `size`.
- I campi di audit non esistono nei DTO. `avatarUrl` e `coverUrl` sono presigned URL a scadenza breve: non vanno salvati.
- Il contratto è `openapi/swagger.json` (copia di quello dell'API). Quando l'API cambia: aggiorna il file e lancia `npm run api:generate`.
- Configurazione in variabili `VITE_*` validate in `src/lib/env.ts` (esempio in `.env.example`). Il file `.env` non va nel repository. Opzionali: `VITE_PRIVACY_CONTROLLER_NAME` e `VITE_PRIVACY_CONTACT_EMAIL` (titolare e recapito mostrati in `/privacy`; se mancano la pagina lo dice).

## Qualità e test

- `npm run verify` (type check, lint, test, controllo del tema Keycloak, build) prima di ogni commit. Se `theme:check` dice che il tema è vecchio: `npm run theme:build` e salvare i file generati (vedi `DESIGN.md` sezione 13).
- Nei test niente `if`, `for`, `while` o ternari nel corpo: si usano `it.each`, `required()` e gli helper (`src/lib/test-support.ts`, `src/lib/book/test-support.ts` con `doc`, `spreadData`, `serveBook`, `draftFixture`, `diaryFixture`, `characterFixture`; solo per i test, mai importati dal codice dell'app) e niente attese in tempo reale (`settle()`, timer finti, `expect.poll`).
- Test accanto al codice: `*.test.ts` (logica, ambiente Node), `*.svelte.spec.ts` (componenti, nel browser), `*.e2e.ts` (Playwright, sulla build).
- Le pagine dell'area protetta non si raggiungono negli e2e (serve Keycloak): si provano con `*.svelte.spec.ts` che mockano `#lib/api/index.js`, anche per i controlli di larghezza (`page.viewport`) e di bersagli da 44 px.
- Ogni funzione nuova ha almeno un test. Ogni regola di accesso (ruoli, rotte protette) ne ha uno dedicato.
- La sicurezza dei dati sta nel backend (ogni query verifica l'appartenenza). Il frontend non si fida mai di sé stesso per nascondere dati.

## Privacy

Nessuna risorsa di terze parti nelle pagine: font ospitati nel progetto (`@fontsource`), niente CDN, niente Google Fonts, niente analytics di terzi.

L'informativa è la pagina `/privacy` (vedi `DESIGN.md` sezione 23) e descrive quello che l'app fa davvero. Quando una modifica aggiunge un cookie, un uso di `localStorage`/`sessionStorage`, un dato raccolto, un fornitore o un servizio esterno (analytics, email, hosting, storage), si aggiorna `/privacy` e la sua data nello stesso cambio.

## Struttura

```
src/
  routes/
    +layout.ts            ssr = false, prerender = false
    +page.svelte          home pubblica (landing)
    privacy/              informativa sulla privacy
    (app)/                area protetta: guard in +layout.ts
  lib/
    auth/                 stato di login (keycloak-js), ruoli, azioni
    api/                  client tipizzato, schema generato, POST /me
    components/
      ui/                 mattoni generici riutilizzabili
      landing/            componenti della home
      privacy/            mattoni dell'informativa (`PolicySection`, `PolicyList`, `PolicyFacts`, `PolicyContact`)
      app/                header e menu account dell'area protetta
      characters/         schermata dei personaggi (card, barra, dialog)
      diaries/            diari del personaggio (card, dialog, `ClosedBook`)
      sheets/             schede del personaggio in PDF (card, dialog, campo PDF, schermata)
      book/               il diario aperto: libro, pagine Tiptap, angoli, barra, salvataggio
    characters/           colori e silhouette, `kind` (personaggio o master), chiamate API, stato della lista
                          e le schede della pagina del personaggio (`tabs.ts`, `load.ts`)
    sheets/               schede in PDF: chiamate API, formati (peso, data, controllo del file), stato della lista
    pdf/                  editor dei PDF compilabili (pdf.js, `PdfEditor`): caricamento lazy, apertura, salvataggio; `test-support.ts` ha due PDF di prova
    book/                 diario aperto: `DiaryBook` (stato, salvataggio, sfoglio), pagine API, impaginazione automatica, misure
    theme/                scelta del tema: sistema / chiaro / scuro
    styles/tokens.css     token dei due temi, utility e font
    env.ts                variabili d'ambiente validate
keycloak-theme/           tema di Keycloak: pagine di login e registrazione (template, build del CSS) e le email (`themes/gdr-diary/email`, vedi DESIGN.md sezione 21)
openapi/swagger.json      contratto dell'API
```

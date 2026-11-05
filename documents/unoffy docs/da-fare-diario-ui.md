# Da fare: frontend Diario GDR (gdr-diary-ui)

Aggiornato il 27 ottobre 2026. Elenco di quello che resta sul frontend. Lo stato del backend e le decisioni stanno in `claude/stato-lavori.md`.

## Prima di pubblicare

- Titolare e recapito della pagina Privacy: mettere nel file `.env` le due variabili `VITE_PRIVACY_CONTROLLER_NAME` (nome e cognome, oppure la ragione sociale) e `VITE_PRIVACY_CONTACT_EMAIL` (un indirizzo che si legge davvero e che può diventare pubblico). Il dettaglio di cosa manca è in `claude/privacy-informativa.md`.
- Cancellare anche l'utente su Keycloak quando si elimina l'account. Oggi il frontend chiama `DELETE /me` (che il backend ha già) e poi fa il logout, ma l'account Keycloak resta e al prossimo accesso si ritrova un utente vuoto. La strada più semplice è l'azione richiesta `delete_account` di Keycloak, lanciata dal frontend come si fa per il cambio email (va attivata nel realm). Ordine voluto: prima il backend, poi Keycloak. Se Keycloak fallisce, si può ripetere `DELETE /me` senza danni.
- Controllo lato server dell'accesso recente su `DELETE /me`: il frontend chiede di nuovo la password se l'accesso ha più di 5 minuti, ma il backend deve rifiutare per conto suo (voce 2 di "Da fare" in `claude/stato-lavori.md`).
- Lanciare `npm run verify` (comprende il controllo del tema Keycloak, la build e le prove di larghezza).

## Da provare con backend e Keycloak veri

Finora il frontend è stato provato con test automatici ed e2e con servizi finti, non contro il backend acceso.

- Il `check-sso` silenzioso: verificare che nel client `gdr-diary-api` del realm `gdr-diary-dev` sia valido il redirect `http://localhost:5173/silent-check-sso.html` e che l'origine dell'app sia tra le Web origins. In produzione app e Keycloak devono stare sullo stesso sito (per esempio `app.dominio.it` e `login.dominio.it`, con la variabile `VITE_THEME_COOKIE_DOMAIN`), altrimenti i cookie di terze parti bloccati fanno ricadere `keycloak-js` sul redirect a pagina intera.
- Salvataggio, conflitti 409 con due schede aperte sulla stessa pagina, cancellazione di pagine, diari e personaggi, rinumerazione, creazione delle pagine.
- Cambio email: provare tutto il giro con Mailpit (link di conferma, rinvio, un solo link valido).

## Funzioni

- Segnalibro interattivo (idea del 22 ottobre). Oggi il segnalibro in alto a destra del libro è solo decorativo: compare quando dopo la coppia visibile ci sono altre pagine scritte e non fa niente se lo si tocca. Idea: farlo diventare un vero segnalibro, che salva il punto a cui si è arrivati, così riaprendo il diario si torna lì. Da decidere dove salvare la posizione (nel browser, semplice ma non segue tra dispositivi; oppure nel backend, con un campo sul diario o nelle impostazioni) e se serve uno solo per diario o più di uno.
- Cancellare un diario dalla schermata del diario aperto: oggi si cancella solo dalla lista dei diari del personaggio.
- Modifica del diario (titolo, copertina) dalla schermata del diario aperto.
- Pagina pronta in anticipo: oggi quando la pagina è piena la successiva è subito scrivibile ma il libro non gira da solo. Se serve, far girare il libro appena la pagina è piena, facendo attenzione a non girare a metà parola.
- Autosave: valutare se far scegliere l'attesa nelle impostazioni (oggi 3 secondi).
- Filtro Tutti, Personaggi, Master nella lista: serve prima il campo `kind` nel filtro dell'API (`GameCharacterSearchRequest`).
- Numero di pagine sulla card del diario: la lista non lo restituisce, serve un campo in più nel backend.
- Esportazione dei dati dell'utente: manca l'endpoint. Oggi la pagina Privacy dice che si chiede per email.
- Campagne: l'area "Le mie campagne" mostra per ora "Coming Soon".

## Tablet e telefoni

Il libro a due pagine si adatta con una scala (vedi `DESIGN.md`) e profilo e impostazioni passano su una colonna sotto i 900 px. Manca una prova su un tablet vero e la scelta di un'opzione "una pagina alla volta" per chi trova il testo troppo piccolo.

## Pulizia

- Il filtro per campagna è un campo libero: se le campagne diventano una tabella, andrà sostituito.

## Non serve (decisioni)

- Impostazioni "Promemoria" (promemoria di scrittura, orario preferito, riepilogo per email): erano campi di esempio inventati nella tela di design. L'app non manda promemoria né email, quindi non vanno costruiti. Se un giorno servono notifiche vere sono una funzione nuova, con backend, invio email e permessi.

## Fatto di recente

- 27 ottobre 2026: pagina Privacy con informativa completa (`/privacy`), componenti `PolicySection`, `PolicyList`, `PolicyFacts` e `PolicyContact`, `BrandLink` condiviso con la pagina d'errore. Le liste numerate dell'editor accettano solo i tipi che il backend ammette (1, a, A, i, I): incollando HTML con un tipo strano non si rompe più il salvataggio.
- 22 ottobre 2026: il login resta tra le ricariche (`check-sso`); l'eliminazione dell'account chiede di nuovo la password; cancellazione con conferma in un solo popup riusabile (`ConfirmDeleteDialog`) per account, personaggio, diario e pagina; barra di scorrimento a coppie di pagine; tasti premuti subito dopo Backspace a inizio pagina non vanno più persi.

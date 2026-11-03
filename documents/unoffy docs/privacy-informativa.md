# Pagina Privacy (/privacy)

Stato al 27 ottobre 2026.

Fatta nel frontend (`gdr-diary-ui`): informativa ai sensi dell'art. 13 del GDPR in italiano semplice, con "In breve" e nove sezioni, un indice e il link al Garante. Il design è in `DESIGN.md` sezione 23. La regola è nel `CLAUDE.md` del frontend (sezione Privacy): ogni cookie, uso di storage, dato o fornitore nuovo aggiorna la pagina e la sua data nello stesso cambio.

## Da fornire o decidere prima di pubblicare

- Titolare e recapito: nel file `.env` le variabili `VITE_PRIVACY_CONTROLLER_NAME` (nome e cognome, oppure la ragione sociale) e `VITE_PRIVACY_CONTACT_EMAIL` (un indirizzo che si legge davvero e che diventa pubblico). Senza, la pagina dice che i recapiti saranno indicati.
- Nomi dei fornitori (hosting, invio email) e regione europea: oggi il testo non li nomina.
- Tempi di conservazione di log e backup: i numeri nel testo vanno confermati.
- Età minima di 14 anni (consenso digitale in Italia): oggi la registrazione non la controlla.
- Il testo assume che la registrazione chieda email, nome utente, nome e cognome: verificare con il tema di Keycloak.
- La pagina dichiara che si può eliminare l'account: `DELETE /me` esiste nel backend, ma l'utente su Keycloak non viene ancora cancellato (vedi `da-fare-diario-ui.md`). Va finito prima di pubblicare.
- Esportazione dei dati: oggi solo su richiesta via email.
- Valutare un link ai termini e alla privacy nella pagina di registrazione di Keycloak.
- Far rivedere il testo a un professionista prima del lancio pubblico.

## Dichiarato nella pagina (deve restare vero)

Cookie di sessione di Keycloak, cookie `gdr-theme`, bozze in IndexedDB, `sessionStorage` (ultimo posto visitato e messaggio d'errore), nessuna analytics né risorsa di terzi.

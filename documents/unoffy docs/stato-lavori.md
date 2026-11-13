# Stato lavori di gdr-diary-api

Aggiornato il 27 ottobre 2026. Le regole di codice del backend stanno nel `CLAUDE.md` del repository, qui c'è solo cosa è fatto, cosa è stato deciso e cosa resta. Per il frontend vedi `claude/da-fare-diario-ui.md`.

## Fatto

### Dati e endpoint

- Entity: `AppUser`, `GameCharacter` (con `kind`, `PLAYER` o `GAME_MASTER`, scelto alla creazione e non modificabile), `Diary`, `DiaryPage`, `Document`. L'audit sta solo in `AbstractAuditable`. La migrazione V1.0 contiene `kind` e l'avatar dell'utente.
- Endpoint attuali:
  - `/me`: `GET`, `POST`, `DELETE`.
  - `/me/settings`: `PATCH`.
  - `/me/avatar`: `PUT`, `DELETE`.
  - `/characters` e `/characters/{id}`: lista, creazione, lettura, modifica, cancellazione.
  - `/characters/{id}/avatar`: `PUT`, `DELETE`.
  - `/characters/{characterId}/diaries` e `/diaries/{id}`: lista, creazione, lettura, modifica, cancellazione.
  - `/diaries/{id}/cover`: `PUT`, `DELETE`.
  - `/diaries/{diaryId}/pages` (lista a due pagine, creazione) e `/pages/{id}` (`PUT`, `DELETE`).
- Utente: `POST /me` crea l'utente al primo accesso e allinea l'email a quella del token. `GET /me` restituisce email, piano, impostazioni (`{}` se nel database sono `NULL`) e indirizzo dell'avatar. `PATCH /me/settings` unisce le chiavi di primo livello (una chiave a `null` si toglie, limite di 10 KB). `DELETE /me` cancella tutti i dati dell'utente nel backend (personaggi, diari, pagine, immagini e `AppUser`) in una sola transazione, poi i file nello storage; si può ripetere senza danni.
- Documenti: caricamento diretto sullo slot (avatar, copertina, immagine del profilo), solo immagini, con controllo del contenuto vero (primi byte, tipo dichiarato coerente, estensione, peso). Lettura con indirizzi temporanei a scadenza breve (5 minuti). Funziona con SeaweedFS: le immagini sono state caricate davvero.
- Pagine: due per vista, numerazione consecutiva assegnata dal server, rinumerazione alla cancellazione, massimo 100. Lock ottimistico con `version` e 409 se è cambiata. Controllo del contenuto (`DiaryPageContentValidator`): accetta esattamente il JSON dell'editor del frontend e rifiuta il resto con un 400 che indica il punto.
- Transazioni: ogni operazione su più righe o tabelle è in una sola transazione con rollback totale, anche per i `Document`. Lo storage non è transazionale: il file nuovo si carica dentro la transazione e si cancella se questa fallisce, i file vecchi si cancellano solo dopo il commit (possibili file orfani se la rimozione fallisce).
- Errori e DTO: `ExceptionResponse` con titolo, stato e violazioni. Liste con `PagedResultDTO` e `SearchRequest` per entity. `SimpleResultDTO` si costruisce sempre nel controller. Ogni query Ebean ha un'etichetta.
- Test: ci sono test unitari per controller, service, DTO, sicurezza, validatore del contenuto e CORS. Non ci sono test di integrazione su un database vero.

### Sicurezza

- Ruoli `user` e `admin` letti da `realm_access.roles`. `/me/**` basta autenticati, il resto richiede un ruolo (403 altrimenti). Essere "master" non è un ruolo.
- Il token deve avere `gdr-diary-backend` nell'audience: lo controlla Spring con la property `spring.security.oauth2.resourceserver.jwt.audiences`, lo scrive Keycloak con un mapper nel client `gdr-diary-api`.
- Ogni query verifica l'appartenenza dei dati all'utente.

### Keycloak e ambiente locale

- Docker Compose con immagini a versione fissata: Keycloak 26.7.0, SeaweedFS 4.48, PostgreSQL 16, Mailpit 1.32.0. Keycloak importa il realm all'avvio da `docker-compose/keycloak/gdr-diary-dev-realm.json`.
- Realm `gdr-diary-dev`: registrazione attiva con verifica email, ruoli `user` e `admin`, gruppo predefinito `users`, client pubblico `gdr-diary-api` con PKCE e mapper dell'audience, utenti di prova `user1` e `admin1` con password di sviluppo.
- Sessioni: "Ricordami" attivo. Sessione normale 30 minuti di inattività e 10 ore in totale. Con la spunta 15 giorni di inattività e 30 in totale. Tempo per completare il login 10 minuti, per le azioni 5 minuti. Rotazione del refresh token con 2 riusi tollerati. Durata dell'access token: 5 minuti (valore di default di Keycloak, non scritto nel JSON).
- Tema di login e registrazione `gdr-diary` (modelli FreeMarker sopra il tema `base`), che sta nel repository del frontend in `keycloak-theme/` e viene montato nel compose. Il CSS è generato dagli stessi token del frontend, quindi non si può disallineare. È stato provato su Keycloak vero. Il tema chiaro e scuro segue il cookie `gdr-theme`.
- Cambio email: lo fa Keycloak con l'azione `UPDATE_EMAIL` (funzione `update-email` attiva nel compose). L'estensione `keycloak-extensions/gdr-diary-kc-extension` evita che l'accesso resti bloccato mentre il nuovo indirizzo attende la conferma, permette di rimandare la mail e fa valere solo l'ultimo link. Si costruisce con `build-keycloak-extensions.sh`.
- Email del realm: tema `gdr-diary` anche per le email, inviate a Mailpit in locale (`http://localhost:8025`).

## Decisioni

- Immagini: caricamento passando dal backend, lettura con indirizzi temporanei. Limiti noti: niente antivirus (rimandato), possibili file orfani, il link di lettura vale per chiunque lo abbia fino alla scadenza.
- Il server di MinIO è archiviato (aprile 2026) e non si usa; di MinIO resta solo il client Java verso lo storage S3.
- PDF editabili e normali si accettano (controllo di `%PDF-`) quando arriverà la scheda personaggio.
- Contenuto delle pagine: JSON di Tiptap, massimo 50 KB, profondità massima 50. Non serve un controllo dell'HTML perché il backend non salva HTML e il testo resta sempre testo. Un controllo servirebbe solo se un giorno si esportasse o si mostrasse HTML (esportazione in PDF, email, `{@html}`): in quel caso con una libreria e la stessa lista di tag ammessi. Nel frontend non si usa mai `{@html}` per le pagine.
- Rinumerazione delle pagine: resta nel service, in una transazione, perché con al massimo 100 pagine il costo non conta. Scartate per ora le alternative: trigger nel database (non aggiorna versione e audit), due `UPDATE` di massa, numeri con buchi calcolati in lettura. Da riconsiderare solo se le cancellazioni diventano frequenti o arrivano da fuori dall'API.
- Cancellazioni: la cascata sta nel service, in una transazione. Passare a `ON DELETE CASCADE` nel database è possibile ma non necessario.
- Impostazioni dell'utente: nel database restano `NULL` finché l'utente non cambia qualcosa, i default vivono nel frontend e non si copiano mai nel database.
- Email dell'utente: si tiene in `app_user` perché servirà per inviti, notifiche e ricerche senza interrogare Keycloak. Si allinea solo con `POST /me`. Quando arriveranno gli inviti per email andrà controllato `email_verified`.
- Ruoli: assegnati solo da Keycloak. `user` a chi si registra (gruppo predefinito), `admin` a mano dalla console. Il backend li legge e non li assegna mai.
- Realm: l'importazione del JSON avviene solo alla prima creazione. Se il file cambia va azzerato il volume `postgres-keycloak-data` e ricreato il contenitore, oppure si cambiano le impostazioni dalla console e si riportano a mano nel JSON. Un ruolo composito che include ruoli predefiniti come `uma_authorization` fa fallire l'importazione: il ruolo predefinito si dà con un gruppo predefinito.
- Sessione nel frontend: token e refresh token solo in memoria. Tra una ricarica e l'altra l'utente lo riconosce il cookie di sessione di Keycloak tramite `check-sso`, con "Ricordami" per la durata lunga. Scartato salvare il refresh token in `localStorage` o `sessionStorage`.
- Nomi di package: il backend usa direttamente `it.gdr.diary`, le estensioni di Keycloak `it.gdr.diary.keycloak`.

## Da fare

1. Cancellare l'utente su Keycloak dopo `DELETE /me`. Oggi il frontend chiama `DELETE /me` e poi fa il logout, ma l'account Keycloak resta: al prossimo accesso si ritrova un utente vuoto. Strade: azione richiesta `delete_account` lanciata dal frontend (come per il cambio email), oppure un service account con le API di amministrazione. Il backend, come scritto nel suo `CLAUDE.md`, non tocca Keycloak.
2. `DELETE /me` dovrebbe rifiutare un accesso troppo vecchio: il frontend richiede di nuovo la password e guarda `auth_time`, ma il controllo vero va fatto anche lato server (non c'è ancora).
3. Elenco chiuso delle impostazioni dell'utente per `PATCH /me/settings`: oggi è un JSON libero con il solo limite di 10 KB. Si ammettono solo chiavi note, ognuna con tipo e valori ammessi (le chiavi vere sono in `src/lib/settings/` e `src/lib/diaries/typography.ts` del frontend), con controllo di profondità e numero di chiavi, e tutto il resto rifiutato con 400. Chiude il rischio di valori inseriti in HTML o CSS dal frontend e di chiavi pericolose come `__proto__`.
4. Avvio senza Keycloak: il `pom.xml` include lo starter OAuth2 client e `application.properties` ha 4 righe `spring.security.oauth2.client.*`, ma il backend è solo resource server. Con quelle righe Spring scarica la configurazione del realm all'avvio e l'app non parte se Keycloak non risponde. Da fare: togliere lo starter (e quello di test) e le 4 righe.
5. Repository: introdurre classi `@Repository` concrete con le sole query e le loro etichette, per togliere i duplicati (`getGameCharacterByIdAndOwner` e `getDiaryByIdAndOwner` compaiono in più service) e testare con repository finti.
6. Lock ottimistico anche su diari e personaggi, con lo stesso schema delle pagine. Da decidere se serve.
7. Un PDF per personaggio (scheda): colonna `sheet_document_id` su `game_character` e endpoint `PUT` e `DELETE /characters/{id}/sheet`, solo PDF.
8. Endpoint da admin per modificare gli utenti, uno alla volta: regola `/admin/**` con `hasRole("ADMIN")`. Se toccano Keycloak serve un service account.
9. Keycloak per l'uso reale: invio email vero, protezione dai tentativi ripetuti, regole sulle password, realm separato di produzione senza login diretto con password, valutare client confidenziale.
10. Contenuto delle pagine: verificare il limite di profondità di Jackson 3 (credo 1000, non verificato) e mettere un tetto alla dimensione della richiesta a livello Tomcat, perché oggi il controllo di peso avviene dopo la lettura del corpo. Se si aggiungono link all'editor vanno ammessi solo `http` e `https`.
11. Intestazioni `X-Content-Type-Options: nosniff` e `Cache-Control: no-store` sulle risposte con dati personali, e CORS limitato all'origine esatta in produzione (la property `app.cors.allowed-origins` oggi è `http://localhost:5173`).

## Da provare con tutto acceso

Non è mai stato provato con backend e Keycloak veri:

- Audience: fare login con `user1`, controllare che l'access token abbia `"aud": "gdr-diary-backend"` e chiamare un endpoint (200); poi chiamarlo con un token di un altro client, per esempio `account` (deve dare 401). Se il secondo caso passa, la property non viene applicata in Spring Boot 4.1 e serve un `JwtDecoder` con validatore in `SecurityConfig`. I token emessi prima del cambio non hanno l'audience: serve un nuovo login.
- Transazioni, cascata e lock: cancellare un personaggio con più diari e pagine e controllare database e bucket; sostituire due volte un avatar; aggiornare la stessa pagina da due schede e controllare che compaia il conflitto; creare 5 pagine, cancellare la 2 e controllare che i numeri siano 1, 2, 3, 4.
- Cose scritte sulla documentazione e non verificate su Ebean 19: `ExpressionList.delete()` come cancellazione di massa, `database.deleteAll`, il passaggio di `createImageDocument` nella transazione aperta dal service, e `select("pageNumber")` parziale con `saveAll` e `@Version` (se dà errore, togliere il `select`).
- Il controllo dei caratteri NUL e delle surrogate spaiate nel testo: non provato su un PostgreSQL vero.
- SeaweedFS: nel compose non c'è un `command` (forse serve `server -s3`) e non viene creato il bucket `diary-documents`. L'upload funziona, ma conviene controllare con `aws --endpoint-url http://localhost:8333 s3 ls` come si crea il bucket da zero.
- Email e tema: password dimenticata, pagina di errore e verifica email non sono ancora state viste in Keycloak. Le email (cinque modelli) sono provate solo con dati finti.
- Con "Revoke Refresh Token" acceso, più schede che rinnovano insieme possono ripresentare un refresh token già usato: oltre i 2 riusi tollerati Keycloak chiude la sessione. Da provare con due schede aperte.
- Frontend: lanciare `npm run verify`, che comprende il controllo del tema Keycloak, la build e le prove di larghezza.

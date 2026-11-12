# gdr-diary-api · Runbook

> Procedure operative e risoluzione dei problemi di **gdr-diary-api**. Vale per l'ambiente di sviluppo in locale: la produzione non è ancora definita (vedi la sezione 5).

---

## 1. Panoramica operativa

### Componenti

Il backend gira fuori da Docker (`./mvnw spring-boot:run`), gli altri servizi nei container del `docker-compose`.

| Servizio | Cosa fa | Porta | Dipende da | Controllo di stato |
|---|---|---|---|---|
| **gdr-diary-api** | Backend REST | `8080` | PostgreSQL, SeaweedFS, Keycloak (per le chiavi dei token) | `/actuator/health` |
| `postgres` | Database applicativo | `5432` | – | `docker compose ps` (healthy) |
| `postgres-keycloak` | Database di Keycloak | `5433` | – | `docker compose ps` (healthy) |
| `keycloak` | Login, token, ruoli | `8180` | `postgres-keycloak`, `mailpit` | `docker compose ps` (healthy) |
| `seaweedfs` | Storage S3 delle immagini | `8333` | – | nessuno |
| `mailpit` | Email di Keycloak | `8025` (web), `1025` (SMTP) | – | nessuno |

Ordine di avvio: prima i container (aspettare che i servizi con controllo di stato siano `healthy`), poi il backend. Il backend all'avvio non contatta Keycloak: scarica le chiavi di firma alla prima richiesta con un token. Contatta invece lo storage per creare i bucket se mancano (se lo storage è spento scrive un avviso e parte lo stesso).

### Dove guardare i log

| Cosa | Come |
|---|---|
| Backend | Console in cui gira `./mvnw spring-boot:run`. Il package `it.gdr.diary` logga a livello `DEBUG`. |
| Container | Dalla cartella `docker-compose`: `docker compose logs -f <servizio>` (`postgres`, `postgres-keycloak`, `keycloak`, `mailpit`, `seaweedfs`). |

---

## 2. Procedure ordinarie

### Avviare e fermare

```bash
cd docker-compose
docker compose up -d        # avvia i servizi
docker compose ps           # stato dei servizi
docker compose down         # ferma i servizi, i dati restano
docker compose down -v      # ferma e cancella tutti i dati (reset totale)
```

Backend, dalla radice del progetto: `./mvnw spring-boot:run`.

### Controllare lo stato del backend

`http://localhost:8080/api/v1/gdr-diary-api/actuator/health` risponde senza dettagli a tutti. Il dettaglio (database, disco) lo vede solo un utente con ruolo `admin`, con il suo token.

### Reimportare il realm di Keycloak

Keycloak importa `docker-compose/keycloak/gdr-diary-dev-realm.json` solo alla prima creazione. Dopo una modifica al file:

```bash
cd docker-compose
docker compose rm -sf keycloak postgres-keycloak
docker volume rm docker-compose_postgres-keycloak-data
docker compose up -d
```

Il nome del volume è il nome della cartella del compose seguito da `_` e dal nome del volume: verificarlo con `docker volume ls`.

> **Attenzione**: gli utenti ricreati hanno un nuovo `sub`, quindi i dati dell'applicazione legati ai vecchi utenti non sono più raggiungibili. In sviluppo conviene azzerare anche il database (vedi sotto).

In alternativa si cambiano le impostazioni dalla console di Keycloak e si riportano a mano nel file JSON.

### Azzerare il database dell'applicazione

```bash
cd docker-compose
docker compose rm -sf postgres
docker volume rm docker-compose_postgres-data
docker compose up -d postgres
```

Al riavvio del backend Flyway ricrea lo schema. Le immagini nello storage non vengono toccate: per un reset coerente cancellare anche il volume `docker-compose_seaweedfs-data`, oppure usare `docker compose down -v`.

### Dare il ruolo `admin` a un utente

Console Keycloak (`http://localhost:8180`, utente `admin`, password `secret`) → realm `gdr-diary-dev` → **Users** → l'utente → **Role mapping** → **Assign role** → `admin`. L'utente deve fare un nuovo login: i ruoli sono scritti nel token.

### Cancellare un utente

L'ordine è: prima `DELETE /me` sul backend, poi la cancellazione dell'account su Keycloak (la lancia il frontend con l'azione `delete_account`).

- Se il frontend si ferma sull'ultimo passo, `DELETE /me` si può richiamare: se l'utente non c'è più nel database risponde 200 senza fare niente.
- L'account su Keycloak si può cancellare a mano dalla console: **Users** → l'utente → **Delete**.

### Leggere le email di Keycloak

In locale le email (verifica email, password dimenticata, cambio email) non partono davvero: si leggono su Mailpit, `http://localhost:8025`.

### Controllare lo storage

Con la CLI di AWS:

```bash
aws --endpoint-url http://localhost:8333 s3 ls
aws --endpoint-url http://localhost:8333 s3 ls s3://diary-documents
```

I bucket `diary-documents` (file degli utenti) e `feedback-documents` (screenshot dei feedback) li crea il backend all'avvio se mancano (`app.storage.create-buckets=true`, i nomi sono `app.storage.bucket` e `app.feedback.bucket`). Se lo storage è spento all'avvio il backend parte lo stesso e scrive un avviso nel log: riavviarlo, oppure creare i bucket a mano:

```bash
aws --endpoint-url http://localhost:8333 s3 mb s3://diary-documents
aws --endpoint-url http://localhost:8333 s3 mb s3://feedback-documents
```

### Leggere e cancellare i feedback

I feedback arrivano da `POST /feedback` (pubblico) e si gestiscono dalla Swagger UI con il token di un utente `admin` (in locale `admin1`, vedi «Dare il ruolo `admin` a un utente»):

- `GET /feedback` li elenca (filtri `type`, `status`, `email`, `createdFrom` e `createdTo` come date `yyyy-MM-dd`, i più recenti per primi); `GET /feedback/{id}` mostra il testo, le informazioni tecniche e gli screenshot con indirizzi temporanei di 5 minuti.
- `PATCH /feedback/{id}/read`, `/unread`, `/answered` e `/archive` cambiano lo stato.
- **Cancellazione su richiesta** (la Privacy la promette): cercare con `GET /feedback?email=...`, poi `DELETE /feedback/{id}`: cancella il feedback, i record degli screenshot e i file nel bucket `feedback-documents`.
- I feedback non si cancellano con `DELETE /me` perché non sono collegati all'account: chi chiede la cancellazione dei suoi dati va cercato per email.

### Ricostruire l'estensione di Keycloak

I sorgenti sono in `keycloak-extensions`, il jar compilato in `docker-compose/keycloak/providers/gdr-diary-kc-extension.jar`. Dopo averlo ricostruito riavviare Keycloak:

```bash
docker compose restart keycloak
```

---

## 3. Problemi noti

### Accesso e token

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 401 su ogni richiesta | Il token non ha l'audience `gdr-diary-backend` (emesso prima del mapper o da un altro client), è scaduto (l'access token dura 5 minuti) oppure l'`issuer-uri` non risponde. | Fare un nuovo login. Decodificare il token e controllare `aud`, `iss`, `exp`. Controllare che `http://localhost:8180/realms/gdr-diary-dev` risponda e che il client `gdr-diary-api` abbia il mapper dell'audience. |
| 403 | Nel token manca il ruolo `user` o `admin` in `realm_access.roles`. | Assegnare il ruolo o inserire l'utente nel gruppo `users`, poi nuovo login. |
| `GET /me` risponde 404 | L'utente non è ancora stato creato nel database. | Chiamare `POST /me` dopo il login (lo fa il frontend). |
| `POST /me` risponde 400 «The token has no email address» | Il token non ha il claim `email`. | Controllare che l'utente abbia un'email in Keycloak. |
| `DELETE /me` risponde 401 con `insufficient_user_authentication` | L'accesso è più vecchio di 5 minuti (`app.user.delete-max-auth-age`), oppure il token è stato preso con il password grant e non ha `auth_time`. | Rifare il login con la password dal frontend (Authorization Code con PKCE) e ripetere subito la chiamata. |

### Avvio

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Il backend non parte con un errore di configurazione | Le proprietà sono validate e un valore sbagliato blocca l'avvio. Endpoint, chiavi e bucket dello storage sono obbligatori, `url-expiry` va da 1 secondo a 7 giorni, le dimensioni devono essere maggiori di zero, `delete-max-auth-age` almeno 1 secondo. | Leggere il log: indica la proprietà. |
| Il build fallisce con un errore sulla versione di Java | È in uso un JDK diverso dal 25. | Controllare `java -version` e usare il JDK 25. |
| Connection refused sulla porta `5432` | `postgres` non è partito o non è `healthy`. | `docker compose ps`, poi `docker compose logs postgres`. |
| Il backend non parte con errori di Flyway o di schema | Il volume del database esisteva già prima dello script `init-db-postgres.sql`, che gira solo alla creazione del volume: lo schema `dev` manca. | Azzerare il database (vedi sezione 2). |

### Dati e API

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 409 «The resource was modified elsewhere, reload it» | Modifica concorrente. Per le pagine la `version` inviata non è più quella del server. | Ricaricare i dati e ripetere la modifica. |
| 409 «... changed while it was being deleted, try again» | Cancellazione di un personaggio, diario o account in corsa con una creazione. | Ripetere la chiamata. |
| 400 «Invalid page content at ...» | Il contenuto della pagina non rientra in quello che accetta il validatore (peso massimo 50 KB, profondità 50). | Il messaggio indica il punto. Se l'editor ha una funzione nuova, va aggiunta anche in `DiaryPageContentValidator`. Caso noto: incollando HTML con `<ol type="x">` l'editor produce un valore che il backend rifiuta. |
| 400 «... contains characters that cannot be stored» | Il testo contiene il carattere NUL o metà di un'emoji. | Togliere i caratteri dal testo. |
| `POST /feedback` risponde 429 con `Retry-After` | Più di 5 invii in un'ora dallo stesso indirizzo (`app.feedback.rate-limit-max-requests` e `rate-limit-window`). I contatori stanno in memoria. | Aspettare, oppure in sviluppo riavviare il backend. Dietro un reverse proxy, se il 429 arriva a tutti, l'indirizzo visto è quello del proxy: serve `server.forward-headers-strategy`. |
| `POST /feedback` risponde 201 ma il feedback non c'è | Il campo trappola `website` era compilato: il backend scrive nel log «Feedback discarded: the trap field was filled» e non salva niente. | Non mandare il campo `website`, o mandarlo vuoto. |
| `POST /feedback` risponde 400 «Too many files» o «Files too large» | Più di 3 immagini, oppure più di 5 MB in totale (`app.feedback.max-files`, `max-total-file-size`). | Ridurre gli screenshot. |

### Immagini e storage

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 413 su un upload | Il file supera i 5 MB (`spring.servlet.multipart.max-file-size`, richiesta 6 MB). | Ridurre il file o alzare i limiti. |
| 400 su un upload | Il contenuto vero non coincide con il tipo dichiarato o con l'estensione. Ammessi png, jpeg e webp. | Caricare un'immagine valida con il tipo corretto. |
| Upload o lettura con errori dello storage | `seaweedfs` fermo, chiavi sbagliate o bucket mancante. | `docker compose ps`, controllare chiavi e bucket (vedi «Controllare lo storage»). |
| L'immagine non si apre dopo un po' | L'indirizzo temporaneo scade dopo 5 minuti. | Richiedere di nuovo il personaggio o il diario per avere un indirizzo nuovo. |
| File nel bucket senza riga nel database | La cancellazione dei file è best effort: se fallisce, il file resta. Non c'è ancora una pulizia automatica. | Confrontare gli `object_key` della tabella `document` con gli oggetti del bucket e cancellare a mano quelli che non compaiono. |

### Keycloak

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Le modifiche al file del realm non hanno effetto | Il realm si importa solo alla prima creazione. | Vedi «Reimportare il realm di Keycloak». |
| L'importazione del realm fallisce | Un ruolo composito che include ruoli predefiniti come `uma_authorization`. | Dare il ruolo predefinito con un gruppo predefinito, non con un ruolo composito. |
| La pagina di login ha il tema standard | `gdr-diary-ui` non è nella cartella accanto a `gdr-diary-api` e il tema non viene montato. | Controllare che esista `../../gdr-diary-ui/keycloak-theme/themes` rispetto a `docker-compose`, poi `docker compose up -d`. |
| L'email di verifica non arriva | In locale le email vanno a Mailpit. | Aprire `http://localhost:8025`. |
| Il cambio email non si comporta come previsto | Manca il jar dell'estensione in `providers` o Keycloak non è stato riavviato dopo averlo ricostruito. Serve anche la funzione `update-email`, attiva nel compose. | Ricostruire il jar e `docker compose restart keycloak`. |

---

## 4. Backup e ripristino

Valido per lo sviluppo in locale. Database e bucket vanno salvati insieme, perché il database punta agli oggetti dello storage.

### Database applicativo

```bash
cd docker-compose
docker compose exec postgres pg_dump -U gdr-diary-api gdr-diary-api > backup.sql
```

Ripristino su un database vuoto (dopo averlo azzerato, vedi sezione 2):

```bash
docker compose exec -T postgres psql -U gdr-diary-api gdr-diary-api < backup.sql
```

### Immagini

Gli oggetti stanno nel volume `seaweedfs-data`. Per una copia leggibile:

```bash
aws --endpoint-url http://localhost:8333 s3 sync s3://diary-documents ./backup-immagini
aws --endpoint-url http://localhost:8333 s3 sync s3://feedback-documents ./backup-feedback
```

Per ripristinarle si inverte la sincronizzazione.

### Keycloak

Gli utenti stanno nel volume `postgres-keycloak-data`. In sviluppo non si salva: il realm e gli utenti di prova si ricreano dal file JSON.

---

## 5. Produzione

Ancora da definire. Prima di andare online:

- [ ] Dove gira il servizio e con quale budget
- [ ] Numeri attesi: utenti, spazio per utente, disponibilità richiesta e frequenza dei backup
- [ ] Invio reale delle email di Keycloak
- [ ] Protezione dai tentativi ripetuti di accesso e regole sulle password
- [ ] Realm separato di produzione, senza login diretto con password; valutare un client confidenziale
- [ ] CORS limitato all'origine esatta del frontend (`app.cors.allowed-origins`)
- [ ] Intestazioni `X-Content-Type-Options: nosniff` e `Cache-Control: no-store` sulle risposte con dati personali
- [ ] Chiavi e password fuori dal repository
- [ ] Pulizia dei file orfani nello storage e antivirus sui file caricati
- [ ] Limite alla dimensione della richiesta a livello di server, oggi il peso si controlla dopo la lettura del corpo
- [ ] Feedback: `server.forward-headers-strategy` dietro il reverse proxy (altrimenti il limite per indirizzo vale per tutti), bucket `feedback-documents` nei backup, durata di conservazione dei feedback, notifica al titolare (oggi il backend non manda email)

# gdr-diary-api · Runbook

> Procedure operative e risoluzione dei problemi di **gdr-diary-api** in ambiente di sviluppo locale. La produzione non è ancora definita.

| | |
|---|---|
| **Ultimo aggiornamento** | 3 novembre 2026 |
| **Per l'installazione e la descrizione del progetto** | vedi il [README](README.md) |

**Indice**

- [1. Panoramica operativa](#1-panoramica-operativa)
- [2. Avvio e arresto](#2-avvio-e-arresto)
- [3. Procedure ordinarie](#3-procedure-ordinarie)
- [4. Problemi noti](#4-problemi-noti)
- [5. Backup e ripristino](#5-backup-e-ripristino)

---

## 1. Panoramica operativa

### Componenti

Il backend gira fuori da Docker (`./mvnw spring-boot:run`), gli altri servizi nei container del `docker-compose`.

| Servizio | Cosa fa | Dipende da | Controllo di stato |
|---|---|---|---|
| **gdr-diary-api** | Backend REST | PostgreSQL, SeaweedFS, Keycloak (per le chiavi dei token) | `/actuator/health` |
| `postgres` | Database applicativo | – | `docker compose ps` (healthy) |
| `postgres-keycloak` | Database di Keycloak | – | `docker compose ps` (healthy) |
| `keycloak` | Login, token, ruoli | `postgres-keycloak`, `mailpit` | `docker compose ps` (healthy) |
| `seaweedfs` | Storage S3 delle immagini | – | nessuno |
| `mailpit` | Email di Keycloak | – | nessuno |

Le porte di ogni servizio sono nel [README › Mapping delle porte](README.md#mapping-delle-porte).

All'avvio il backend non contatta Keycloak: scarica le chiavi di firma alla prima richiesta con un token.

### Dove guardare i log

| Cosa | Come |
|---|---|
| Backend | Console in cui gira `./mvnw spring-boot:run`. Il package `it.gdr.diary` logga a livello `DEBUG`. |
| Container | Dalla cartella `docker-compose`: `docker compose logs -f <servizio>` (`postgres`, `postgres-keycloak`, `keycloak`, `mailpit`, `seaweedfs`). |

---

## 2. Avvio e arresto

### Avviare il progetto

**Prerequisiti**: vedi [README › Prerequisiti](README.md#prerequisiti).

1. Avviare i container e il backend con i comandi di [README › Avvio in ambiente locale](README.md#3-avvio-in-ambiente-locale).

2. Controllare lo stato dei container, dalla cartella `docker-compose`:

   ```bash
   docker compose ps
   ```

   Atteso: `postgres`, `postgres-keycloak` e `keycloak` in stato `healthy`. Keycloak impiega qualche decina di secondi.

3. Controllare il log del backend.

   Atteso: compaiono le migrazioni di Flyway e la riga `Started GdrDiaryApiApplication`.

4. Verificare il backend con l'health check. L'endpoint è `/actuator/health`: [Health check](http://localhost:8080/api/v1/gdr-diary-api/actuator/health).

   Atteso: `{"status":"UP"}`. Chi può vedere il dettaglio: vedi [README › Health Check](README.md#health-check).

### Fermare il progetto

Fermare il backend con `Ctrl+C`, poi dalla cartella `docker-compose`:

```bash
docker compose down        # ferma i container, i dati restano
docker compose down -v     # ferma e cancella tutti i dati (reset totale)
```

### Riavviare un singolo servizio

```bash
docker compose restart <servizio>
```

---

## 3. Procedure ordinarie

### Reimportare il realm di Keycloak

Keycloak importa `docker-compose/keycloak/gdr-diary-dev-realm.json` solo alla prima creazione. Dopo una modifica al file, dalla cartella `docker-compose`:

```bash
docker compose rm -sf keycloak postgres-keycloak
docker volume rm docker-compose_postgres-keycloak-data
docker compose up -d
```

Il nome del volume è il nome della cartella del compose (`docker-compose`) seguito da `_` e dal nome del volume.

> **Attenzione**: gli utenti ricreati hanno un nuovo `sub`, quindi i dati dell'applicazione legati ai vecchi utenti non sono più raggiungibili. In sviluppo conviene azzerare anche il database (vedi [Azzerare il database dell'applicazione](#azzerare-il-database-dellapplicazione)).

Verifica: [la configurazione del realm](http://localhost:8180/realms/gdr-diary-dev/.well-known/openid-configuration) risponde con un JSON.

> L'opzione "Always set password on register form" si accende da sola quando il realm viene creato (la fa l'[estensione di Keycloak](#ricostruire-lestensione-di-keycloak)). Se dopo una reimportazione la registrazione non chiede la password, vedi [Riaccendere la password obbligatoria in registrazione](#riaccendere-la-password-obbligatoria-in-registrazione).

In alternativa si cambiano le impostazioni dalla console di Keycloak e si riportano a mano nel file JSON.

### Riaccendere la password obbligatoria in registrazione

L'interruttore di Keycloak **"Always set password on register form"** non si può scrivere nel file JSON del realm. Lo accende da solo l'estensione (`RegistrationPasswordDefaultsFactory`) ogni volta che un realm viene creato o importato, quindi di norma non serve fare nulla: in `docker compose logs keycloak` compare `password required on the registration form`.

Questa procedura a mano serve solo se l'estensione non è caricata (jar mancante o vecchio) o se il realm esisteva già prima di installarla. Se l'interruttore resta spento, la pagina di registrazione non chiede la password, l'utente nasce senza e al primo accesso Keycloak gli impone "Aggiorna password".

1. Apri la [console di Keycloak](http://localhost:8180) e accedi con `admin` / `secret`.
2. In alto a sinistra apri il selettore dei realm e scegli **GDR Diary (dev)** (non `master`).
3. Nel menu a sinistra, sotto *Configure*, clicca **Authentication**.
4. Nella scheda **Flows** clicca il flusso **registration**.
5. Nella riga **Password Validation** (dentro "registration form") clicca l'**ingranaggio** a destra. Si apre "Password Validation config".
6. Nel campo **Alias** scrivi un nome qualsiasi, per esempio `registration-password-always` (è obbligatorio).
7. Accendi l'interruttore **Always set password on register form** (deve dire **On**).
8. Clicca **Save**: compare "Successfully saved the config". Poi ricarica la pagina e riapri l'ingranaggio: l'interruttore deve essere ancora **On** (a volte il salvataggio non resta).
9. Verifica: apri la pagina di registrazione dall'app (pulsante "Registrati"). Devono esserci i campi **Password** e **Conferma password**. Registra un utente di prova: al primo accesso non deve comparire "Aggiorna password".

Se non sei sicuro che sia già acceso, riapri l'ingranaggio del passo 5: l'interruttore è lì. Se c'è già l'alias e dice **On**, non serve altro.

### Azzerare il database dell'applicazione

Dalla cartella `docker-compose`:

```bash
docker compose rm -sf postgres
docker volume rm docker-compose_postgres-data
docker compose up -d postgres
```

Al riavvio del backend Flyway ricrea lo schema. Verifica: l'health check risponde `UP`.

Le immagini nello storage non vengono toccate: per un reset coerente cancellare anche il volume `docker-compose_seaweedfs-data`, oppure usare `docker compose down -v`.

### Ottenere un token per provare le API

Il login con password diretta (Direct Access Grants) è disattivato nel client `gdr-diary-api`, anche in sviluppo: il token si prende da una sessione del frontend.

**Prerequisito**: container, backend e frontend accesi ([README › Avvio in ambiente locale](README.md#3-avvio-in-ambiente-locale), [gdr-diary-ui › Avvio in ambiente locale](../gdr-diary-ui/README.md#3-avvio-in-ambiente-locale)).

1. Aprire [http://localhost:5173](http://localhost:5173) e accedere con `user1` (o `admin1` per le operazioni da `admin`). Le credenziali sono in [README › Configurazione Keycloak Realm](README.md#configurazione-keycloak-realm).

2. Aprire gli strumenti di sviluppo del browser (`F12`), scheda **Network**, e ricaricare la pagina.

3. Selezionare una richiesta verso `localhost:8080` (per esempio `me`) e, in **Request Headers**, copiare il valore di `Authorization` senza la parola `Bearer`.

4. In [Swagger UI](http://localhost:8080/api/v1/gdr-diary-api/swagger-ui.html) premere **Authorize**, incollare il token nel campo `bearerAuth` e confermare. Le richieste di **Try it out** lo inviano da sole.

In alternativa, da terminale:

```bash
TOKEN=<token copiato>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/gdr-diary-api/me
```

Atteso: risposta `200` con email, `premium` e `settings` dell'utente.

> **Nota**: l'access token dura 5 minuti. Quando le richieste tornano a rispondere `401`, ricaricare la pagina del frontend e ripetere dal punto 3. `DELETE /me` richiede un accesso recente (`delete-max-auth-age`): conviene fare un nuovo login subito prima.

### Dare il ruolo `admin` a un utente

[Console Keycloak](http://localhost:8180) (credenziali in [README › Configurazione Keycloak Realm](README.md#configurazione-keycloak-realm)) → realm `gdr-diary-dev` → **Users** → l'utente → **Role mapping** → **Assign role** → `admin`.

Verifica: l'utente fa un nuovo login, perché i ruoli sono scritti nel token.

### Cancellare un utente

L'ordine è: prima `DELETE /me` sul backend, poi la cancellazione dell'account su Keycloak (la lancia il frontend con l'azione `delete_account`).

- Se il frontend si ferma sull'ultimo passo, `DELETE /me` si può richiamare: se l'utente non c'è più nel database risponde 200 senza fare niente.
- L'account su Keycloak si può cancellare a mano dalla console: **Users** → l'utente → **Delete**.

### Leggere le email di Keycloak

In locale le email (verifica email, password dimenticata, cambio email) non partono davvero: si leggono su [Mailpit](http://localhost:8025).

### Controllare lo storage

Servono la CLI di AWS e le chiavi dello storage ([README › Configurazione Storage](README.md#configurazione-storage)):

```bash
export AWS_ACCESS_KEY_ID=admin
export AWS_SECRET_ACCESS_KEY=secret
export AWS_DEFAULT_REGION=us-east-1
```

Elencare i bucket e gli oggetti:

```bash
aws --endpoint-url http://localhost:8333 s3 ls
aws --endpoint-url http://localhost:8333 s3 ls s3://diary-documents
```

Atteso: il bucket `diary-documents` compare nell'elenco. Se manca, crearlo:

```bash
aws --endpoint-url http://localhost:8333 s3 mb s3://diary-documents
```

### Ricostruire l'estensione di Keycloak

Il jar compilato sta in `docker-compose/keycloak/providers/`, i sorgenti in `keycloak-extensions/gdr-diary-kc-extension`. Serve Docker: la compilazione gira in un container Maven.

Dalla radice del progetto:

```bash
sh keycloak-extensions/build-keycloak-extensions.sh
```

Atteso: lo script termina elencando il jar in `docker-compose/keycloak/providers`. Poi, dalla cartella `docker-compose`:

```bash
docker compose up -d --force-recreate keycloak
```

Il jar va compilato per la stessa versione di Keycloak del compose (26.7.0): se la compilazione fallisce l'errore indica il metodo cambiato. Dettagli in `keycloak-extensions/gdr-diary-kc-extension/README.md`.

---

## 4. Problemi noti

### Accesso e token

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| 401 su ogni richiesta | Il token non ha l'audience `gdr-diary-backend` (emesso prima del mapper o da un altro client), è scaduto (l'access token dura 5 minuti) oppure l'`issuer-uri` non risponde. | Fare un nuovo login. Decodificare il token e controllare `aud`, `iss`, `exp`. Controllare che il realm risponda (vedi [Reimportare il realm di Keycloak](#reimportare-il-realm-di-keycloak)) e che il client `gdr-diary-api` abbia il mapper dell'audience. |
| 403 su ogni richiesta | Nel token manca il ruolo `user` o `admin` in `realm_access.roles`. | Assegnare il ruolo o inserire l'utente nel gruppo `users`, poi nuovo login. |

### Avvio

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Il backend non parte con un errore di configurazione | Le proprietà sono validate e un valore sbagliato blocca l'avvio. Endpoint, chiavi e bucket dello storage sono obbligatori, `url-expiry` va da 1 secondo a 7 giorni, le dimensioni devono essere maggiori di zero, `delete-max-auth-age` almeno 1 secondo. | Leggere il log: indica la proprietà. |
| Il build fallisce con un errore sulla versione di Java | È in uso un JDK diverso dal 25. | Controllare `java -version` e usare il JDK 25. |
| Connection refused sulla porta `5432` | `postgres` non è partito o non è `healthy`. | `docker compose ps`, poi `docker compose logs postgres`. |
| Il backend non parte con errori di Flyway o di schema | Il volume del database esisteva già prima dello script `init-db-postgres.sql`, che gira solo alla creazione del volume: lo schema `dev` manca. | Azzerare il database (vedi [Azzerare il database dell'applicazione](#azzerare-il-database-dellapplicazione)). |

### Immagini e storage

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Upload o lettura delle immagini con errori dello storage | `seaweedfs` fermo, chiavi sbagliate o bucket mancante. | `docker compose ps`, poi controllare chiavi e bucket (vedi [Controllare lo storage](#controllare-lo-storage)). |
| File nel bucket senza riga nel database | La cancellazione dei file è best effort: se fallisce, il file resta. Non c'è ancora una pulizia automatica. | Confrontare gli `object_key` della tabella `document` con gli oggetti del bucket e cancellare a mano quelli che non compaiono. |

### Keycloak

| Sintomo | Causa probabile | Rimedio |
|---|---|---|
| Le modifiche al file del realm non hanno effetto | Il realm si importa solo alla prima creazione. | Vedi [Reimportare il realm di Keycloak](#reimportare-il-realm-di-keycloak). |
| L'importazione del realm fallisce | Un ruolo composito che include ruoli predefiniti come `uma_authorization`. | Dare il ruolo predefinito con un gruppo predefinito, non con un ruolo composito. |
| La pagina di login ha il tema standard | `gdr-diary-ui` non è nella cartella accanto a `gdr-diary-api` e il tema non viene montato. | Controllare che esista `../../gdr-diary-ui/keycloak-theme/themes` rispetto a `docker-compose`, poi `docker compose up -d`. |
| L'email di verifica non arriva | In locale le email vanno a Mailpit. | Aprire [Mailpit](http://localhost:8025). |
| Dopo la registrazione, al primo accesso compare "Aggiorna password"; la pagina di registrazione non ha i campi password (o non li esige) | L'interruttore "Always set password on register form" del flusso `registration` è spento: l'estensione lo accende solo alla creazione del realm, quindi un realm già esistente o un jar vecchio lo lasciano spento. | Vedi [Riaccendere la password obbligatoria in registrazione](#riaccendere-la-password-obbligatoria-in-registrazione). |
| Il cambio email non si comporta come previsto | Manca il jar dell'estensione in `providers`, è per un'altra versione di Keycloak o Keycloak non è stato ricreato dopo averlo ricostruito. Serve anche la funzione `update-email`, attiva nel compose. | Vedi [Ricostruire l'estensione di Keycloak](#ricostruire-lestensione-di-keycloak). |

---

## 5. Backup e ripristino

Valido per lo sviluppo in locale. Database e bucket vanno salvati insieme, perché il database punta agli oggetti dello storage.

### Database applicativo

Dalla cartella `docker-compose`:

```bash
docker compose exec postgres pg_dump -U gdr-diary-api gdr-diary-api > backup.sql
```

Ripristino su un database vuoto (dopo averlo azzerato, vedi [Azzerare il database dell'applicazione](#azzerare-il-database-dellapplicazione)):

```bash
docker compose exec -T postgres psql -U gdr-diary-api gdr-diary-api < backup.sql
```

### Immagini

Gli oggetti stanno nel volume `seaweedfs-data`. Con le variabili d'ambiente descritte in [Controllare lo storage](#controllare-lo-storage):

```bash
aws --endpoint-url http://localhost:8333 s3 sync s3://diary-documents ./backup-immagini
```

Per ripristinarle:

```bash
aws --endpoint-url http://localhost:8333 s3 sync ./backup-immagini s3://diary-documents
```

### Keycloak

Gli utenti stanno nel volume `postgres-keycloak-data`. In sviluppo non si salva: il realm e gli utenti di prova si ricreano dal file JSON.

# gdr-diary-api

> Backend di **GDR Diary**, l'app per tenere i diari dei personaggi dei giochi di ruolo.
>
> Per verificare, fermare e riparare il servizio vedi il [Runbook](RUNBOOK.md).
>
> Per l'architettura complessiva vedi il [documento arc42](../documents/gdr-diary-architettura-arc42.pdf). Per i due progetti insieme vedi il [README della cartella gdr-diary](../README.md).

**Indice**

- [Descrizione dell'applicativo](#descrizione-dellapplicativo)
- [1. Configurazione Dettagliata](#1-configurazione-dettagliata)
- [2. Struttura del Progetto](#2-struttura-del-progetto)
- [3. Avvio in ambiente locale](#3-avvio-in-ambiente-locale)
- [4. API Documentation](#4-api-documentation)
- [5. Integrazione con sistemi esterni](#5-integrazione-con-sistemi-esterni)
- [6. Testing](#6-testing)
- [7. Sicurezza e Compliance](#7-sicurezza-e-compliance)

---

## Descrizione dell'applicativo

**gdr-diary-api** è l'applicazione backend di *GDR Diary*, pensata per la gestione dei diari dei personaggi dei giochi di ruolo.

Ogni utente ha dei *personaggi*, ogni personaggio ha dei *diari* e ogni diario ha fino a 100 *pagine*. L'applicativo gestisce inoltre le immagini (avatar e copertine) e la cancellazione dei dati dell'account.

| Ambito | Tecnologia |
|---|---|
| Linguaggio | Java 25 |
| Framework | Spring Boot 4.1 |
| ORM | Ebean 19 |
| Database | PostgreSQL 16, migrazioni con Flyway |
| Autenticazione | Keycloak 26.7 |
| Storage file | SeaweedFS (compatibile S3) |
| Documentazione API | springdoc-openapi |
| Test | JUnit 5, Mockito, AssertJ, Testcontainers |
| Qualità | JaCoCo, SonarQube |

---

## 1. Configurazione Dettagliata

### Setup del Database in Locale

| | |
|---|---|
| **Tipo di database** | PostgreSQL 16 |
| **Porta** | `5432` (host) → `5432` (container) |
| **Avviamento** | vedi la sezione [Avvio in ambiente locale](#3-avvio-in-ambiente-locale) |
| **Migrazione** | All'avvio del database viene eseguito `init-db-postgres.sql`, che crea lo schema `dev`. All'avvio dell'applicativo ***Flyway*** esegue le migrazioni e popola lo schema automaticamente. |

### Configurazione Keycloak Realm

Il realm `gdr-diary-dev` viene importato all'avvio di Keycloak dal file `docker-compose/keycloak/gdr-diary-dev-realm.json`.

> **Nota**: l'importazione avviene **solo alla prima creazione**. Se il file viene modificato vedi [Runbook › Reimportare il realm di Keycloak](RUNBOOK.md#reimportare-il-realm-di-keycloak).

Il realm contiene:

- **Client** `gdr-diary-api`: pubblico, Authorization Code con PKCE, usato dal frontend.
- **Audience**: un mapper del client scrive `gdr-diary-backend` nell'access token. Lo stesso valore è in `spring.security.oauth2.resourceserver.jwt.audiences`: se viene cambiato, va cambiato in entrambi i posti.
- **Ruoli**: `user`, assegnato in automatico alla registrazione tramite il gruppo `users`, e `admin`, assegnato a mano dalla console.

**Credenziali di sviluppo** (valide solo in locale):

| Cosa | Utente | Password | Ruolo |
|---|---|---|---|
| Console Keycloak (`http://localhost:8180`) | `admin` | `secret` | – |
| Utente di prova | `user1` | `secret` | `user` |
| Utente di prova | `admin1` | `secret` | `admin` |

### Mapping delle porte

Per questo applicativo, in ambiente Dev, verranno utilizzate le seguenti porte:

| Servizio | Porta host | Porta container | Note |
|---|---|---|---|
| **gdr-diary-api** *(backend)* | `8080` | – | Context path `/api/v1/gdr-diary-api` |
| **gdr-diary-ui** *(frontend)* | `5173` | – | Origine ammessa da CORS e da Keycloak |
| PostgreSQL (applicazione) | `5432` | `5432` | Database e utente `gdr-diary-api` |
| PostgreSQL (Keycloak) | `5433` | `5432` | Database e utente `keycloak` |
| Keycloak | `8180` | `8080` | Console di amministrazione |
| SeaweedFS | `8333` | `8333` | Storage S3 |
| Mailpit, protocollo SMTP | `1025` | `1025` | Intercetta le email di Keycloak |
| Mailpit, interfaccia web | `8025` | `8025` | `http://localhost:8025` |

### Configurazione Storage

Le immagini sono salvate in uno storage compatibile S3. In locale viene utilizzato **SeaweedFS**.

| Proprietà (`application.properties`) | Valore |
|---|---|
| `app.storage.endpoint` | `http://localhost:8333` |
| `app.storage.bucket` | `diary-documents` |
| `app.storage.access-key` | `admin` |
| `app.storage.secret-key` | `secret` |

### Configurazione SMTP

L'invio delle email (verifica email, password dimenticata) è gestito da Keycloak, non dal backend. Di default il realm include una configurazione preimpostata per funzionare con **Mailpit**, che intercetta le email senza inviarle. Per leggerle vedi [Runbook › Leggere le email di Keycloak](RUNBOOK.md#leggere-le-email-di-keycloak).

| Parametro | Valore |
|---|---|
| `host` | `mailpit` |
| `port` | `1025` |
| `from` | `no-reply@gdr-diary.local` |
| `auth` | `false` |
| `ssl` | `false` |
| `starttls` | `false` |

### Proprietà principali

| Proprietà | Valore locale | Significato |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/gdr-diary-api` | Database applicativo |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8180/realms/gdr-diary-dev` | Realm Keycloak che firma i token |
| `spring.security.oauth2.resourceserver.jwt.audiences` | `gdr-diary-backend` | Audience richiesta nel token |
| `app.cors.allowed-origins` | `http://localhost:5173` | Origine del frontend |
| `spring.servlet.multipart.max-file-size` | `5MB` | Peso massimo di un file caricato |

---

## 2. Struttura del Progetto

### gdr-diary-api *(Backend)*

Panoramica delle directory principali dell'applicativo:

| Directory | Contenuto |
|---|---|
| `/docker-compose` | Tutti i file necessari per l'avvio dei container Docker e la loro inizializzazione per far partire il progetto in locale, compreso il realm di Keycloak. |
| `/keycloak-extensions` | I sorgenti dell'estensione di Keycloak per il cambio email. |
| `/resources/dbinit/postgres` | I file `.sql` necessari a ***Flyway*** per effettuare la migrazione del database all'avvio dell'applicativo. |
| `/java/.../controller` | Le classi che espongono gli endpoint dell'applicativo. |
| `/java/.../config` | Le classi di configurazione dell'applicativo. |
| `/java/.../dto` | I `Data Transfer Object` utilizzati per lo scambio dati tra i componenti. |
| `/java/.../exception` | Le eccezioni di business e la loro traduzione in risposte. |
| `/java/.../model` | Le `entity` del database. |
| `/java/.../security` | La lettura del JWT e dei ruoli. |
| `/java/.../services` | La logica di business dell'applicativo. |
| `/java/.../validation` | I controlli sul testo e sul contenuto delle pagine. |

---

## 3. Avvio in ambiente locale

### Prerequisiti

- **JDK 25**
- **Docker** con Docker Compose
- Il repository **gdr-diary-ui** nella stessa cartella di **gdr-diary-api**: il `docker-compose` ne monta il tema grafico di Keycloak.

### gdr-diary-api *(Backend)*

Per avviare l'applicativo **gdr-diary-api** utilizzare il terminale e posizionarsi nella root della cartella dell'applicativo.

#### 1. Docker

Posizionarsi nella cartella `docker-compose` ed eseguire il comando:

```bash
docker compose up -d
```

#### 2. Applicativo backend

Posizionarsi nella root della cartella progetto ed eseguire il comando:

```bash
./mvnw spring-boot:run
```

Per verificare che tutto sia avviato, fermare o riavviare i servizi vedi [Runbook › Avvio e arresto](RUNBOOK.md#2-avvio-e-arresto).

---

## 4. API Documentation

La documentazione inerente alle API è presente nello `Swagger UI`, raggiungibile una volta avviato l'applicativo backend al seguente link:

**[Swagger UI](http://localhost:8080/api/v1/gdr-diary-api/swagger-ui.html)**

Gli endpoint richiedono un token (pulsante **Authorize** di Swagger UI). Il login con password diretta è disattivato, quindi il token si ricava da una sessione del frontend: vedi [Runbook › Ottenere un token per provare le API](RUNBOOK.md#ottenere-un-token-per-provare-le-api).

### Health Check

Il progetto include degli health check per verificare lo stato dei servizi:

- Per i container Docker sono configurati all'interno del **`docker-compose.yml`**.
- Per il backend è esposto `/actuator/health`: [Health check](http://localhost:8080/api/v1/gdr-diary-api/actuator/health). Il dettaglio (database, disco) e le altre risorse di Actuator sono riservati al ruolo `admin`.
- Come controllarlo all'avvio: [Runbook › Avviare il progetto](RUNBOOK.md#avviare-il-progetto).

---

## 5. Integrazione con sistemi esterni

L'applicativo effettua chiamate a servizi esterni e riceve a sua volta interrogazioni da sistemi terzi, come descritto di seguito.

### Sistemi esterni interrogati

| Sistema | Utilizzo |
|---|---|
| **Keycloak** | Validazione dei token: il backend ne scarica la chiave pubblica dal realm. |
| **Storage S3** | Salvataggio e lettura delle immagini (avatar e copertine). In locale SeaweedFS. |

### Sistemi esterni chiamanti

| Sistema | Utilizzo |
|---|---|
| **gdr-diary-ui** | Il frontend è l'unico client dell'API. |

---

## 6. Testing

L'applicativo è già configurato per supportare `SonarQube` e `Jacoco`.

### gdr-diary-api Test

Posizionarsi nella root dell'applicativo da un terminale ed eseguire uno dei comandi:

| Comando | Cosa esegue |
|---|---|
| `./mvnw verify` | Test unitari e di integrazione. Questi ultimi avviano PostgreSQL, SeaweedFS e Keycloak con `Testcontainers`: serve Docker. |
| `./mvnw test` | Solo i test unitari. |

Il report generato da `Jacoco` sarà disponibile in: `/target/site/jacoco/index.html`

**Risposte**: nel terminale, i test possono restituire uno dei seguenti messaggi:

```
BUILD FAILURE   <- in caso di fallimento dei test
BUILD SUCCESS   <- in caso di successo dei test
```

In entrambi i casi sarà possibile visualizzare un resoconto dettagliato nel terminale, simile al seguente:

```
Tests run: N, Failures: K, Errors: Q, Skipped: X
```

### Analisi con SonarQube

1. Aprire il file `pom.xml` e inserire i valori corretti per le seguenti voci:
   ```xml
   <sonar.host.url>URL_DI_SONARQUBE</sonar.host.url>
   <sonar.projectKey>PROJECT_KEY</sonar.projectKey>
   ```
2. Utilizzare il comando fornito direttamente dalla console di `SonarQube` nel terminale dell'applicativo.

> **Nota**: assicurarsi di essere posizionati nella root del progetto quando si esegue il comando.

---

## 7. Sicurezza e Compliance

Il progetto adotta i seguenti strumenti per la sicurezza:

- Autenticazione tramite Keycloak OIDC
- Autorizzazione basata su ruoli (`user`, `admin`)
- Token validati per firma, scadenza e audience (`gdr-diary-backend`)
- Comunicazioni HTTPS in produzione

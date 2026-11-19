# liber-heroum

> **Liber Heroum**, l'app per tenere i diari dei personaggi dei giochi di ruolo. Questa cartella raccoglie i due progetti che la compongono e la documentazione generale.

**Indice**

- [Descrizione dell'applicativo](#descrizione-dellapplicativo)
- [1. Struttura della cartella](#1-struttura-della-cartella)
- [2. Avvio in ambiente locale](#2-avvio-in-ambiente-locale)
- [3. Documentazione](#3-documentazione)
- [4. Licenza](#4-licenza)

---

## Descrizione dell'applicativo

**Liber Heroum** è una web app per chi gioca di ruolo. Ogni utente ha dei *personaggi*, ogni personaggio ha dei *diari* e ogni diario ha fino a 100 *pagine* da scrivere e sfogliare.

Si compone di due progetti:

| Progetto | Cosa è | Tecnologia | Documentazione |
|---|---|---|---|
| **liber-heroum-api** | Backend REST | Java 25, Spring Boot 4.1 | [README](liber-heroum-api/README.md) · [Runbook](liber-heroum-api/RUNBOOK.md) |
| **liber-heroum-ui** | Frontend, applicazione a pagina singola | SvelteKit 3, Svelte 5, TypeScript | [README](liber-heroum-ui/README.md) · [Runbook](liber-heroum-ui/RUNBOOK.md) |

---

## 1. Struttura della cartella

| Directory | Contenuto |
|---|---|
| `/liber-heroum-api` | Il backend, con i container di sviluppo (database, Keycloak, storage, email) e l'estensione di Keycloak. |
| `/liber-heroum-ui` | Il frontend, con il tema grafico di Keycloak. |
| `/documents` | La documentazione generale: l'architettura e i documenti di lavoro (idea del progetto, stato dei lavori, design delle schermate, informativa privacy, contratto dell'API). |

> **Nota**: `liber-heroum-api` e `liber-heroum-ui` devono stare nella stessa cartella. Il `docker-compose` del backend monta il tema di Keycloak da `liber-heroum-ui/keycloak-theme`: se manca, la pagina di login ha il tema standard.

---

## 2. Avvio in ambiente locale

### Prerequisiti

- **JDK 25** e **Docker** con Docker Compose, per il backend
- **Node.js 22.17 o successivo** e npm, per il frontend

Il dettaglio è in [liber-heroum-api › Prerequisiti](liber-heroum-api/README.md#prerequisiti) e [liber-heroum-ui › Prerequisiti](liber-heroum-ui/README.md#prerequisiti).

### Ordine di avvio

Il frontend ha bisogno del backend e di Keycloak, il backend ha bisogno dei container: si parte da questi.

#### 1. Container

Dalla cartella `liber-heroum-api/docker-compose`:

```bash
docker compose up -d
```

#### 2. Backend

Dalla cartella `liber-heroum-api`:

```bash
./mvnw spring-boot:run
```

#### 3. Frontend

Dalla cartella `liber-heroum-ui`:

```bash
cp .env.example .env
npm install
npm run dev
```

L'applicazione è raggiungibile su [http://localhost:5173](http://localhost:5173). Le credenziali degli utenti di prova sono in [liber-heroum-api › Configurazione Keycloak Realm](liber-heroum-api/README.md#configurazione-keycloak-realm), le porte in [liber-heroum-api › Mapping delle porte](liber-heroum-api/README.md#mapping-delle-porte).

Per controllare che tutto sia acceso, fermare o riavviare i servizi vedi i runbook: [backend](liber-heroum-api/RUNBOOK.md#2-avvio-e-arresto) e [frontend](liber-heroum-ui/RUNBOOK.md#2-avvio-e-arresto).

---

## 3. Documentazione

| Documento | Contenuto |
|---|---|
| [Architettura (arc42)](documents/liber-heroum-architettura-arc42.pdf) | Contesto, container, modello dei dati, decisioni e scenari principali (login, salvataggio di una pagina, caricamento di un'immagine, cancellazione dell'account). |
| [README del backend](liber-heroum-api/README.md) | Configurazione, struttura, avvio, API e test del backend. |
| [Runbook del backend](liber-heroum-api/RUNBOOK.md) | Procedure operative e problemi noti del backend, dei container e di Keycloak. |
| [README del frontend](liber-heroum-ui/README.md) | Configurazione, struttura, avvio e test del frontend. |
| [Runbook del frontend](liber-heroum-ui/RUNBOOK.md) | Procedure operative e problemi noti del frontend. |
| [Changelog](CHANGELOG.md) | Le modifiche rilevanti del progetto. |

---

## 4. Licenza

Il progetto è distribuito con licenza **PolyForm Noncommercial 1.0.0**. Il testo è nel file `LICENSE` di ciascun progetto.

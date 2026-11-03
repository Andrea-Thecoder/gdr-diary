# gdr-diary-ui

> Frontend di **GDR Diary**, l'app per tenere i diari dei personaggi dei giochi di ruolo.
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

**gdr-diary-ui** è l'applicazione frontend di *GDR Diary*, pensata per la gestione dei diari dei personaggi dei giochi di ruolo.

È una **SPA pura**: SvelteKit si usa solo per router, build e struttura, non c'è nessuna parte server. Il browser parla direttamente con Keycloak (login e registrazione) e con l'API [gdr-diary-api](../gdr-diary-api/README.md). Il token di accesso resta solo in memoria.

| Ambito | Tecnologia |
|---|---|
| Linguaggio | TypeScript (strict) |
| Framework | SvelteKit 3, Svelte 5 (adapter statico con fallback `index.html`) |
| Stile | Tailwind CSS 4, DaisyUI 5 |
| Autenticazione | keycloak-js (Authorization Code con PKCE) |
| Client API | openapi-fetch, openapi-typescript |
| Editor delle pagine | Tiptap 3 |
| Validazione | zod |
| Test | Vitest, Playwright |
| Qualità | ESLint, Prettier, svelte-check |

---

## 1. Configurazione Dettagliata

### Variabili d'ambiente

Sono lette al build (prefisso `VITE_`) e validate all'avvio in `src/lib/env.ts`. Il file `.env` non va nel repository: si parte da `.env.example`.

| Variabile | Valore locale | Significato |
|---|---|---|
| `VITE_KEYCLOAK_URL` | `http://localhost:8180` | Indirizzo di Keycloak |
| `VITE_KEYCLOAK_REALM` | `gdr-diary-dev` | Realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `gdr-diary-api` | Client Keycloak del frontend |
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1/gdr-diary-api` | Indirizzo dell'API |
| `VITE_THEME_COOKIE_DOMAIN` | *(vuota)* | Opzionale, solo online: dominio del cookie del tema condiviso con le pagine di login di Keycloak |
| `VITE_PRIVACY_CONTROLLER_NAME` | *(vuota)* | Opzionale: nome del titolare mostrato in `/privacy` |
| `VITE_PRIVACY_CONTACT_EMAIL` | *(vuota)* | Opzionale: recapito mostrato in `/privacy` |

> **Nota**: le due variabili della privacy servono entrambe, altrimenti la pagina dice che i dati verranno aggiunti.

### Configurazione Keycloak Realm

Il realm `gdr-diary-dev` e il client `gdr-diary-api` sono definiti nel backend: vedi [gdr-diary-api › Configurazione Keycloak Realm](../gdr-diary-api/README.md#configurazione-keycloak-realm), dove ci sono anche i ruoli e le credenziali degli utenti di prova.

Il frontend si aspetta che il client sia pubblico, con PKCE, e che accetti come indirizzo del frontend `http://localhost:5173` (redirect, redirect dopo il logout e Web origins).

Chi si registra riceve il ruolo `user`. Un account senza `user` né `admin` vede la pagina 403.

### Mapping delle porte

Per questo applicativo, in ambiente Dev, verranno utilizzate le seguenti porte:

| Servizio | Porta | Note |
|---|---|---|
| **gdr-diary-ui** *(frontend)* | `5173` | Server di sviluppo. È l'unica porta autorizzata nel client Keycloak e nel CORS dell'API. |
| Anteprima della build e test e2e | `4173` | `npm run preview` e Playwright |

Le porte di API, Keycloak e dei container sono nel [gdr-diary-api › Mapping delle porte](../gdr-diary-api/README.md#mapping-delle-porte).

### Tema di Keycloak

Le pagine di login e registrazione e le email di Keycloak hanno il tema `gdr-diary`, che sta in questo repository, in `keycloak-theme/`. Il `docker-compose` del backend lo monta nel container di Keycloak.

Il CSS, i font e lo script del tema sono **generati** dagli stessi token del frontend, quindi non si possono disallineare. Per rigenerarli e controllarli vedi [Runbook › Controllare e rigenerare il tema di Keycloak](RUNBOOK.md#controllare-e-rigenerare-il-tema-di-keycloak).

---

## 2. Struttura del Progetto

### gdr-diary-ui *(Frontend)*

Panoramica delle directory principali dell'applicativo:

| Directory | Contenuto |
|---|---|
| `/src/routes` | Le rotte dell'applicativo. `+layout.ts` imposta `ssr = false` e `prerender = false`. |
| `/src/routes/(app)` | Le rotte protette: personaggi, diari, profilo, impostazioni. Il guard in `+layout.ts` fa il login, controlla i ruoli e chiama `POST /me`. |
| `/src/routes/privacy` | La pagina con l'informativa sulla privacy. |
| `/src/lib/auth` | Stato di login (`keycloak-js`) e ruoli. |
| `/src/lib/api` | Client tipizzato verso l'API (`openapi-fetch`), con bearer token e rinnovo automatico, e schema generato. |
| `/src/lib/components` | Componenti Svelte: `ui` (mattoni generici), `landing`, `privacy`, `app`, `characters`, `diaries`, `book`. |
| `/src/lib/characters`, `/diaries`, `/book`, `/profile`, `/settings`, `/list` | Logica di ciascuna area: chiamate all'API, stato, impaginazione del libro. |
| `/src/lib/theme` | Scelta del tema: sistema, chiaro, scuro. |
| `/src/lib/styles` | Token dei due temi, font e stile delle pagine del diario. |
| `/src/lib/env.ts` | Variabili d'ambiente validate. |
| `/keycloak-theme` | Il tema di Keycloak: template, build del CSS ed email. |
| `/openapi` | `swagger.json`, copia del contratto dell'API. |
| `/static` | File statici, tra cui `silent-check-sso.html` per il login silenzioso. |

Gli import interni usano l'alias `#lib/...` con l'estensione `.js`, anche per i file `.ts`.

---

## 3. Avvio in ambiente locale

### Prerequisiti

- **Node.js 22.17 o successivo** e npm
- Il backend e Keycloak accesi: vedi [gdr-diary-api › Avvio in ambiente locale](../gdr-diary-api/README.md#3-avvio-in-ambiente-locale)

### gdr-diary-ui *(Frontend)*

Per avviare l'applicativo **gdr-diary-ui** utilizzare il terminale e posizionarsi nella root della cartella dell'applicativo.

#### 1. Configurazione e dipendenze

Creare il file `.env` a partire dall'esempio e installare le dipendenze presenti in `package.json`:

```bash
cp .env.example .env
npm install
```

#### 2. Applicativo frontend

```bash
npm run dev
```

L'applicazione è raggiungibile su [http://localhost:5173](http://localhost:5173).

Per verificare che tutto sia avviato, fermare o riavviare il servizio vedi [Runbook › Avvio e arresto](RUNBOOK.md#2-avvio-e-arresto).

---

## 4. API Documentation

Il frontend non espone API: usa quelle del backend, descritte nello `Swagger UI` del backend (vedi [gdr-diary-api › API Documentation](../gdr-diary-api/README.md#4-api-documentation)).

Il contratto è la copia `openapi/swagger.json`, da cui si genera il client tipizzato `src/lib/api/schema.d.ts`. Quando l'API cambia va aggiornata: vedi [Runbook › Aggiornare il contratto dell'API](RUNBOOK.md#aggiornare-il-contratto-dellapi).

---

## 5. Integrazione con sistemi esterni

L'applicativo effettua chiamate a servizi esterni, come descritto di seguito. Non ci sono risorse di terze parti nelle pagine: i font sono ospitati nel progetto, senza CDN né analytics.

### Sistemi esterni interrogati

| Sistema | Utilizzo |
|---|---|
| **Keycloak** | Login e registrazione (le fa Keycloak, il frontend non ha pagine proprie), rinnovo del token e controllo silenzioso della sessione. Il cambio email e la cancellazione dell'account passano da azioni di Keycloak. |
| **gdr-diary-api** | Tutti i dati: personaggi, diari, pagine, profilo e impostazioni. |
| **Storage S3** | Avatar e copertine: il browser li legge con gli indirizzi temporanei restituiti dall'API. |

### Sistemi esterni chiamanti

Nessuno: l'applicativo è composto da file statici serviti al browser.

---

## 6. Testing

L'applicativo è già configurato per `Vitest` e `Playwright`.

### gdr-diary-ui Test

Posizionarsi nella root dell'applicativo da un terminale ed eseguire uno dei comandi:

| Comando | Cosa esegue |
|---|---|
| `npm run verify` | Type check, lint, test unitari, controllo del tema Keycloak e build. Da lanciare prima di ogni commit. |
| `npm run check` | Type check (`svelte-check`). |
| `npm run lint` | Prettier (solo controllo) ed ESLint. `npm run lint:fix` formatta e corregge. |
| `npm run test:unit` | Test unitari e dei componenti con Vitest, in modalità watch. Con `-- --run` una sola esecuzione. |
| `npm run test:e2e` | Test end-to-end con Playwright: scarica i browser, costruisce l'applicativo e lo prova sulla porta `4173`. |
| `npm test` | Test unitari ed end-to-end. |

I test stanno accanto al codice:

| File | Cosa prova |
|---|---|
| `*.test.ts` | Logica, in ambiente Node. |
| `*.svelte.spec.ts` | Componenti e pagine, in un browser Chromium. |
| `*.e2e.ts` | Pagine pubbliche sulla build, con Playwright. |

> **Nota**: le pagine dell'area protetta non si raggiungono negli end-to-end, perché serve Keycloak. Si provano con i `*.svelte.spec.ts`, con l'API simulata.

**Risposte**: nel terminale, Vitest riporta un riepilogo simile al seguente:

```
Test Files  N passed (N)
     Tests  M passed (M)
```

In caso di fallimento il riepilogo mostra `failed` e il dettaglio del test che non è passato.

---

## 7. Sicurezza e Compliance

Il progetto adotta i seguenti strumenti per la sicurezza:

- Autenticazione tramite Keycloak OIDC, Authorization Code con PKCE, client pubblico
- Token di accesso e di rinnovo solo in memoria: mai `localStorage`, `sessionStorage` o cookie scritti dal frontend
- Unico cookie scritto dal frontend: `gdr-theme` con la scelta del tema, senza dati personali
- Mai `{@html}` per mostrare contenuti dell'utente (lo vieta ESLint)
- Nessuna risorsa di terze parti, nessun analytics
- Informativa sulla privacy alla pagina `/privacy`
- Autorizzazione dei dati nel backend: il frontend non si fida mai di sé stesso per nascondere dati
- Comunicazioni HTTPS in produzione

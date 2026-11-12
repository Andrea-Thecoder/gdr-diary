# gdr-diary-ui · Runbook

> Procedure operative e risoluzione dei problemi di **gdr-diary-ui** in ambiente di sviluppo locale. La produzione non è ancora definita.

|                                                       |                                                                 |
| ----------------------------------------------------- | --------------------------------------------------------------- |
| **Ultimo aggiornamento**                              | 3 novembre 2026                                                 |
| **Per l'installazione e la descrizione del progetto** | vedi il [README](README.md)                                     |
| **Per Keycloak e il backend**                         | vedi il [Runbook di gdr-diary-api](../gdr-diary-api/RUNBOOK.md) |

**Indice**

- [1. Panoramica operativa](#1-panoramica-operativa)
- [2. Avvio e arresto](#2-avvio-e-arresto)
- [3. Procedure ordinarie](#3-procedure-ordinarie)
- [4. Problemi noti](#4-problemi-noti)

---

## 1. Panoramica operativa

### Componenti

Il frontend gira fuori da Docker (`npm run dev`). Per funzionare servono Keycloak e il backend, che si gestiscono dal [Runbook di gdr-diary-api](../gdr-diary-api/RUNBOOK.md).

| Servizio         | Cosa fa               | Dipende da                      | Controllo di stato                                                                |
| ---------------- | --------------------- | ------------------------------- | --------------------------------------------------------------------------------- |
| **gdr-diary-ui** | Interfaccia web (SPA) | Keycloak, gdr-diary-api         | Aprire la [home](http://localhost:5173)                                           |
| Keycloak         | Login, token, ruoli   | –                               | vedi il [Runbook di gdr-diary-api](../gdr-diary-api/RUNBOOK.md#2-avvio-e-arresto) |
| gdr-diary-api    | API REST              | PostgreSQL, SeaweedFS, Keycloak | vedi il [Runbook di gdr-diary-api](../gdr-diary-api/RUNBOOK.md#2-avvio-e-arresto) |

Le porte sono nel [README › Mapping delle porte](README.md#mapping-delle-porte).

### Dove guardare i log

| Cosa               | Come                                                                                                            |
| ------------------ | --------------------------------------------------------------------------------------------------------------- |
| Server di sviluppo | Console in cui gira `npm run dev`.                                                                              |
| Errori nel browser | Console degli strumenti per sviluppatori (F12). La scheda **Network** mostra le chiamate all'API e le risposte. |
| Keycloak e backend | Vedi il [Runbook di gdr-diary-api › Dove guardare i log](../gdr-diary-api/RUNBOOK.md#dove-guardare-i-log).      |

---

## 2. Avvio e arresto

### Avviare il progetto

**Prerequisiti**: vedi [README › Prerequisiti](README.md#prerequisiti).

1. Avviare Keycloak e il backend seguendo il [Runbook di gdr-diary-api › Avvio e arresto](../gdr-diary-api/RUNBOOK.md#2-avvio-e-arresto).

2. Avviare il frontend con i comandi di [README › Avvio in ambiente locale](README.md#3-avvio-in-ambiente-locale).

3. Controllare la console.

   Atteso: Vite stampa l'indirizzo locale `http://localhost:5173/`.

4. Verificare il frontend. Aprire la [home](http://localhost:5173) e usare il pulsante di accesso.

   Atteso: compare la pagina di login di Keycloak con il tema `gdr-diary`. Dopo il login con un utente di prova (credenziali nel [README del backend](../gdr-diary-api/README.md#configurazione-keycloak-realm)) si arriva alla schermata dei personaggi.

### Fermare il progetto

Fermare il server di sviluppo con `Ctrl+C`.

### Riavviare il progetto

Fermare il server con `Ctrl+C` e rilanciare `npm run dev`. Le variabili d'ambiente si leggono all'avvio: dopo ogni modifica a `.env` serve un riavvio.

---

## 3. Procedure ordinarie

### Aggiornare il contratto dell'API

Da fare quando il backend cambia endpoint o DTO. Con il backend acceso, dalla radice del frontend:

```bash
curl -o openapi/swagger.json http://localhost:8080/api/v1/gdr-diary-api/v3/api-docs
npm run api:generate
npm run check
```

Atteso: `src/lib/api/schema.d.ts` viene rigenerato e `npm run check` termina senza errori. Se ci sono errori di tipo, indicano dove adattare il codice.

Un messaggio d'errore nuovo del backend va tradotto in `src/lib/api/messages.ts`.

### Controllare e rigenerare il tema di Keycloak

Da fare dopo aver cambiato `tokens.css`, un font, `theme.ts` o le classi usate nei template di `keycloak-theme/`.

```bash
npm run theme:check
npm run theme:build
```

`theme:check` fallisce se i file generati nel repository sono diversi da quelli che produrrebbe la build. `theme:build` li rigenera: vanno poi salvati nel repository.

Verifica: ricaricare la pagina di login di Keycloak.

### Costruire e provare la build

```bash
npm run build
npm run preview
```

Atteso: la build termina senza errori e produce `build/`. L'anteprima è su `http://localhost:4173`.

La build è statica e ha il fallback `index.html`: un hosting deve servire `index.html` per ogni percorso.

### Verifica completa prima di un commit

```bash
npm run verify
```

Esegue in ordine: type check, lint, test unitari, controllo del tema Keycloak, build. Il comando si ferma al primo passo che fallisce: per i casi più comuni vedi «Build e test» nei [Problemi noti](#4-problemi-noti).

Atteso: il comando termina senza errori.

### Cambiare le variabili d'ambiente

Modificare `.env` e riavviare il frontend. Significato e valori: [README › Variabili d'ambiente](README.md#variabili-dambiente).

---

## 4. Problemi noti

### Installazione e avvio

| Sintomo                                                     | Causa probabile                                                              | Rimedio                                                                                                      |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `npm install` si ferma con un errore sulla versione di Node | Node precedente alla 22.17 (`engine-strict=true`).                           | Controllare `node --version` e usare Node 22.17 o successivo.                                                |
| Errore all'avvio sulle variabili d'ambiente                 | Il file `.env` manca o ha valori non validi (`src/lib/env.ts` li controlla). | `cp .env.example .env`, controllare i valori e riavviare.                                                    |
| Vite parte su una porta diversa, per esempio `5174`         | La porta `5173` è occupata da un altro processo.                             | Chiudere il processo che usa `5173` e riavviare: Keycloak e il CORS dell'API accettano solo quell'indirizzo. |

### Accesso

| Sintomo                                                                                      | Causa probabile                                                                                                                 | Rimedio                                                                                                                                                      |
| -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Keycloak mostra «Invalid redirect URI»                                                       | Il frontend gira su un indirizzo diverso da `http://localhost:5173`.                                                            | Usare la porta `5173` (vedi sopra).                                                                                                                          |
| Dopo il login si torna alla home con un messaggio in basso a destra, o compare la pagina 403 | L'app ha chiamato `POST /me` e non è andata, oppure l'account non ha né `user` né `admin`: in questi casi disconnette l'utente. | Leggere il messaggio. Per 401, 403 o API che non risponde vedi il [Runbook di gdr-diary-api › Accesso e token](../gdr-diary-api/RUNBOOK.md#accesso-e-token). |
| Chiamate all'API bloccate da CORS nella console del browser                                  | Il frontend non gira su `http://localhost:5173`, l'unica origine ammessa dall'API, oppure il backend è spento.                  | Usare la porta `5173` e controllare il backend: [Runbook di gdr-diary-api › Avvio e arresto](../gdr-diary-api/RUNBOOK.md#2-avvio-e-arresto).                 |
| 401 su tutte le chiamate all'API                                                             | Il token non ha l'audience del backend o è scaduto.                                                                             | Vedi il [Runbook di gdr-diary-api › Accesso e token](../gdr-diary-api/RUNBOOK.md#accesso-e-token).                                                           |

### Build e test

| Sintomo                                                                        | Causa probabile                                           | Rimedio                                                                    |
| ------------------------------------------------------------------------------ | --------------------------------------------------------- | -------------------------------------------------------------------------- |
| `npm run verify` si ferma su `theme:check`                                     | I file generati del tema Keycloak sono vecchi.            | `npm run theme:build` e salvare i file generati.                           |
| `npm run check` dà errori di tipo dopo un cambio nel backend                   | Il contratto dell'API e `schema.d.ts` non sono allineati. | Vedi [Aggiornare il contratto dell'API](#aggiornare-il-contratto-dellapi). |
| I test dei componenti (`*.svelte.spec.ts`) non partono perché manca il browser | Chromium di Playwright non è installato.                  | `npx playwright install chromium`. `npm run test:e2e` li scarica da solo.  |
| `npm run test:e2e` non parte                                                   | La porta `4173` è occupata.                               | Chiudere il processo che la usa.                                           |

### Interfaccia

| Sintomo                               | Causa probabile                     | Rimedio                                   |
| ------------------------------------- | ----------------------------------- | ----------------------------------------- |
| Un errore dell'API compare in inglese | Il messaggio non è ancora tradotto. | Aggiungerlo in `src/lib/api/messages.ts`. |

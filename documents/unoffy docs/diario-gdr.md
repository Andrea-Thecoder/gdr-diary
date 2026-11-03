# Diario GDR

Documento di progetto, versione 0.8, 27 ottobre 2026. Autore: Andrea.

Qui c'è l'idea del progetto e le scelte principali. Per i dettagli: lo stato del backend è in `stato-lavori.md`, quello che resta sul frontend in `da-fare-diario-ui.md`, il design delle schermate in `landing-design.md`.

## Cos'è

Diario GDR è una web app per chi gioca di ruolo. Ogni giocatore crea i suoi personaggi, e per ogni personaggio tiene uno o più diari: veri diari con la copertina, le pagine da sfogliare e il testo scritto a mano libera. Un diario ha al massimo 100 pagine, come uno vero.

Gli strumenti che esistono (World Anvil, Kanka, Obsidian Portal, D&D Beyond, oppure Notion e Obsidian usati in modo creativo) ragionano per campagna o per mondo. Diario GDR parte invece dal personaggio. Quello che conta è il piacere di scrivere e di rileggere, non il numero di funzioni.

Lo sto costruendo anche per imparare a fare un prodotto completo, dal database all'interfaccia, per usarlo davvero con il mio gruppo di gioco e magari, un giorno, per aprirlo ad altri. Lavoro a piccoli passi, nei tempi liberi, senza scadenze.

## Per chi è

Per i giocatori, che tengono il diario del proprio personaggio, e per i master, che tengono i loro appunti. Per ora il master crea un profilo di tipo Master, che ha i suoi diari come gli altri. Più avanti avrà funzioni dedicate, come le campagne e una bacheca per i giocatori.

Giocatore e master non sono due tipi di account. Sono la parte che una persona ha in una campagna, e la stessa persona può giocare in una e fare il master in un'altra. Per questo l'account ha solo i ruoli tecnici `user` e `admin`, mentre il tipo (personaggio o master) si sceglie quando si crea un profilo.

## Le funzioni

### Funzioni di base (la versione minima, MVP)

La versione minima serve a un giocatore per fare questo, dall'inizio alla fine: si registra, crea un personaggio, crea un diario, comincia a scrivere. Per questo ha solo queste funzioni, fatte bene:

1. Registrazione e login.
2. Profili personaggio, con nome, immagine e tipo (personaggio o master).
3. Più diari per ogni personaggio, con la copertina.
4. Pagine del diario con testo, al massimo 100 per diario.
5. Caricamento delle immagini: personaggio, copertina e profilo dell'utente.
6. Uso comodo anche da telefono.

### Funzioni successive

In questo ordine:

Subito dopo la base:

1. Tag sulle pagine.
2. Ricerca.
3. Esportazione del diario: chi scrive per anni vuole poter portare via i propri dati.
4. Link a siti esterni da cui scaricare le schede personaggio editabili. La scheda scaricata si carica poi nell'app e si salva sul personaggio. È più sicuro che ospitare noi le schede.
5. Versione installabile sul telefono (PWA), facoltativa: chi non la installa usa il sito come sempre.

Dopo:

6. Condivisione di un diario con altri utenti, che richiede i permessi.
7. Funzioni per il master: campagne e bacheca con informazioni per i giocatori.

Molto più avanti:

8. Scheda in PDF aperta, compilata e salvata direttamente nell'app. Funziona solo con i PDF a form standard (AcroForm) ed è la funzione tecnicamente più rischiosa.
9. Immagini generate con intelligenza artificiale per gli utenti premium, per esempio una al giorno. Ha un costo per ogni immagine e richiede moderazione.
10. Piano premium, con più spazio e funzioni extra.
11. Schede personaggio native, costruite dentro il sito. Lavoro molto grande.

## A che punto sono

La versione minima è quasi finita. Ho diviso il lavoro in sei tappe: registrarsi e fare login, creare un personaggio, creare un diario, scrivere e salvare pagine, caricare le immagini, e infine provare tutto con il mio gruppo di gioco. Le prime cinque sono fatte.

Oggi l'app permette di:

- registrarsi e accedere con le pagine di Keycloak, che hanno lo stesso aspetto dell'app;
- creare, modificare ed eliminare personaggi e master, con la loro immagine;
- creare diari con copertina colorata o con un'immagine;
- aprire un diario, scrivere su due pagine con un editor di testo, sfogliare, e vedere le pagine nuove nascere da sole quando si riempiono;
- contare su un salvataggio automatico, che tiene una copia sul dispositivo se cade la connessione e avvisa se la stessa pagina è stata modificata altrove;
- gestire il profilo: immagine, impostazioni di scrittura, cambio email, eliminazione dell'account;
- leggere l'informativa sulla privacy;
- usare l'app da telefono e da tablet.

Prima della tappa 6 mancano tre cose. Devo provare tutto insieme con backend e Keycloak accesi, perché finora il frontend è stato provato soprattutto con test automatici. Quando si elimina l'account va cancellato anche l'utente su Keycloak, che oggi resta. Infine devo compilare i dati del titolare nella pagina Privacy e far rivedere il testo.

## Come è costruita

- Backend: Java 25 e Spring Boot 4.1, con Ebean al posto di JPA. Documentazione dell'API con springdoc-openapi, che espone solo DTO e mai le entity.
- Database: PostgreSQL 16 con Flyway per le migrazioni.
- Accesso: Keycloak 26.7. Le pagine di login e di registrazione sono sue, con un tema nostro. Nel database dell'app restano solo l'identificativo utente e l'email, mai le password. Una piccola estensione di Keycloak gestisce il cambio email senza bloccare l'accesso.
- Frontend: SvelteKit 3 e Svelte 5, usato come app a pagina singola senza parte server. Tailwind 4 e DaisyUI 5 per lo stile, Tiptap per l'editor. Lo sfoglio è un'animazione CSS scritta da noi.
- Immagini: archivio compatibile S3 con bucket privato. In locale SeaweedFS, online Cloudflare R2 o S3: cambiano solo indirizzo e chiavi.
- Ambiente locale: Docker Compose con PostgreSQL, Keycloak, SeaweedFS e Mailpit per le email di prova, tutti con la versione fissata.

Il browser parla direttamente con Keycloak e con l'API, a cui manda il token di accesso. Il token resta solo in memoria.

Il client TypeScript del frontend si genera dal file OpenAPI con `npm run api:generate`.

Licenza: entrambi i repository sono sotto PolyForm Noncommercial 1.0.0. Il codice si può leggere e modificare, ma non usare a fini commerciali.

## I dati

Il diagramma delle entity è in `diario-gdr-entities.mermaid`. In breve: un utente ha dei personaggi, un personaggio ha dei diari, un diario ha delle pagine, e le immagini sono documenti collegati a utente, personaggio o diario. Le regole importanti:

- Ogni tabella ha un identificativo UUID, i campi di controllo (chi e quando ha creato e modificato) e una versione per il controllo delle modifiche concorrenti.
- Il testo delle pagine è il JSON di Tiptap, salvato in una colonna `jsonb`. Il backend accetta solo i nodi e gli attributi che l'editor produce davvero e rifiuta il resto. Nelle pagine non si mostra mai HTML grezzo, per evitare problemi di sicurezza.
- Le pagine di un diario sono sempre consecutive, da 1 a 100. Il numero lo assegna il server e il database garantisce il limite.
- Se due schede modificano la stessa pagina, il secondo salvataggio riceve un errore 409 e l'utente sceglie quale versione tenere.
- Le immagini si caricano passando dal backend, che controlla il contenuto vero del file. Si leggono con indirizzi temporanei.
- Cancellare un personaggio cancella anche diari, pagine e immagini; cancellare l'account cancella tutto. Ogni cancellazione è una sola transazione.
- Il tipo del personaggio (personaggio o master) si sceglie alla creazione e non si cambia.
- Ogni richiesta controlla che i dati appartengano all'utente che la fa.
- L'email è una copia di quella di Keycloak e si allinea a ogni accesso.

## Rischi e attenzioni

- Perdita di dati: chi scrive per anni deve poter esportare il diario e non perderlo. Servono backup e un'esportazione vera.
- Caricamenti: costi di spazio, moderazione dei contenuti e diritti sulle immagini.
- PDF editabile: funziona solo con i PDF a form standard, non con scansioni o vecchi moduli XFA.
- Scrivere offline: oggi c'è solo la copia di sicurezza sul dispositivo, non la sincronizzazione completa, che è complessa per via dei conflitti.
- Esperienza di scrittura: è il punto di forza dell'app, non deve diventare un "Notion peggiore".
- Troppe funzioni: la versione minima resta piccola e il resto va nella lista delle funzioni successive.

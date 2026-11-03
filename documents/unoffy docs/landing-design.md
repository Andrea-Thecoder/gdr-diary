# Design FE: landing e area dopo il login

Versione 0.8, 27 ottobre 2026.

Design delle schermate di Diario GDR (SvelteKit e Tailwind, solo frontend): la home pubblica (sezioni 1-9) e le schermate dopo il login: personaggi (sezione 10), profilo (11), impostazioni (12), diari del personaggio (13), diario aperto per scrivere (14) e layout per tablet (15).

Questo documento descrive le tele di progetto e le decisioni di design. Le misure e i valori effettivamente usati nel codice stanno nel file `DESIGN.md` del repository del frontend, che è il riferimento quando i due documenti differiscono: lì, per esempio, il tema scuro usa un nero quasi puro con accento `#3C0366` al posto del viola della tela. Le tele sono private, le apre solo chi ha accesso:

- Landing: https://claude.ai/artifact/DrLNjTEQebYHCE2k5DeGKG
- Personaggi, profilo, impostazioni, diari, diario aperto e tablet: https://claude.ai/artifact/Syq1FERNPgBzaktPvxe77M

## 1. Direzione

Un diario di pergamena con anima fantasy medievale, in due modalità dello stesso design:

- Chiara, "pergamena": carta calda, inchiostro scuro, rosso ceralacca come accento, verde cuoio per la fascia scura.
- Scura, "fantasy viola": cielo notturno viola, stelle, bagliori, oro come secondo colore. Le pagine del diario restano color pergamena anche qui, per la leggibilità e perché sono il prodotto.

Struttura, testi e componenti sono identici nelle due modalità: cambiano la tavolozza e gli effetti (stelle e bagliori solo nella scura).

La home promette solo ciò che l'MVP ha già: personaggi, diari, pagine (al massimo 100), immagini, uso da telefono. Niente tag, ricerca, esportazione, condivisione, premium, PDF.

## 2. Sezioni della landing, in ordine

1. Header: logo (penna e "Diario GDR"), link "Come funziona", "La scrittura", "Per chi è", pulsante contornato "Accedi". Su schermo stretto va a capo, sotto i 900 px diventa un menu a burger.
2. Hero: etichetta "Il diario di ogni avventuriero", titolo "Ogni eroe merita il suo diario." (la parola diario in accento), sottotitolo, pulsante "Apri il tuo diario" e link "Scopri come funziona". A destra un diario aperto su due pagine scritte a mano, con segnalibro, sigillo di cera e un cerchio arcano sottile dietro.
3. Come funziona: titolo "Tre passi dalla taverna alla prima pagina." Tre card con numeri romani: "Forgia il personaggio" (nome, volto, campagna), "Rilega un diario" (titolo, colore della copertina, quanti vuoi), "Scrivi, pagina dopo pagina" (100 pagine, in coda, sempre in ordine). Ogni card ha una miniatura con i campi veri delle entity.
4. La scrittura: titolo "Scrivi come uno scriba, rileggi come un libro antico." Capolettera miniata e tre punti: "Due pagine alla volta", "Testo ricco, nessuna distrazione" (titoli, grassetto, corsivo, elenchi), "Solo tuo". A destra la barra strumenti, una pagina in scrittura con cursore e i comandi "Pagine 7 e 8 di 100".
5. Per chi è (fascia scura): titolo "Per l'avventuriero e per il master." Tre card: diario del personaggio, appunti del master (un profilo di tipo Master), anche dal telefono.
6. Chiusura: sigillo grande, "Il tuo eroe ha già una storia da scrivere.", un solo pulsante "Accedi e inizia a scrivere".
7. Footer: logo, link "Privacy" (porta alla pagina `/privacy`, che oggi è l'informativa vera) e "Accedi".

C'è un solo tipo di azione primaria, ripetuta: l'accesso.

### 2.1 Collegamento con il resto dell'app

- I pulsanti "Accedi" e "Registrati" non sono link interni: passano da Keycloak (`auth.login()` e `auth.register()`) e dopo l'accesso si torna a `/characters`. Nella tela puntano a un'àncora perché lì non si può navigare. "Registrati" è stato aggiunto perché il percorso dell'MVP parte dalla registrazione.
- Dopo il login il frontend chiama `POST /me`, poi mostra la schermata dei personaggi. Chi è già dentro e apre la home viene portato ai personaggi.
- Il testo di esempio (Lyra, la torre di Valkar, Brann, "Cronache del Nord") è inventato e non viene dal backend.

## 3. Tipografia

- Titoli, logo e numeri grandi: Grenze Gotisch, peso 700.
- Etichette, menu e pulsanti (maiuscoletto con spaziatura): Cinzel, pesi 500 e 600.
- Testo: EB Garamond, 20 px di base, pesi 400 e 500, corsivo 400.
- Scrittura a mano nelle pagine del diario: Caveat, peso 500.

I font di riserva sono Georgia e serif. I titoli lunghi usano `clamp()`: h1 da 48 a 92 px, h2 da 38 a 62 px. I font sono ospitati nel progetto e non caricati da Google Fonts, per non mandare l'indirizzo IP dei visitatori a terzi (GDPR).

## 4. Palette

I colori sono variabili (Tailwind esteso, DaisyUI con due temi), mai scritti a mano nei componenti. Valori della tela, chiaro e poi scuro:

- Sfondo pagina: `#F1E6CC`, `#120A22`.
- Testo e titoli: `#2A1F16`, `#F4EAFF`.
- Testo secondario: `#5A4A3A`, `#CDBDF2`.
- Etichette: `#6F5220`, `#E2BE6A`.
- Titoli delle card: `#2A1F16`, `#F8E3A0`.
- Accento (pulsanti, sigillo, segnalibro): `#8B2B22`, `#7C3AED`.
- Ornamenti (rombi, linee): come l'accento, `#E2BE6A`.
- Card: `#FAF3E1` con bordo `#D9C89C`; in scuro sfumatura da `#27134F` a `#1A0E34` con bordo oro al 55% e cornice interna.
- Pagina del diario: `#FAF3E1`, `#F2E6C9`.
- Inchiostro della pagina: `#2A1F16`, `#2B1B3F`.
- Fascia "La scrittura": `#E4D3AE`, sfumatura da `#3F1E86` a `#1B0E3C`.
- Fascia "Per chi è": `#26382F` con testo `#F6ECD3`, `#0E071C` con testo `#F4EAFF`.
- Copertina del diario: `#2F4B43` con angoli `#B8923A`, sfumatura viola con angoli `#E2BE6A`.

L'accento delle due modalità si sceglie dalle impostazioni della pagina, quindi si cambia in un punto solo. Il pulsante primario scuro è una sfumatura dall'accento a `#4C1D95` con bordo oro. I testi sono pensati per un contrasto di almeno 4,5 a 1 (3 a 1 oltre i 24 px).

## 5. Componenti ricorrenti

- Pulsante primario: Cinzel 15 px maiuscolo, spaziatura 0,12 em, altezza minima 54 px, raggio 4 px, bordo e ombra.
- Pulsante contornato (per esempio "Accedi" nell'header): altezza minima 44 px.
- Card con doppia cornice: bordo di 1 px più una cornice interna; nel tema chiaro la cornice interna è trasparente.
- Libro aperto: copertina, due pagine con ombra interna al centro, angoli decorati, segnalibro con ritaglio a V, sigillo di cera in basso a sinistra.
- Cerchio arcano: disegno decorativo dietro al libro, tre cerchi e otto tacche, semitrasparente.
- Capolettera miniata: prima lettera del paragrafo in Grenze Gotisch 68 px, in accento o in oro.
- Le illustrazioni (libro, miniature, editor) hanno `role="img"` e una descrizione: sono decorative, non controlli veri.

## 6. Responsive

- Il layout usa flex con `flex-wrap`, `clamp()` e `min()`: le colonne vanno a capo da sole sotto i 420 px circa.
- Contenitore centrale da 1200 px con margine laterale `clamp(16px, 4vw, 48px)`.
- Bersagli di tocco di almeno 44 px.
- Versione mobile verificata a 390 px. Regole e misure complete nel `DESIGN.md` del frontend.

## 7. Come si è tradotto in SvelteKit e Tailwind

- Un componente di pagina per la home, con sezioni come componenti piccoli (`SiteHeader`, `Hero`, `BookSpread`, `StepCards`, `WritingSection`, `AudienceSection`, `FinalCta`, `SiteFooter`).
- Tema chiaro e scuro con i token come variabili: parte dalla preferenza di sistema e si può cambiare dalle impostazioni.
- Gli effetti del tema scuro (stelle, bagliori, cielo) sono sfondi a gradiente CSS, senza immagini. Libro e miniature sono HTML e CSS, non immagini: restano nitidi e seguono il tema.

## 8. Da decidere

- Un accento solo per le due modalità (viola anche in chiaro) oppure tenere rosso e viola come ora.
- Logo vero al posto della penna generica, e sigillo come favicon (oggi c'è ancora la favicon predefinita di SvelteKit).
- Se aggiungere la sezione "in arrivo" (tag, ricerca, esportazione, condivisione): oggi è fuori di proposito.
- Il testo della pagina Privacy è deciso (vedi `claude/privacy-informativa.md`), la rotta dopo il login è `/characters`.

## 9. Stato

La landing è fatta e approvata, in una tela a quattro tavole (chiaro e scuro, desktop e mobile). Tutte le schermate sono state costruite nel frontend: personaggi, diari, diario aperto, profilo, impostazioni, pagina d'errore, pagina Privacy e pagine di Keycloak con lo stesso design. Il riferimento aggiornato è il `DESIGN.md` del frontend; questo documento resta come memoria delle scelte di design e delle tele.

## 10. Personaggi (dopo il login)

Stessa grafica della landing, stessi font e token.

### 10.1 Dati dal backend

- `GET /characters`: filtri `name`, `campaignName`, `page`, `size`, `sort`, `descending`. Ogni elemento ha `id`, `name`, `kind`, `campaignName`, `backgroundColor`, `avatarUrl`. La lista non restituisce descrizione né numero di diari.
- `kind` vale `PLAYER` (un personaggio interpretato) oppure `GAME_MASTER` (un profilo da Game Master). Si sceglie alla creazione e non si cambia.
- `POST /characters`: `kind` e `name` obbligatori; `description`, `campaignName` e `backgroundColor` (formato `#RRGGBB`) facoltativi.
- `PUT` e `DELETE /characters/{id}/avatar`: immagine in png, jpeg o webp.

### 10.2 Struttura della pagina

1. Header con logo e menu dell'account.
2. Titolo ("Chi scrive oggi?") e sottotitolo, con il pulsante primario "Nuovo personaggio" a destra.
3. Barra degli strumenti: cerca per nome, filtro per campagna, ordinamento, conteggio.
4. Griglia di card, più una cella tratteggiata "Nuovo personaggio" in fondo.
5. Primo accesso, senza personaggi: titolo "Il tuo primo eroe ti aspetta." e una sola cella grande "Forgia il tuo primo eroe".
6. Finestra "Nuovo personaggio" in due passi.

### 10.3 La card del personaggio

- Foto alta 240 px. Con `avatarUrl` è l'immagine a tutta larghezza; senza, una silhouette in penombra con sfumatura dal `backgroundColor` al quasi nero. La silhouette (donna, uomo, o figura incappucciata per il master) dipende dall'`id`, quindi non cambia ricaricando.
- Penna di modifica tonda da 44 px in alto a destra, nome in Grenze Gotisch, campagna con bandierina oppure "Senza campagna" in corsivo, pulsante primario "Apri i diari" a tutta larghezza.
- Al passaggio del mouse la card sale di 8 px. Le card di una riga hanno la stessa altezza.

### 10.4 Griglia

Contenitore da 1200 px, griglia `auto-fill` con colonne da almeno 280 px e spazio di 36 px: una colonna sotto i 596 px, due fino a 1007 px, tre da 1008 px. Nello stato vuoto c'è una sola colonna da 420 px centrata. Sotto i 560 px la finestra diventa un foglio ancorato in basso.

### 10.5 Finestra "Nuovo personaggio"

Il tipo (personaggio o master) non si cambia dopo la creazione, quindi si sceglie da solo, con l'avviso che è definitivo.

- Passo 1, "Che cosa crei?": due card di scelta singola, "Personaggio" (preselezionato) e "Master", l'avviso "La scelta non si cambia dopo: se sbagli, ne crei un altro.", pulsanti "Annulla" e "Continua".
- Passo 2: titolo "Nuovo personaggio" oppure "Nuovo Master", una riga con il tipo scelto e il link "Cambia", anteprima tonda dell'avatar con "Carica un'immagine", poi Nome (obbligatorio), Campagna, Descrizione e cinque colori più un campo esadecimale. Pulsanti "Indietro" e "Forgia il personaggio" oppure "Crea il Master".
- Alla conferma partono `POST /characters` e, se c'è un'immagine, `PUT /characters/{id}/avatar`. Tra i due passi non c'è nessuna chiamata.

### 10.6 Token in più rispetto alla landing

Ombre delle card (normale e al passaggio del mouse), cella "Nuovo", campi, sfondo e velo della finestra, card di scelta selezionata: sono nel `tokens.css` del frontend e documentati in `DESIGN.md`.

### 10.7 Cosa è stato deciso dopo la tela

- La silhouette dipende dall'`id`.
- Il filtro per campagna è un campo libero. I filtri si applicano con "Applica filtri", l'ordinamento subito.
- La lista si carica 12 alla volta con "Mostra altri personaggi".
- La penna apre la finestra di modifica con gli stessi campi della creazione, senza la scelta del tipo.
- L'eliminazione si fa con il cestino e un popup di conferma; il backend cancella a cascata diari, pagine e immagini.
- Le aree "I miei personaggi" e "Le mie campagne" sono due schede; la seconda mostra per ora "Coming Soon".
- La corona dorata sulla foto segna il master.

Restano aperti: un filtro per tipo (serve il campo `kind` nel filtro dell'API) e se un utente può avere più profili Master o uno solo.

## 11. Profilo

I campi mostrati nella tela erano di esempio. Nel codice ci sono solo i campi veri, e per aggiungerne uno basta una nuova riga con il template del campo (11.3).

### 11.1 Dati dal backend

- `GET /me`: email, piano e impostazioni; risponde 404 se l'utente non esiste ancora.
- `POST /me`: da chiamare dopo ogni login, crea l'utente la prima volta e restituisce il suo `id`. È ripetibile.
- `PUT` e `DELETE /me/avatar`: immagine del profilo.
- `DELETE /me`: cancella tutti i dati dell'utente nel backend.
- Il cambio email lo fa Keycloak (nessun endpoint nostro). Non esistono endpoint per esportare i dati.

### 11.2 Struttura

1. Header dell'app. Profilo e Impostazioni si raggiungono dal menu dell'account in alto a destra, non ci sono voci a pillola.
2. Titolo "Il tuo profilo".
3. Colonna sinistra (280-340 px): riepilogo con avatar da 104 px (penna di modifica), nome utente, badge del piano, frase e pulsante "Esci da qui". Sotto i 900 px passa sopra le altre card.
4. Colonna destra: la sezione "Il tuo account" con Email (e il pulsante "Cambia email"), Piano e Identificativo in sola lettura; poi la sezione "Elimina l'account" con il pulsante rosso e il popup di conferma.

### 11.3 Il template del campo

Un solo componente, ripetuto. Ogni riga ha due zone: a sinistra l'etichetta in maiuscoletto con il testo di aiuto, a destra il controllo. Su schermo stretto le zone si impilano. Tipi di controllo: testo, sola lettura (sfondo diverso, bordo tratteggiato, lucchetto), scelta, interruttore, scelta segmentata, cursore, pulsante (contornato o rosso per le azioni distruttive). Le voci si descrivono con dati (etichetta, aiuto, tipo, valore): aggiungere un campo non richiede nuovo markup.

## 12. Impostazioni

### 12.1 Dati dal backend

`PATCH /me/settings` con un oggetto JSON: ogni chiave sostituisce il valore salvato, una chiave a `null` si toglie, le chiavi non inviate restano. Risponde con le impostazioni dopo l'aggiornamento. Errori: 400 (corpo mancante o troppo grande) e 404 (utente non creato). Un diario può avere un suo font e una sua dimensione; se mancano valgono quelli dell'utente.

### 12.2 Struttura

1. Titolo "Impostazioni" e sottotitolo.
2. Colonna sinistra: card "In questa pagina" con link di ancoraggio alle sezioni (sotto i 900 px diventa una riga di pillole).
3. Colonna destra: la sezione Scrittura (font dei diari, dimensione del testo da 12 a 24 pt, interlinea compatta, normale o ampia, salvataggio automatico) e la sezione Aspetto (tema, effetto sfoglio, animazioni ridotte).
4. Barra di salvataggio in fondo, che compare quando qualcosa cambia: "Hai modifiche non salvate. Si inviano solo le voci cambiate." con "Annulla" e "Salva le impostazioni". Il tema fa eccezione: si applica e si salva subito.

Il gruppo "Promemoria" della tela non esiste: l'app non manda nulla.

### 12.3 Ancora da decidere

- Un controllo, lato backend, che ammetta solo le chiavi note (vedi `claude/stato-lavori.md`).
- Un avviso quando si lascia la pagina con modifiche non salvate.

## 13. Diari del personaggio

Si arriva da "Apri i diari" nella card del personaggio.

Scelta: card, non scaffale. L'immagine della card è il diario chiuso. La vista a scaffale (libri in piedi, con il dorso) è scartata per ora e potrebbe tornare come seconda vista facoltativa. Motivi: con la card si vede la copertina caricata, la lista non ha né pagine né descrizione da mettere sul dorso, i titoli lunghi stanno meglio su due righe, ed è coerente con le altre schermate.

### 13.1 Dati dal backend

- `GET /characters/{characterId}/diaries`: filtri `title`, `page`, `size` (massimo 100), `sort`, `descending`. Elemento: `id`, `characterId`, `title`, `coverColor`, `coverUrl`.
- `POST /characters/{characterId}/diaries`: `title` obbligatorio; facoltativi `description` (max 2000), `coverColor`, `font`, `fontSize`. Se mancano font e dimensione valgono quelli delle impostazioni utente.
- `PUT` e `DELETE /diaries/{id}/cover`: copertina in png, jpeg o webp. `coverUrl` è un indirizzo temporaneo che vale pochi minuti.
- `GET /diaries/{id}` aggiunge `description`, `font`, `fontSize`. `DELETE /diaries/{id}` cancella il diario con tutte le pagine e la copertina.

### 13.2 Struttura della pagina

1. Header dell'app e link "Tutti i personaggi".
2. Contesto del personaggio: avatar tondo da 112 px, etichetta "I diari di", nome, campagna, pulsante primario "Nuovo diario".
3. Barra degli strumenti: cerca per titolo, ordinamento, conteggio. Niente filtro per campagna.
4. Griglia di card più la cella tratteggiata "Nuovo diario".
5. Primo diario, senza diari: sottotitolo "Alira non ha ancora nessun diario. Rilegane uno e comincia dalla prima pagina." e una sola cella grande "Rilega il primo diario".
6. Finestra "Nuovo diario".

### 13.3 La card del diario

- Stessa griglia delle card dei personaggi. Zona immagine alta 330 px con il diario chiuso al centro (184 per 262 px, proporzione 2 a 3).
- Il diario chiuso è fatto di strati CSS: blocco di pagine color crema dietro, copertina con sfumatura dal colore scelto, dorso scuro a sinistra con cuciture tratteggiate, riflesso in diagonale. Al passaggio del mouse si alza di 4 px e ruota leggermente.
- Senza copertina caricata: cornice oro, tre rombi in basso e il titolo in rilievo, al massimo quattro righe; il corpo del titolo scende con la lunghezza (25 px fino a 22 caratteri, 19 px fino a 40, 14 px oltre). Con copertina: l'immagine riempie la copertina.
- Penna di modifica da 44 px in alto a destra, titolo in Grenze Gotisch su al massimo due righe, pulsante "Apri il diario" a tutta larghezza.

### 13.4 Finestra "Nuovo diario"

Un solo passo. Anteprima del diario che segue titolo e colore, "Carica una copertina" (se la carichi, prende il posto del colore), Titolo (obbligatorio), Descrizione, Colore della copertina (cinque cerchi più campo esadecimale), Font e Dimensione (il valore predefinito è "Come le impostazioni", cioè non si manda nulla). Pulsanti "Annulla" e "Rilega il diario". Alla conferma parte `POST /characters/{characterId}/diaries` e, se c'è un'immagine, `PUT /diaries/{id}/cover`. Se la seconda chiamata fallisce il diario esiste già: si mostra l'errore e si propone di riprovare solo la copertina.

### 13.5 Da decidere

- La vista a scaffale come seconda vista.
- Mostrare il numero di pagine ("pag. 7 / 100"): serve un campo in più nel backend.
- Il nome del personaggio nel titolo quando è molto lungo (oggi va a capo).

La penna apre la finestra di modifica con sostituzione ed eliminazione della copertina, e il cestino elimina il diario con conferma: entrambe fatte.

## 14. Il diario aperto

Si arriva da "Apri il diario" nella card.

Scelte: entrambe le pagine sono sempre modificabili (niente pulsante "Modifica"). Barra di formattazione fissa sopra il libro. Si sfoglia con gli angoli piegati delle pagine, senza frecce, e le pagine si aggiungono da sole quando si riempiono, senza pulsante "+". Formattazione: titoli, grassetto, corsivo, sottolineato, elenco puntato e numerato, citazione, allineamento, linea di separazione, annulla e ripeti. Niente immagini dentro le pagine per ora.

### 14.1 Dati dal backend

- `GET /diaries/{diaryId}/pages?page=N`: due pagine per vista, quindi la vista N contiene le pagine 2N-1 e 2N (la pagina a sinistra è sempre dispari). La risposta è paginata e contiene il totale delle pagine (`totalRows`), che serve per scrivere "Pagine 7 e 8 di 23".
- Ogni pagina ha `id`, `pageNumber`, `content` e `version`. `content` è il JSON di Tiptap e si passa all'editor così com'è.
- `POST /diaries/{diaryId}/pages`: crea la pagina in coda, il numero lo assegna il server, al massimo 100; il contenuto non può essere vuoto né troppo grande. Non si inseriscono pagine nel mezzo.
- `PUT /pages/{id}`: salva il contenuto e richiede la `version`; risponde 409 se nel frattempo è cambiata. `DELETE /pages/{id}`: elimina e rinumera le successive.
- Font e dimensione vengono da `GET /diaries/{id}`; se mancano valgono quelli delle impostazioni utente.

### 14.2 Struttura della pagina

1. Header compatto, senza altri elementi, per dare spazio al libro.
2. Link "I diari di" seguito dal nome del personaggio.
3. Riga di riepilogo: miniatura della copertina, titolo del diario, personaggio e campagna; a destra il contatore ("Pagine 7 e 8 di 23", su telefono "Pagina 7 di 23") e lo stato di salvataggio.
4. Barra di formattazione in una pillola con bordo e ombra. Pulsanti da 44 px, separatori tra i gruppi, stato attivo con `aria-pressed`, annulla e ripeti spenti quando non c'è nulla da annullare.
5. Il libro, su un piano più scuro: copertina del diario con dentro le due pagine, ombra della rilegatura al centro e angoli piegati per sfogliare.
6. Sotto il libro: barra "23 pagine scritte" su 100, un campo per andare a una pagina, la frase "Quando la pagina è piena, il testo continua da solo sulla successiva." e il suggerimento "Alt + frecce" per sfogliare mentre si scrive.

### 14.3 Il libro

- Pagina in carta, altezza minima 648 px (18 righe), margini di 36 px sopra e 48 px ai lati. Righe sottili ogni 36 px e testo con interlinea di 36 px, così il testo sta sempre sulle righe. Il testo occupa esattamente 15 righe (540 px). Il numero di pagina sta in basso al centro per lasciare liberi gli angoli.
- Testo nel font del diario. Titolo di pagina in Grenze Gotisch 36 px, secondo titolo 30 px. Elenchi con rientro di 36 px, citazione con filo verticale e corsivo.
- La pagina attiva ha un leggero alone interno. Lo spessore del libro mostra la pila delle pagine scritte a sinistra e di quelle che restano a destra. Il segnalibro è sul margine esterno della pagina destra e sparisce a diario vuoto e all'ultima pagina.

### 14.4 Sfogliare: gli angoli piegati

- In basso a sinistra della pagina sinistra e in basso a destra della pagina destra c'è un angolo piegato. A riposo misura 44 px, al passaggio del mouse o con il fuoco da tastiera si apre a 64 px. L'area cliccabile è un pulsante da 72 px con descrizione "Pagine precedenti" e "Pagine successive".
- Non c'è l'angolo sinistro sulla prima vista né quello destro sull'ultima. Altri comandi: Alt con le frecce (le frecce semplici servono al cursore) e il campo "Vai alla pagina".
- Animazione: il foglio di destra si solleva e ruota attorno alla rilegatura per circa 500 ms. Con `prefers-reduced-motion` o con l'impostazione "Animazioni ridotte" la vista cambia subito.
- Non si usa una libreria di animazione (StPageFlip): con due editor veri nelle pagine la libreria e il cursore si disturbano. È un'animazione CSS di sola presentazione: durante il giro le pagine sono di sola lettura.
- Prima di cambiare vista si salva la pagina in modifica; se il salvataggio fallisce non si sfoglia e compare l'errore.

### 14.5 Salvataggio

- Automatico dopo una pausa nella scrittura (oggi 3 secondi, al più 20 dalla prima modifica) e prima di sfogliare, chiudere o uscire. Il funzionamento completo, con bozza locale, nuovi tentativi e conflitti, è in `claude/autosave-diario.md`.
- Stato accanto al contatore: "Salvato", "Salvataggio" con un cerchio che gira, "Non salvato".
- La pagina nasce con `POST` solo quando c'è del testo, perché il backend rifiuta il contenuto vuoto: non esistono pagine vuote create per sbaglio. Le scritture successive sono `PUT`.
- Pagine che nascono da sole: quando il testo supera le 15 righe, quello che deborda passa in cima alla pagina successiva e il cursore lo segue. La nuova pagina si crea con `POST`, compare un avviso breve ("Pagina 24 aggiunta") e, se si era sull'ultima pagina della vista, la vista cambia da sola. Il taglio avviene per parole intere.
- Alla pagina 100 il testo che deborda non si può più inserire e compare l'avviso di diario pieno. Il testo non si perde mai: resta nell'editor e il salvataggio riprova da solo.

### 14.6 Stati

- Normale: due pagine scritte, cursore su una delle due.
- Diario vuoto: pagina 1 a sinistra con il suggerimento "Comincia da qui. La pagina nasce quando scrivi la prima riga."; a destra "La pagina 2 arriva dopo la prima."; nessun angolo; contatore "Nessuna pagina ancora".
- Ultima pagina: se il totale è dispari a destra c'è un foglio bianco a righe ("Continua da qui: la pagina 24 nasce quando scrivi la prima riga"), si può cliccare e scrivere subito. Nessun angolo successivo.
- Pagina piena: il testo che deborda passa sulla pagina dopo con il cursore, avviso "Pagina 24 aggiunta".
- Diario pieno: pagine 99 e 100, nessun foglio di aggiunta, avviso "Il diario è pieno: 100 pagine su 100. Per scrivere ancora, rilegane uno nuovo" con il link "Nuovo diario".
- Errore di salvataggio: stato "Non salvato" e avviso "La pagina 8 non è stata salvata. Il testo è ancora qui sotto: non lo perdi." con il pulsante "Riprova ora".
- Conflitto, offline e bozza ripristinata: descritti in `claude/autosave-diario.md`.

### 14.7 Responsive

- Sotto i 760 px il libro mostra una sola pagina (la sinistra della coppia) a tutta larghezza; la seconda pagina e il segnalibro sono nascosti. In basso a sinistra c'è l'angolo "precedente" e in basso a destra quello "successivo". La vista del backend resta a coppie, ma si mostra una pagina alla volta (serve un indice di pagina singola nel frontend, non nell'API).
- La stessa pagina deve essere impaginata allo stesso modo su ogni schermo, altrimenti il testo si dividerebbe in modo diverso su telefono e su computer. Per questo la pagina ha una dimensione logica fissa (474 px per 648, 15 righe) e su schermo piccolo si riduce con una scala. Il testo risulta più piccolo (circa 18 px su telefono) ma l'impaginazione è identica.
- La barra di formattazione sotto i 760 px passa in basso, fissa sopra la tastiera, e scorre in orizzontale se non sta.

### 14.8 Come si è tradotto in SvelteKit

- La vista si legge con `GET .../pages?page=N` e sta nell'indirizzo (`?vista=4`), quindi i tasti indietro e avanti del browser funzionano e la vista si può salvare nei preferiti.
- Ogni pagina avvolge un'istanza di Tiptap con le estensioni ammesse dal backend (`StarterKit` con alcune parti spente, `TextAlign`, il mark `title`). Le due istanze sono indipendenti; la barra agisce sull'editor che ha il fuoco.
- L'impaginazione automatica misura l'altezza del contenuto dopo ogni modifica e sposta in avanti quello che eccede, con una transazione Tiptap per non rompere l'annulla. Non riporta il testo indietro quando si cancella.
- Annulla e ripeti valgono dentro la pagina, non tra pagine.

### 14.9 Da decidere

- Impaginazione automatica: spezzare a metà di un paragrafo o spostarlo intero, e cosa fare con un elenco o un titolo ultimo di pagina.
- Cancellare una pagina in mezzo: il backend rinumera, quindi la conferma deve dire che le pagine dopo scalano di uno (la conferma esiste già).
- Barra di formattazione fluttuante vicino alla selezione, oltre a quella fissa.
- Modifica di titolo e copertina dalla schermata del diario aperto.
- Landing: la miniatura "Pagine 6 e 7 di 100" non rispetta la coppia dispari e pari del backend, da correggere in "Pagine 7 e 8" (già corretto nel testo della sezione 2).

## 15. Layout per tablet

Il tablet non è una terza interfaccia: usa lo stesso design con meno colonne. Su un tablet si tocca e non c'è il mouse, quindi niente effetti che richiedano il passaggio del mouse come unica via, e bersagli di tocco di almeno 44 px.

### 15.1 Fasce di larghezza

- Telefono, fino a 560 px: una colonna, finestre come foglio ancorato in basso.
- Telefono grande, da 561 a 760 px: come il telefono, libro più grande.
- Tablet verticale, da 761 a 900 px: due colonne di card, libro a due pagine in scala.
- Tablet orizzontale, da 901 a 1109 px: fino a tre colonne, libro a due pagine in scala maggiore.
- Computer, da 1110 px: come nelle sezioni precedenti.

### 15.2 Schermate

- Griglie di card: una sola regola per tutte le fasce (colonne da almeno 280 px, spazio 36 px, contenitore da 1200 px), che dà una colonna sotto i 596 px, due da 596 a 1007 px, tre da 1008 px.
- Profilo e impostazioni: sotto i 900 px la colonna sinistra passa sopra le card a tutta larghezza. Il riepilogo del profilo da 641 a 900 px è una riga (avatar, dati, pulsante); l'indice delle impostazioni diventa una riga di pillole da 44 px.
- Finestre: dalla fascia "telefono grande" in su sono una scheda centrata larga al massimo 600 px, con scorrimento interno.
- Diario aperto: due pagine anche in verticale, ridotte con una scala (a 768 px il testo risulta di circa 18 px). La cornice del libro ha una larghezza logica di 980 px in ogni fascia, e le misure degli angoli (44 e 64 px) restano in pixel logici: con scala 0,68 l'area cliccabile resta di circa 49 px. Su tablet l'angolo non si apre al passaggio del mouse: si gira subito la pagina al tocco. La barra di formattazione sta in alto su una riga fino a 760 px.
- Landing: a 768 px intestazione su una riga, titolo e libro impilati, "Come funziona" con due card più una a tutta larghezza; a 1024 px titolo a sinistra e libro a destra, tre colonne.

### 15.3 Scala del libro

Invece di una tabella di passi si misura la larghezza disponibile e si imposta una scala continua uguale al minimo tra 1 e larghezza diviso 980 (a una pagina, diviso 506), da applicare con `transform: scale()` compensando l'altezza.

### 15.4 Da decidere

- Un'impostazione "Una pagina alla volta" per chi trova il testo troppo piccolo su tablet verticale.
- Scala continua o a passi: la continua è più pulita ma si prova con più attenzione.
- Telefoni in orizzontale (da 561 a 760 px): oggi una pagina da 445 px; due pagine a scala 0,55 non sono consigliate.
- Barra di formattazione fissa in alto o fluttuante quando la tastiera è aperta (iPadOS e Android la gestiscono in modo diverso).
- Schermi divisi e dispositivi ripiegabili: finiscono nella fascia telefono, nessun lavoro specifico.

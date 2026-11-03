# Autosave delle pagine del diario (frontend)

Aggiornato il 27 ottobre 2026.

Stato: fatto nel frontend (`gdr-diary-ui`, cartella `src/lib/book`) e provato con test automatici. Non ancora provato a mano contro il backend vero.

## Come funziona

- Ogni modifica segna la pagina come "da salvare" e riarma un timer. Il salvataggio parte dopo 3 secondi di pausa nella scrittura, ma al più tardi dopo 20 secondi dalla prima modifica non salvata (`SAVE_DELAY` e `SAVE_MAX_WAIT`). Parte un solo salvataggio alla volta, in coda.
- Prima di salvare si confronta il testo con l'ultimo salvato: se è uguale, la chiamata non parte.
- Salvataggi forzati: tasto "Salva" e Ctrl+S, cambio pagina, uscita dalla schermata, scheda che passa in secondo piano.
- Errori: nuovo tentativo dopo 5, 10, 20 e poi 30 secondi (`RETRY_DELAY`, `RETRY_MAX_DELAY`). Un `TypeError` di `fetch` conta come "offline": stato "Offline" e nuovo tentativo subito quando torna la rete (evento `online`).
- Bozza locale: ogni modifica si copia anche in IndexedDB (database `gdr-diary-drafts`, chiave `diaryId:pagina`, con 500 ms di attesa). Alla riapertura del diario le bozze non salvate tornano nell'editor, con un avviso. Si cancellano al salvataggio riuscito e al logout.
- Lock ottimistico: `PUT /pages/{id}` manda la `version` dell'ultima risposta. Ogni bozza porta la versione del server su cui è stata scritta (`baseVersion`), quindi una bozza vecchia ripristinata dopo una modifica fatta altrove dà 409 e non sovrascrive in silenzio.
- Conflitto (409): il salvataggio va in pausa, senza tentativi automatici e senza cambio pagina, e compare lo stato "Conflitto". L'utente sceglie tra "Usa la versione salvata" (il testo locale e la bozza si scartano e l'editor mostra quella del server) e "Tieni la mia versione" (si rilegge la versione del server e si salva il testo locale sopra). Se la pagina non è più dov'era, per esempio dopo una cancellazione che ha rinumerato, o se la lettura fallisce, il conflitto resta aperto con un avviso.

## Limiti noti

- Qualsiasi `TypeError` è trattato come offline.
- Finché c'è un errore di salvataggio o un conflitto, non si può uscire dalla schermata (le modifiche restano comunque nella bozza locale).
- Le bozze si cancellano solo con il logout esplicito.
- Nessuna prova a mano con due schede aperte sulla stessa pagina: da fare con il backend acceso.
- La copia locale nel browser è dichiarata nella pagina Privacy: si cancella con "Esci".

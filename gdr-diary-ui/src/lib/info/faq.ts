export interface FaqItem {
	question: string;
	answer: string;
}

export interface FaqGroup {
	id: string;
	title: string;
	items: readonly FaqItem[];
}

export const FAQ_GROUPS: readonly FaqGroup[] = [
	{
		id: 'iniziare',
		title: 'Per cominciare',
		items: [
			{
				question: 'Che cos’è Diario GDR?',
				answer:
					'È un diario per chi gioca di ruolo. Crei un personaggio (o un profilo Master), gli rilegi uno o più diari e scrivi le sue cronache pagina dopo pagina, su due pagine affiancate come in un libro vero.'
			},
			{
				question: 'Devo pagare?',
				answer:
					'No. Il piano gratuito include tutto ciò che serve per scrivere. Un piano Premium è in preparazione: trovi il confronto nella pagina Piani.'
			},
			{
				question: 'Posso usarlo da telefono e da tablet?',
				answer:
					'Sì. Su telefono vedi una pagina alla volta, su tablet due pagine in scala. Non serve installare nulla.'
			},
			{
				question: 'Posso usarlo se faccio il master?',
				answer:
					'Sì. Alla creazione scegli se è un personaggio o un Master: i diari funzionano nello stesso modo, e il tipo non si cambia dopo.'
			}
		]
	},
	{
		id: 'scrivere',
		title: 'Scrivere',
		items: [
			{
				question: 'Quante pagine può avere un diario?',
				answer:
					'Fino a cento. Le pagine nascono da sole quando riempi quella corrente: non c’è un pulsante «aggiungi».'
			},
			{
				question: 'Come si formatta il testo?',
				answer:
					'Con la barra sopra il libro: titoli, grassetto, corsivo, sottolineato, elenchi, citazioni, allineamento e linea di separazione.'
			},
			{
				question: 'Posso scrivere senza connessione?',
				answer:
					'Per un po’ sì: se la connessione cade, il testo resta in una copia sul dispositivo e l’app riprova da sola a salvarlo. Non è una sincronizzazione completa: appena puoi, riconnettiti.'
			},
			{
				question: 'Cosa succede se apro lo stesso diario su due dispositivi?',
				answer:
					'Se la stessa pagina è stata modificata altrove, l’app te lo dice prima di sovrascrivere e ti lascia scegliere quale versione tenere.'
			}
		]
	},
	{
		id: 'schede',
		title: 'Schede e file',
		items: [
			{
				question: 'Posso caricare la scheda del mio personaggio?',
				answer:
					'Sì, in formato PDF, dalla pagina del personaggio, scheda «Schede del personaggio». Puoi tenerne più di una.'
			},
			{
				question: 'Posso compilare la scheda dentro l’app?',
				answer:
					'Sì, se il PDF ha i campi compilabili standard. Le scansioni e i vecchi moduli non sono supportati.'
			},
			{
				question: 'Che file posso caricare come immagine?',
				answer: 'PNG, JPEG o WebP, per l’avatar e per le copertine.'
			}
		]
	},
	{
		id: 'dati',
		title: 'Account e dati',
		items: [
			{
				question: 'I miei diari sono privati?',
				answer: 'Sì. Per ora li vedi solo tu: la condivisione con altri non c’è ancora.'
			},
			{
				question: 'Posso esportare i miei dati?',
				answer:
					'Per ora su richiesta, scrivendomi. Un’esportazione automatica è nella lista dei lavori.'
			},
			{
				question: 'Come cambio l’email?',
				answer: 'Dal profilo, con «Cambia email». Ti arriva un link di conferma al nuovo indirizzo.'
			},
			{
				question: 'Come elimino l’account?',
				answer:
					'Dal profilo, in fondo alla pagina. Eliminando l’account spariscono tutti i dati: personaggi, schede, diari, pagine, tag e file. Non si torna indietro.'
			}
		]
	},
	{
		id: 'piani',
		title: 'Piani',
		items: [
			{
				question: 'Cosa cambierà con Premium?',
				answer:
					'Più spazio per file e funzioni extra. Le voci sono ancora in definizione: vedi la tabella nella pagina Piani.'
			},
			{
				question: 'Cosa resta gratis?',
				answer:
					'Scrivere, rilegare diari e caricare le tue schede: è il cuore dell’app. Cosa cambierà con Premium è ancora da definire.'
			}
		]
	}
];

function normalize(text: string): string {
	return text
		.normalize('NFD')
		.replace(/\p{Diacritic}/gu, '')
		.toLowerCase()
		.trim();
}

export function filterFaq(groups: readonly FaqGroup[], query: string): FaqGroup[] {
	const needle = normalize(query);
	if (needle === '') {
		return [...groups];
	}
	return groups
		.map((group) => ({
			...group,
			items: group.items.filter((item) =>
				normalize(`${item.question} ${item.answer}`).includes(needle)
			)
		}))
		.filter((group) => group.items.length > 0);
}

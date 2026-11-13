import { documentOptions, loadPdfjs } from '#lib/pdf/pdfjs.js';

export interface ThumbnailBox {
	// misure in pixel CSS dello spazio in cui sta la miniatura
	width: number;
	height: number;
}

const MAX_PARALLEL = 2;
const MAX_RATIO = 2;

let running = 0;
const waiting: (() => void)[] = [];

// Poche miniature alla volta: ogni scheda è un PDF da aprire, e la pagina ne mostra fino a dodici.
async function inLine<T>(work: () => Promise<T>): Promise<T> {
	if (running >= MAX_PARALLEL) {
		await new Promise<void>((resolve) => waiting.push(resolve));
	}
	running += 1;
	try {
		return await work();
	} finally {
		running -= 1;
		waiting.shift()?.();
	}
}

// Disegna la prima pagina del PDF e la restituisce come indirizzo di un'immagine (`blob:`):
// chi lo riceve lo libera con `URL.revokeObjectURL`. Con `signal` si può rinunciare a metà.
export function renderThumbnail(
	url: string,
	box: ThumbnailBox,
	signal?: AbortSignal
): Promise<string> {
	return inLine(async () => {
		signal?.throwIfAborted();
		const kit = await loadPdfjs();
		kit.pdfjs.GlobalWorkerOptions.workerSrc = kit.workerSrc;
		// niente scaricamento in anticipo del resto del file: serve solo la prima pagina
		const task = kit.pdfjs.getDocument({ ...documentOptions(url), disableAutoFetch: true });
		try {
			const doc = await task.promise;
			signal?.throwIfAborted();
			if (doc.isPureXfa) {
				throw new Error('Modulo XFA: niente miniatura.');
			}
			const page = await doc.getPage(1);
			const ratio = Math.min(window.devicePixelRatio, MAX_RATIO);
			const natural = page.getViewport({ scale: 1 });
			const scale = Math.min(
				(box.width * ratio) / natural.width,
				(box.height * ratio) / natural.height
			);
			const viewport = page.getViewport({ scale });
			const canvas = document.createElement('canvas');
			canvas.width = Math.ceil(viewport.width);
			canvas.height = Math.ceil(viewport.height);
			// ENABLE_STORAGE disegna anche i campi compilati del modulo
			await page.render({
				canvas,
				viewport,
				annotationMode: kit.pdfjs.AnnotationMode.ENABLE_STORAGE
			}).promise;
			signal?.throwIfAborted();
			const blob = await new Promise<Blob>((resolve, reject) => {
				canvas.toBlob(
					(result) => {
						if (result === null) {
							reject(new Error('Miniatura non riuscita.'));
						} else {
							resolve(result);
						}
					},
					'image/webp',
					0.85
				);
			});
			return URL.createObjectURL(blob);
		} finally {
			void task.destroy();
		}
	});
}

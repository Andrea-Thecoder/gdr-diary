import type * as PdfjsApi from 'pdfjs-dist/legacy/build/pdf.mjs';
import type * as PdfjsViewer from 'pdfjs-dist/legacy/web/pdf_viewer.mjs';

export interface PdfjsKit {
	pdfjs: typeof PdfjsApi;
	viewer: typeof PdfjsViewer;
	workerSrc: string;
	sandboxSrc: string;
}

// cartella dei file di supporto, servita dal plugin di Vite (vite.config.ts)
export const PDFJS_ASSETS = '/pdfjs/';

// Dove pdf.js trova font, cmap, profili ICC e il wasm dei decodificatori (stessi in editor e miniature).
export function documentOptions(url: string): {
	url: string;
	wasmUrl: string;
	standardFontDataUrl: string;
	cMapUrl: string;
	cMapPacked: boolean;
	iccUrl: string;
} {
	return {
		url,
		wasmUrl: `${PDFJS_ASSETS}wasm/`,
		standardFontDataUrl: `${PDFJS_ASSETS}standard_fonts/`,
		cMapUrl: `${PDFJS_ASSETS}cmaps/`,
		cMapPacked: true,
		iccUrl: `${PDFJS_ASSETS}iccs/`
	};
}

let kit: Promise<PdfjsKit> | undefined;

// pdf.js pesa più di un megabyte: si scarica solo quando si apre una scheda.
export function loadPdfjs(): Promise<PdfjsKit> {
	kit ??= (async () => {
		const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs');
		// il visualizzatore di pdf.js cerca la libreria nell'oggetto globale
		Object.assign(globalThis, { pdfjsLib: pdfjs });
		const [viewer, worker, sandbox] = await Promise.all([
			import('pdfjs-dist/legacy/web/pdf_viewer.mjs'),
			import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'),
			import('pdfjs-dist/legacy/build/pdf.sandbox.min.mjs?url')
		]);
		return {
			pdfjs,
			viewer,
			workerSrc: worker.default,
			sandboxSrc: sandbox.default
		};
	})().catch((cause: unknown) => {
		kit = undefined;
		throw cause;
	});
	return kit;
}

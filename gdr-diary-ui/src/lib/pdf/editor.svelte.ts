import type { PDFDocumentLoadingTask, PDFDocumentProxy } from 'pdfjs-dist/legacy/build/pdf.mjs';
import type { PDFScriptingManager, PDFViewer } from 'pdfjs-dist/legacy/web/pdf_viewer.mjs';
import { loadPdfjs, PDFJS_ASSETS } from '#lib/pdf/pdfjs.js';

export type EditorStatus = 'idle' | 'loading' | 'ready' | 'error';

export const PDF_NOT_READ = 'Non riesco a leggere il PDF.';

interface ModifiedStorage {
	onSetModified: (() => void) | null;
	resetModified: () => void;
}

export class PdfEditor {
	status = $state<EditorStatus>('idle');
	error = $state<string | null>(null);
	// true se il PDF ha campi da compilare (i moduli XFA puri non si leggono)
	editable = $state(false);
	dirty = $state(false);

	private scripting: PDFScriptingManager | null = null;
	private viewer: PDFViewer | null = null;
	private doc: PDFDocumentProxy | null = null;
	private task: PDFDocumentLoadingTask | null = null;
	private closed = false;

	private isStopped(): boolean {
		return this.closed;
	}

	async open(container: HTMLDivElement, viewerElement: HTMLDivElement, url: string): Promise<void> {
		this.status = 'loading';
		this.error = null;
		try {
			const kit = await loadPdfjs();
			if (this.isStopped()) {
				return;
			}
			kit.pdfjs.GlobalWorkerOptions.workerSrc = kit.workerSrc;
			const eventBus = new kit.viewer.EventBus();
			const linkService = new kit.viewer.PDFLinkService({ eventBus });
			const scripting = new kit.viewer.PDFScriptingManager({
				eventBus,
				sandboxBundleSrc: kit.sandboxSrc,
				wasmUrl: `${PDFJS_ASSETS}wasm/`
			});
			const viewer = new kit.viewer.PDFViewer({
				container,
				viewer: viewerElement,
				eventBus,
				linkService,
				scriptingManager: scripting,
				annotationMode: kit.pdfjs.AnnotationMode.ENABLE_FORMS
			});
			linkService.setViewer(viewer);
			scripting.setViewer(viewer);
			eventBus.on('pagesinit', () => {
				viewer.currentScaleValue = 'page-width';
			});
			const task = kit.pdfjs.getDocument({
				url,
				wasmUrl: `${PDFJS_ASSETS}wasm/`,
				standardFontDataUrl: `${PDFJS_ASSETS}standard_fonts/`,
				cMapUrl: `${PDFJS_ASSETS}cmaps/`,
				cMapPacked: true,
				iccUrl: `${PDFJS_ASSETS}iccs/`
			});
			this.task = task;
			const doc = await task.promise;
			if (this.isStopped()) {
				void task.destroy();
				return;
			}
			this.scripting = scripting;
			this.viewer = viewer;
			this.doc = doc;
			const fields = doc.isPureXfa ? null : await doc.getFieldObjects();
			if (this.isStopped()) {
				return;
			}
			this.editable = fields !== null && fields.size > 0;
			(doc.annotationStorage as unknown as ModifiedStorage).onSetModified = () => {
				this.dirty = true;
			};
			viewer.setDocument(doc);
			linkService.setDocument(doc, null);
			this.status = 'ready';
		} catch (cause) {
			if (this.isStopped()) {
				return;
			}
			this.error = PDF_NOT_READ;
			this.status = 'error';
			console.error(cause);
		}
	}

	// Il PDF com'è adesso, con i campi compilati, pronto da mandare al server.
	async save(): Promise<Uint8Array> {
		if (this.doc === null) {
			throw new Error(PDF_NOT_READ);
		}
		await this.scripting?.dispatchWillSave();
		const data = await this.doc.saveDocument();
		await this.scripting?.dispatchDidSave();
		return data;
	}

	markSaved(): void {
		(this.doc?.annotationStorage as unknown as ModifiedStorage | undefined)?.resetModified();
		this.dirty = false;
	}

	destroy(): void {
		this.closed = true;
		this.viewer?.setDocument(null);
		void this.task?.destroy();
		this.viewer = null;
		this.scripting = null;
		this.doc = null;
		this.task = null;
	}
}

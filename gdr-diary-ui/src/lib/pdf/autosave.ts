// Il salvataggio automatico dei PDF compilati segue le regole del diario: parte dopo una pausa
// nella scrittura (la sceglie l'utente nelle impostazioni), ma al più tardi `AUTOSAVE_MAX_WAIT`
// dopo la prima modifica non salvata (mai sotto la pausa scelta).
export const AUTOSAVE_MAX_WAIT = 20000;

export const RETRY_DELAY = 5000;
export const RETRY_MAX_DELAY = 30000;

// attesa prima del tentativo successivo a un salvataggio fallito: 5, 10, 20 e poi 30 secondi
export function retryDelay(attempts: number): number {
	return Math.min(RETRY_DELAY * 2 ** attempts, RETRY_MAX_DELAY);
}

export interface AutoSaveOptions {
	enabled: () => boolean;
	// pausa in millisecondi
	delay: () => number;
	run: () => Promise<void>;
}

export class AutoSaver {
	private readonly options: AutoSaveOptions;
	private timer: ReturnType<typeof setTimeout> | null = null;
	private since: number | null = null;

	constructor(options: AutoSaveOptions) {
		this.options = options;
	}

	isEnabled(): boolean {
		return this.options.enabled();
	}

	// Va chiamato a ogni modifica: riarma l'attesa.
	touch(): void {
		if (!this.options.enabled()) {
			return;
		}
		const now = Date.now();
		const since = this.since ?? now;
		this.since = since;
		const delay = this.options.delay();
		const remaining = since + Math.max(AUTOSAVE_MAX_WAIT, delay) - now;
		this.clear();
		this.timer = setTimeout(
			() => {
				this.timer = null;
				this.since = null;
				void this.options.run();
			},
			Math.max(0, Math.min(delay, remaining))
		);
	}

	cancel(): void {
		this.clear();
		this.since = null;
	}

	private clear(): void {
		if (this.timer !== null) {
			clearTimeout(this.timer);
			this.timer = null;
		}
	}
}

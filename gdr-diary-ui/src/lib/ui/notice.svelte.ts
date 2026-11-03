export class Notice {
	message = $state<string | null>(null);
	failed = $state(false);

	show(message: string, failed = false): void {
		this.failed = failed;
		this.message = message;
	}

	clear(): void {
		this.message = null;
	}
}

export interface DownloadedFile {
	blob: Blob;
	fileName: string;
}

export function fileNameFromDisposition(disposition: string | null, fallback: string): string {
	const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition ?? '');
	if (encoded?.[1] !== undefined) {
		try {
			return decodeURIComponent(encoded[1]);
		} catch {
			return fallback;
		}
	}
	const plain = /filename="([^"]+)"/i.exec(disposition ?? '');
	return plain?.[1] ?? fallback;
}

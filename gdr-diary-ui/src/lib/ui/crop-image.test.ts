import { describe, expect, it } from 'vitest';
import { croppedName } from '#lib/ui/crop-image.js';

describe('croppedName', () => {
	it('swaps the extension', () => {
		expect(croppedName('io.png', 'webp')).toBe('io.webp');
		expect(croppedName('foto.JPEG', 'webp')).toBe('foto.webp');
	});

	it('only removes the last extension', () => {
		expect(croppedName('archivio.tar.gz', 'webp')).toBe('archivio.tar.webp');
	});

	it('adds the extension when there is none', () => {
		expect(croppedName('senza-estensione', 'png')).toBe('senza-estensione.png');
	});

	it('falls back to a generic name', () => {
		expect(croppedName('.png', 'webp')).toBe('immagine.webp');
		expect(croppedName('', 'webp')).toBe('immagine.webp');
	});
});

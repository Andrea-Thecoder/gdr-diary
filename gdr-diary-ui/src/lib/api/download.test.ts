import { describe, expect, it } from 'vitest';
import { fileNameFromDisposition } from './download.js';

describe('fileNameFromDisposition', () => {
	it.each([
		['attachment; filename="diario-gdr-2026-11-19.zip"', 'diario-gdr-2026-11-19.zip'],
		['attachment; filename="a.zip"; filename*=UTF-8\'\'%C3%A9lodie.zip', 'élodie.zip'],
		["attachment; filename*=UTF-8''%E0%A4%A", 'copia.zip'],
		[null, 'copia.zip']
	])('reads %s as %s', (header, name) => {
		expect(fileNameFromDisposition(header, 'copia.zip')).toBe(name);
	});
});

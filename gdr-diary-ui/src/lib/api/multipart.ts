export type ImageChange = { type: 'keep' } | { type: 'remove' } | { type: 'replace'; file: File };

export function fileForm(file: File): () => FormData {
	return () => {
		const form = new FormData();
		form.append('file', file);
		return form;
	};
}

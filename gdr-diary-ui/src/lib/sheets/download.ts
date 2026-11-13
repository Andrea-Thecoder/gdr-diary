export function startDownload(url: string): void {
	const link = document.createElement('a');
	link.href = url;
	link.download = '';
	link.rel = 'noopener';
	document.body.append(link);
	link.click();
	link.remove();
}

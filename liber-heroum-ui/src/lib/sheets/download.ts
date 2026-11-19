export function startDownload(url: string, fileName = ''): void {
	const link = document.createElement('a');
	link.href = url;
	link.download = fileName;
	link.rel = 'noopener';
	document.body.append(link);
	link.click();
	link.remove();
}

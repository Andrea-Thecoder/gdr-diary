(() => {
	const light = "pergamena";
	const dark = "viola";
	const colors = {"pergamena":"#F1E6CC","viola":"#12031F"};
	const cookieName = "gdr-theme";
	const media = window.matchMedia('(prefers-color-scheme: dark)');
	const stored = () => {
		const entry = document.cookie.split('; ').find((part) => part.startsWith(cookieName + '='));
		const value = entry === undefined ? null : decodeURIComponent(entry.slice(cookieName.length + 1));
		return value === light || value === dark ? value : null;
	};
	const apply = () => {
		const theme = stored() ?? (media.matches ? dark : light);
		document.documentElement.setAttribute('data-theme', theme);
		document.querySelector('meta[name="theme-color"]')?.setAttribute('content', colors[theme]);
	};
	apply();
	media.addEventListener('change', apply);
})();

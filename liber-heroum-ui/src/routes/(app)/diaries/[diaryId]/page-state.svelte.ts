import { SvelteURL } from 'svelte/reactivity';

export const pageState = $state<{ url: SvelteURL; shallow: { url: SvelteURL } | null }>({
	url: new SvelteURL('http://localhost/diaries/d1'),
	shallow: null
});

export function setPageUrl(href: string): void {
	pageState.url = new SvelteURL(href, 'http://localhost');
	pageState.shallow = null;
}

export function setShallowUrl(href: string): void {
	pageState.shallow = { url: new SvelteURL(href, 'http://localhost') };
}

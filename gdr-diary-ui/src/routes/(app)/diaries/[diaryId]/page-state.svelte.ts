import { SvelteURL } from 'svelte/reactivity';

export const pageState = $state<{ url: SvelteURL }>({
	url: new SvelteURL('http://localhost/diaries/d1')
});

export function setPageUrl(href: string): void {
	pageState.url = new SvelteURL(href, 'http://localhost');
}

import { test as base } from '@playwright/test';

export { expect } from '@playwright/test';

const KEYCLOAK = 'http://localhost:8180/**';

export const test = base.extend<{ keycloak: undefined }>({
	keycloak: [
		async ({ context }, use) => {
			await context.route(KEYCLOAK, (route) => {
				const url = new URL(route.request().url());
				if (url.pathname.endsWith('/3p-cookies/step1.html')) {
					return route.fulfill({
						status: 200,
						contentType: 'text/html',
						body: "<script>parent.postMessage('supported', '*')</script>"
					});
				}
				if (url.searchParams.get('prompt') === 'none') {
					const redirect = url.searchParams.get('redirect_uri') ?? '';
					const state = url.searchParams.get('state') ?? '';
					return route.fulfill({
						status: 302,
						headers: { location: `${redirect}#error=login_required&state=${state}` }
					});
				}
				return route.fulfill({ status: 200, contentType: 'text/html', body: 'keycloak' });
			});
			await use(undefined);
		},
		{ auto: true }
	]
});

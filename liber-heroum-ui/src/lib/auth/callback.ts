const CALLBACK_KEYS = ['state', 'session_state', 'iss', 'code', 'kc_action_status', 'kc_action'];

export function withoutAuthCallback(href: string): string | null {
	const url = new URL(href);
	const fragment = new URLSearchParams(url.hash.slice(1));
	if (!fragment.has('state') || !(fragment.has('code') || fragment.has('kc_action_status'))) {
		return null;
	}
	for (const key of CALLBACK_KEYS) {
		fragment.delete(key);
	}
	url.hash = fragment.toString();
	return url.href;
}

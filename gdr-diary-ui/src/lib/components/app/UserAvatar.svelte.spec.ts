import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import UserAvatar from './UserAvatar.svelte';

describe('UserAvatar', () => {
	it('shows the initial of the name without an image', async () => {
		const screen = await render(UserAvatar, { label: 'lyra', url: null });

		expect(screen.container.textContent.trim()).toBe('L');
		expect(screen.container.querySelector('img')).toBeNull();
	});

	it('shows the image when there is one', async () => {
		const url =
			'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
		const screen = await render(UserAvatar, { label: 'lyra', url });

		expect(screen.container.querySelector('img')?.getAttribute('src')).toBe(url);
		expect(screen.container.textContent.trim()).toBe('');
	});
});

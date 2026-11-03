import { getApi } from '#lib/api/index.js';
import { fetchAvatarUrl } from './api.js';

class ProfileAvatar {
	url = $state<string | null>(null);

	private refreshing = false;

	async refresh(): Promise<void> {
		if (this.refreshing) {
			return;
		}
		this.refreshing = true;
		try {
			const fresh = await fetchAvatarUrl(getApi());
			if (fresh !== this.url) {
				this.url = fresh;
			}
		} catch {
			return;
		} finally {
			this.refreshing = false;
		}
	}
}

export const profileAvatar = new ProfileAvatar();

<script lang="ts">
	import { onMount } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import { auth } from '#lib/auth/auth.svelte.js';
	import Button from '#lib/components/ui/Button.svelte';
	import { RecentLoginRequiredError } from '#lib/profile/api.js';
	import { downloadUserExport, profileUrl } from '#lib/profile/export.js';
	import { startDownload } from '#lib/sheets/download.js';
	import type { Notice } from '#lib/ui/notice.svelte.js';
	import { rememberPendingAction, takePendingAction } from '#lib/ui/pending-action.js';

	interface Props {
		notice: Notice;
		class?: string;
	}

	let { notice, class: className = '' }: Props = $props();

	let exporting = $state(false);

	function relogin(): void {
		rememberPendingAction('export-data');
		void auth.reauthenticate(profileUrl(window.location.origin));
	}

	async function exportData(): Promise<void> {
		if (!auth.recentlyAuthenticated()) {
			relogin();
			return;
		}
		exporting = true;
		try {
			const file = await downloadUserExport(getApi());
			const url = URL.createObjectURL(file.blob);
			startDownload(url, file.fileName);
			setTimeout(() => {
				URL.revokeObjectURL(url);
			}, 60_000);
			notice.show('Ecco la copia dei tuoi dati: la trovi tra i download.');
		} catch (cause) {
			if (cause instanceof RecentLoginRequiredError) {
				relogin();
				return;
			}
			notice.show(
				cause instanceof Error ? cause.message : 'Non riesco a preparare la copia dei tuoi dati.',
				true
			);
		} finally {
			exporting = false;
		}
	}

	onMount(() => {
		if (takePendingAction('export-data')) {
			void exportData();
		}
	});
</script>

<Button
	variant="outline"
	size="sm"
	class={className}
	disabled={exporting}
	aria-busy={exporting}
	onclick={() => {
		void exportData();
	}}
>
	{exporting ? 'Preparo il file…' : 'Scarica i miei dati'}
</Button>

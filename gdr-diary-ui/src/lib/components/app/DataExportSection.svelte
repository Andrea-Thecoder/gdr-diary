<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { onMount } from 'svelte';
	import { getApi } from '#lib/api/index.js';
	import { auth } from '#lib/auth/auth.svelte.js';
	import Button from '#lib/components/ui/Button.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import { RecentLoginRequiredError } from '#lib/profile/api.js';
	import { downloadUserExport, exportReturnUrl, wantsExport } from '#lib/profile/export.js';
	import { startDownload } from '#lib/sheets/download.js';
	import type { Notice } from '#lib/ui/notice.svelte.js';

	interface Props {
		notice: Notice;
		class?: string;
	}

	let { notice, class: className = '' }: Props = $props();

	let exporting = $state(false);

	function relogin(): void {
		void auth.reauthenticate(exportReturnUrl(window.location.origin));
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
		if (wantsExport(window.location.href)) {
			void goto(resolve('/(app)/profile'), { shallow: true, replace: true });
			void exportData();
		}
	});
</script>

<SettingsSection
	title="I tuoi dati"
	intro="Una copia di tutto quello che hai salvato, da tenere o portare altrove."
	class={className}
>
	{#snippet action()}
		<Button
			variant="outline"
			size="sm"
			disabled={exporting}
			aria-busy={exporting}
			onclick={() => {
				void exportData();
			}}
		>
			{exporting ? 'Preparo il file…' : 'Scarica i miei dati'}
		</Button>
	{/snippet}
	<p class="m-0 text-[17px] text-soft">
		Un file ZIP con account e impostazioni, i personaggi con la loro storia, le schede in PDF, i
		diari con tutte le pagine, le immagini e i pagamenti. I testi sono in formato JSON, leggibile da
		un computer. Per sicurezza ti chiederemo di nuovo la password.
	</p>
</SettingsSection>

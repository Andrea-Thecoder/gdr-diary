<script lang="ts">
	import { getApi } from '#lib/api/index.js';
	import ConfirmDeleteDialog from '#lib/components/ui/ConfirmDeleteDialog.svelte';
	import { deleteAccount, RecentLoginRequiredError } from '#lib/profile/api.js';

	interface Props {
		open: boolean;
		onclose: () => void;
		ondeleted: () => void;
		onrelogin: () => boolean;
	}

	let { open, onclose, ondeleted, onrelogin }: Props = $props();

	async function confirm(): Promise<void> {
		try {
			await deleteAccount(getApi());
		} catch (cause) {
			if (cause instanceof RecentLoginRequiredError) {
				if (onrelogin()) {
					return;
				}
				throw new Error(
					'Non riesco a confermare il tuo accesso. Esci, rientra con la password e riprova.',
					{ cause }
				);
			}
			throw cause;
		}
		ondeleted();
	}
</script>

<ConfirmDeleteDialog
	{open}
	{onclose}
	onconfirm={confirm}
	title="Eliminare l’account?"
	subtitle="Stai per cancellare tutto quello che hai creato."
	consequences={[
		'tutti i tuoi personaggi;',
		'tutti i loro diari, con ogni pagina scritta;',
		'le schede in PDF e le immagini che hai caricato;',
		'il tuo accesso: non potrai più entrare con questo indirizzo.'
	]}
	fallbackError="Non riesco a eliminare l’account."
/>

<script lang="ts">
	import Button from './Button.svelte';

	interface Props {
		message: string;
		saving: boolean;
		error: string | null;
		oncancel: () => void;
	}

	let { message, saving, error, oncancel }: Props = $props();
</script>

<div
	class="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 rounded-[8px] border border-(color:--card-border) px-5 py-4 text-ink shadow-(--modal-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
>
	<div class="min-w-0 flex-[1_1_280px]">
		<p class="m-0 text-[17px]">{message}</p>
		{#if error !== null}
			<p role="alert" class="mx-0 mt-1 mb-0 text-base text-label">{error}</p>
		{/if}
	</div>
	<div class="flex flex-wrap gap-3">
		<Button variant="outline" size="form" onclick={oncancel} disabled={saving}>Annulla</Button>
		<Button type="submit" size="form" disabled={saving}>
			{saving ? 'Salvo…' : 'Salva le impostazioni'}
		</Button>
	</div>
</div>

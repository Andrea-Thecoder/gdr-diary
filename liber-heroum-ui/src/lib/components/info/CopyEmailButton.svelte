<script lang="ts">
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';

	interface Props {
		text: string;
	}

	let { text }: Props = $props();

	const RESET_AFTER = 2200;

	let result = $state<'idle' | 'copied' | 'failed'>('idle');
	let timer: ReturnType<typeof setTimeout> | undefined;

	async function copy(): Promise<void> {
		clearTimeout(timer);
		try {
			await navigator.clipboard.writeText(text);
			result = 'copied';
		} catch {
			result = 'failed';
		}
		timer = setTimeout(() => {
			result = 'idle';
		}, RESET_AFTER);
	}

	$effect(() => () => {
		clearTimeout(timer);
	});
</script>

<Button variant="outline" size="md" onclick={copy}>
	<Icon size={16} strokeWidth={1.8}>
		<rect x="9" y="9" width="11" height="11" rx="2" />
		<path d="M5 15V6a2 2 0 012-2h9" />
	</Icon>
	{#if result === 'copied'}
		Indirizzo copiato
	{:else if result === 'failed'}
		Non riesco a copiare
	{:else}
		Copia l’indirizzo
	{/if}
</Button>
<span role="status" class="sr-only">
	{result === 'copied' ? 'Indirizzo copiato negli appunti' : ''}
</span>

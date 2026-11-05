<script lang="ts">
	import type { Snippet } from 'svelte';
	import Button from './Button.svelte';
	import Icon from './Icon.svelte';
	import LinkButton from './LinkButton.svelte';

	interface Order {
		label: string;
		hint: string;
		up: boolean;
	}

	interface Props {
		sortLabel: string;
		descending: boolean;
		count: string;
		canClear: boolean;
		order?: Order | undefined;
		ontoggleorder: () => void;
		onapply: () => void;
		onclear: () => void;
		children: Snippet;
	}

	let {
		sortLabel,
		descending,
		count,
		canClear,
		order,
		ontoggleorder,
		onapply,
		onclear,
		children
	}: Props = $props();

	const shown = $derived<Order>(
		order ?? {
			label: `${sortLabel} ${descending ? 'Z-A' : 'A-Z'}`,
			hint: `Ordina per ${sortLabel.toLowerCase()}, ${descending ? 'dalla Z alla A' : 'dalla A alla Z'}`,
			up: descending
		}
	);
</script>

<form
	role="search"
	onsubmit={(event) => {
		event.preventDefault();
		onapply();
	}}
	class="mb-[clamp(36px,5vw,56px)] flex flex-wrap items-center gap-x-4 gap-y-3"
>
	{@render children()}
	<button
		type="button"
		onclick={ontoggleorder}
		aria-label={shown.hint}
		class="inline-flex min-h-[50px] cursor-pointer items-center gap-2.5 rounded-[6px] border border-(color:--tool-border) px-[18px] font-label text-[13px] font-medium tracking-[.12em] text-tool-ink uppercase hover:opacity-85"
		style:background="var(--tool-bg)"
	>
		{shown.label}
		<Icon size={16} strokeWidth={2} class={shown.up ? 'rotate-180' : ''}>
			<path d="M12 5v14M6 13l6 6 6-6" />
		</Icon>
	</button>
	<Button type="submit" size="sm">Applica filtri</Button>
	{#if canClear}
		<LinkButton onclick={onclear}>Azzera</LinkButton>
	{/if}
	<p class="m-0 ml-auto flex-none text-base text-soft" role="status">{count}</p>
</form>

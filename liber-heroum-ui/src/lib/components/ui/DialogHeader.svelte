<script lang="ts">
	import Icon from './Icon.svelte';
	import IconButton from './IconButton.svelte';

	interface Props {
		titleId: string;
		title: string;
		subtitle: string;
		onclose: () => void;
	}

	let { titleId, title, subtitle, onclose }: Props = $props();
</script>

<div class="flex items-start justify-between gap-4">
	<div class="min-w-0">
		<h2
			id={titleId}
			class="m-0 font-display text-[clamp(32px,5vw,42px)] leading-[1.05] font-bold [overflow-wrap:anywhere] text-ink"
		>
			{title}
		</h2>
		<p class="mx-0 mt-2 mb-0 text-[18px] [overflow-wrap:anywhere] text-soft">{subtitle}</p>
	</div>
	<IconButton label="Chiudi" onclick={onclose} class="flex-none rounded-full">
		<Icon size={18} strokeWidth={2}>
			<path d="M6 6l12 12M18 6L6 18" />
		</Icon>
	</IconButton>
</div>

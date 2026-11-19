<script lang="ts">
	import { postitColor, postitTilt } from '#lib/book/tags.js';
	import Icon from '#lib/components/ui/Icon.svelte';
	import PostIt from '#lib/components/ui/PostIt.svelte';

	interface Props {
		page: number;
		tag: string | null;
		ready: boolean;
		gone: boolean;
		onopen: () => void;
	}

	let { page, tag, ready, gone, onopen }: Props = $props();

	const addTitle = $derived.by(() => {
		if (gone) {
			return 'La pagina è stata eliminata altrove.';
		}
		if (!ready) {
			return 'Appena la pagina è salvata puoi mettere il tag.';
		}
		return 'Metti un tag a questa pagina';
	});
</script>

<div
	role="group"
	aria-label={`Tag della pagina ${String(page)}`}
	style:position="absolute"
	style:top="100%"
	style:left="4px"
	style:right="4px"
	class="z-10 flex [scrollbar-width:thin] items-start gap-2 overflow-x-auto px-1 pt-1 pb-2"
>
	{#if tag === null}
		<PostIt
			color={null}
			label={`Metti un tag, pagina ${String(page)}`}
			title={addTitle}
			disabled={!ready || gone}
			onclick={onopen}
		>
			<Icon size={16} strokeWidth={2}>
				<path d="M12 5v14M5 12h14" />
			</Icon>
			<span class="[writing-mode:vertical-rl]">Tag</span>
		</PostIt>
	{:else}
		<PostIt
			color={postitColor(tag)}
			tilt={postitTilt(tag)}
			label={`Tag ${tag}, pagina ${String(page)}`}
			title={tag}
			disabled={gone}
			onclick={onopen}
		>
			<span
				class="min-h-0 overflow-hidden text-ellipsis whitespace-nowrap [writing-mode:vertical-rl]"
			>
				{tag}
			</span>
		</PostIt>
	{/if}
</div>

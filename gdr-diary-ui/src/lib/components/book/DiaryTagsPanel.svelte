<script lang="ts">
	import { pagesLabel, type DiaryTag } from '#lib/book/tags.js';
	import type { DiaryTagList } from '#lib/book/tags.svelte.js';
	import Button from '#lib/components/ui/Button.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';
	import TagChip from '#lib/components/ui/TagChip.svelte';

	interface Props {
		list: DiaryTagList;
		ongo: (tag: DiaryTag) => void;
		class?: string;
	}

	let { list, ongo, class: className = '' }: Props = $props();
</script>

<section aria-labelledby="diary-tags-title" class={['flex flex-col gap-3', className]}>
	<SubHeading as="h2" size="sm" id="diary-tags-title">Tag del diario</SubHeading>
	{#if list.status === 'error' && list.items.length === 0}
		<p class="m-0 text-base text-label">{list.error}</p>
		<Button
			variant="outline"
			size="sm"
			class="self-start"
			onclick={() => {
				void list.reload();
			}}
		>
			Riprova
		</Button>
	{:else if list.status === 'loading' && list.items.length === 0}
		<p role="status" class="m-0 text-base text-soft">Carico i tag…</p>
	{:else if list.items.length === 0}
		<p class="m-0 text-base text-soft">
			Nessun tag ancora: aggiungine uno a una pagina e comparirà qui, con i numeri delle pagine.
		</p>
	{:else}
		<ul class="m-0 flex list-none flex-wrap gap-2 p-0">
			{#each list.items as tag, index (index)}
				<li class="max-w-full min-w-0">
					<TagChip
						label={tag.name}
						detail={pagesLabel(tag.pageNumbers)}
						onselect={() => {
							ongo(tag);
						}}
					/>
				</li>
			{/each}
		</ul>
		{#if list.error !== null}
			<p class="m-0 text-base text-label">{list.error}</p>
		{/if}
		{#if list.hasMore}
			<Button
				variant="outline"
				size="sm"
				class="self-start"
				disabled={list.loadingMore}
				onclick={() => {
					void list.loadMore();
				}}
			>
				Mostra altri tag
			</Button>
		{/if}
	{/if}
</section>

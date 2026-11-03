<script lang="ts" generics="T">
	import type { Snippet } from 'svelte';
	import type { PagedList } from '#lib/list/paged-list.svelte.js';
	import Button from './Button.svelte';
	import CardGrid from './CardGrid.svelte';
	import LinkButton from './LinkButton.svelte';

	interface Props {
		list: PagedList<T>;
		itemKey: (item: T) => string | undefined;
		firstTime: boolean;
		loadingText: string;
		noMatchText: string;
		moreLabel: string;
		onclearfilters: () => void;
		card: Snippet<[T]>;
		newCard: Snippet;
		firstTimeCard: Snippet;
	}

	let {
		list,
		itemKey,
		firstTime,
		loadingText,
		noMatchText,
		moreLabel,
		onclearfilters,
		card,
		newCard,
		firstTimeCard
	}: Props = $props();
</script>

{#if list.status === 'loading' && list.items.length === 0}
	<p role="status" class="m-0 text-center text-[20px] text-soft">{loadingText}</p>
{:else if list.status === 'error' && list.items.length === 0}
	<div role="alert" class="flex flex-col items-center gap-5 text-center">
		<p class="m-0 text-[20px] text-ink">{list.error}</p>
		<Button
			variant="outline"
			size="form"
			onclick={() => {
				void list.reload();
			}}
		>
			Riprova
		</Button>
	</div>
{:else if firstTime}
	<CardGrid single>
		<li class="min-w-0">
			{@render firstTimeCard()}
		</li>
	</CardGrid>
{:else if list.items.length === 0}
	<div class="flex flex-col items-center gap-3 text-center">
		<p class="m-0 text-[20px] text-ink">{noMatchText}</p>
		<LinkButton onclick={onclearfilters}>Azzera i filtri</LinkButton>
	</div>
{:else}
	<CardGrid
		aria-busy={list.status === 'loading'}
		class={list.status === 'loading' ? 'opacity-60' : ''}
	>
		{#each list.items as item (itemKey(item))}
			<li class="min-w-0">
				{@render card(item)}
			</li>
		{/each}
		{#if !list.hasMore}
			<li class="min-w-0">
				{@render newCard()}
			</li>
		{/if}
	</CardGrid>
	{#if list.error !== null}
		<p role="alert" class="mx-0 mt-8 mb-0 text-center text-base text-label">{list.error}</p>
	{/if}
	{#if list.hasMore}
		<div class="mt-12 flex justify-center">
			<Button
				variant="outline"
				size="form"
				disabled={list.loadingMore}
				onclick={() => {
					void list.loadMore();
				}}
			>
				{list.loadingMore ? 'Carico…' : moreLabel}
			</Button>
		</div>
	{/if}
{/if}

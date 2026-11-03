<script lang="ts">
	import type { DiaryBook, Slot } from '#lib/book/book.svelte.js';
	import type { Typography } from '#lib/book/layout.js';
	import BookPage from './BookPage.svelte';
	import LivePage from './LivePage.svelte';

	interface Props {
		book: DiaryBook;
		slot: Slot;
		side: 'left' | 'right';
		typography: Typography;
		scale: number;
		live: boolean;
		hint?: string;
	}

	let { book, slot, side, typography, scale, live, hint = '' }: Props = $props();
</script>

<div class="absolute top-0 left-0 origin-top-left" style:transform={`scale(${String(scale)})`}>
	{#if live}
		<LivePage {book} {slot} {side} {typography} {hint} />
	{:else}
		<BookPage page={slot.page} content={slot.content} editable={false} {side} {typography} />
	{/if}
</div>

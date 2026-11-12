<script lang="ts">
	import type { DiaryBook } from '#lib/book/book.svelte.js';
	import Button from '#lib/components/ui/Button.svelte';
	import BookMessage from './BookMessage.svelte';

	interface Props {
		book: DiaryBook;
	}

	let { book }: Props = $props();

	const failed = $derived(
		book.visible.flatMap((page) => {
			const id = book.pageId(page);
			const message = id === null ? null : book.tags.errorOf(id);
			return id === null || message === null
				? []
				: [{ page, message, retry: book.tags.pendingOf(id) && !book.tags.goneOf(id) }];
		})
	);
</script>

{#each failed as item (item.page)}
	<BookMessage tone="error">
		Tag della pagina {item.page}: {item.message}
		{#snippet action()}
			{#if item.retry}
				<Button
					variant="outline"
					size="sm"
					onclick={() => {
						void book.tags.flush();
					}}
				>
					Riprova a salvare il tag
				</Button>
			{/if}
		{/snippet}
	</BookMessage>
{/each}

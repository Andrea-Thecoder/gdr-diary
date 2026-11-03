<script lang="ts">
	import type { JSONContent } from '@tiptap/core';
	import type { DiaryBook, PageHandle, Slot } from '#lib/book/book.svelte.js';
	import { MAX_PAGES } from '#lib/book/pages.js';
	import type { Typography } from '#lib/book/layout.js';
	import BookPage from './BookPage.svelte';

	interface Props {
		book: DiaryBook;
		slot: Slot;
		side: 'left' | 'right';
		typography: Typography;
		hint: string;
	}

	let { book, slot, side, typography, hint }: Props = $props();

	const page = $derived(slot.page);
</script>

<BookPage
	{page}
	content={slot.content}
	editable={book.editable(page)}
	{side}
	{typography}
	{hint}
	canSpill={page < MAX_PAGES}
	onchange={(content: JSONContent) => {
		book.update(page, content);
	}}
	onoverflow={(rest: JSONContent, cursor: number | null) => {
		void book.spill(page, rest, cursor);
	}}
	onfull={() => {
		book.notice.show('Il diario è pieno: la pagina 100 non ha più spazio.', true);
	}}
	onfocus={() => {
		book.activate(page);
	}}
	ontransaction={() => {
		if (book.activePage === page) {
			book.touch();
		}
	}}
	onhandle={(handle: PageHandle | null) => {
		if (handle === null) {
			const known = book.editors.get(page);
			if (known !== undefined) {
				book.unregister(page, known);
			}
		} else {
			book.register(page, handle);
		}
	}}
	onbackspace={page > 1
		? () => {
				void book.previousPage(page);
			}
		: undefined}
	takePending={() => book.takePending(page)}
	takeTyped={() => book.takeTyped()}
	onfill={(full: boolean) => {
		book.markFull(page, full);
	}}
/>

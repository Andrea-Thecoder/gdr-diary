<script lang="ts">
	import { Editor, type JSONContent } from '@tiptap/core';
	import { untrack } from 'svelte';
	import { diaryExtensions } from '#lib/book/extensions.js';
	import {
		BODY_HEIGHT,
		PAGE_HEIGHT,
		PAGE_SIDE,
		PAGE_TOP,
		PAGE_WIDTH,
		type Typography
	} from '#lib/book/layout.js';
	import type { PageHandle } from '#lib/book/book.svelte.js';
	import {
		isBlankDoc,
		isFull,
		overflows,
		prependToEditor,
		splitOverflow
	} from '#lib/book/pagination.js';

	interface Props {
		page: number;
		content: JSONContent;
		editable: boolean;
		side: 'left' | 'right';
		typography: Typography;
		hint?: string;
		canSpill?: boolean;
		class?: string;
		onchange?: ((content: JSONContent) => void) | undefined;
		onoverflow?: ((rest: JSONContent, cursor: number | null) => void) | undefined;
		onfull?: (() => void) | undefined;
		onfocus?: (() => void) | undefined;
		ontransaction?: (() => void) | undefined;
		onhandle?: ((handle: PageHandle | null) => void) | undefined;
		onbackspace?: (() => void) | undefined;
		onfill?: ((full: boolean) => void) | undefined;
		takePending?: (() => { cursor: number | null } | null) | undefined;
		takeTyped?: (() => string[]) | undefined;
	}

	let {
		page,
		content,
		editable,
		side,
		typography,
		hint = '',
		canSpill = true,
		class: className = '',
		onchange,
		onoverflow,
		onfull,
		onfocus,
		ontransaction,
		onhandle,
		onbackspace,
		onfill,
		takePending,
		takeTyped
	}: Props = $props();

	let element = $state<HTMLDivElement | null>(null);
	let editor = $state.raw<Editor | null>(null);
	let blank = $state(untrack(() => isBlankDoc(content)));

	function replay(current: Editor, pieces: string[]): void {
		for (const piece of pieces) {
			if (piece === '\n') {
				current.commands.splitBlock();
			} else {
				current.commands.insertContent(piece);
			}
		}
	}

	function check(current: Editor): void {
		if (!overflows(current, BODY_HEIGHT)) {
			return;
		}
		if (!untrack(() => canSpill)) {
			current.commands.undo();
			onfull?.();
			return;
		}
		const split = splitOverflow(current, BODY_HEIGHT);
		if (split !== null) {
			onoverflow?.(split.rest, split.cursor);
		}
	}

	function report(current: Editor): void {
		onfill?.(
			isFull(
				current,
				BODY_HEIGHT,
				untrack(() => typography.lineHeight)
			)
		);
	}

	function changed(current: Editor): void {
		check(current);
		report(current);
		const json = current.getJSON();
		blank = isBlankDoc(json);
		onchange?.(json);
	}

	$effect(() => {
		if (element === null) {
			return;
		}
		const created = new Editor({
			element,
			extensions: diaryExtensions(),
			content: untrack(() => content),
			editable: untrack(() => editable),
			editorProps: {
				attributes: { class: 'diary-prose', 'aria-label': `Pagina ${String(untrack(() => page))}` },
				handleKeyDown: (view, event) => {
					const { selection } = view.state;
					const handler = untrack(() => onbackspace);
					if (
						handler === undefined ||
						event.key !== 'Backspace' ||
						event.ctrlKey ||
						event.metaKey ||
						event.altKey ||
						event.shiftKey ||
						!selection.empty ||
						selection.$from.pos !== 1 ||
						selection.$from.parent.type.name !== 'paragraph'
					) {
						return false;
					}
					handler();
					return true;
				}
			},
			onUpdate: ({ editor: current }) => {
				changed(current);
			},
			onFocus: () => {
				onfocus?.();
			},
			onTransaction: () => {
				ontransaction?.();
			}
		});
		editor = created;
		const handle: PageHandle = {
			editor: created,
			prepend: (rest, cursor) => {
				prependToEditor(created, rest, cursor);
			},
			focus: () => {
				created.commands.focus();
			},
			focusEnd: () => {
				created.commands.focus('end');
			},
			insert: (pieces) => {
				replay(created, pieces);
				changed(created);
			}
		};
		untrack(() => onhandle?.(handle));
		const measure = requestAnimationFrame(() => {
			report(created);
		});
		void document.fonts.ready.then(() => {
			if (editor === created) {
				report(created);
			}
		});
		const request = untrack(() => takePending?.() ?? null);
		let frame = 0;
		if (request !== null) {
			frame = requestAnimationFrame(() => {
				if (request.cursor !== null) {
					created.commands.setTextSelection(
						Math.min(Math.max(request.cursor, 1), created.state.doc.content.size - 1)
					);
				}
				created.commands.focus();
				replay(created, takeTyped?.() ?? []);
				changed(created);
			});
		}
		return () => {
			cancelAnimationFrame(frame);
			cancelAnimationFrame(measure);
			untrack(() => onhandle?.(null));
			created.destroy();
			editor = null;
		};
	});

	$effect(() => {
		if (editor !== null && editor.isEditable !== editable) {
			editor.setEditable(editable, false);
		}
	});
</script>

<div
	class={[
		'relative overflow-hidden bg-(--page) text-page-ink focus-within:shadow-[inset_0_0_40px_-12px_var(--sel)]',
		className
	]}
	style:width={`${String(PAGE_WIDTH)}px`}
	style:height={`${String(PAGE_HEIGHT)}px`}
>
	<div aria-hidden="true" class="paper-aged pointer-events-none absolute inset-0"></div>
	<div aria-hidden="true" class="paper-grain pointer-events-none absolute inset-0"></div>
	<div
		aria-hidden="true"
		class={[
			'paper-frame pointer-events-none absolute inset-y-4',
			side === 'left' ? 'right-5 left-4' : 'right-4 left-5'
		]}
	></div>
	<div
		aria-hidden="true"
		class={[
			'pointer-events-none absolute inset-y-0 w-16',
			side === 'left' ? 'paper-gutter-left right-0' : 'paper-gutter-right left-0'
		]}
	></div>
	<div
		aria-hidden="true"
		class={[
			'pointer-events-none absolute inset-0',
			side === 'left' ? 'paper-edge-left' : 'paper-edge-right'
		]}
	></div>
	<div
		class="absolute"
		style:top={`${String(PAGE_TOP)}px`}
		style:left={`${String(PAGE_SIDE)}px`}
		style:right={`${String(PAGE_SIDE)}px`}
		style:height={`${String(BODY_HEIGHT)}px`}
		style:--lh={`${String(typography.lineHeight)}px`}
		style:--diary-font={`'${typography.font}'`}
		style:--diary-size={`${String(typography.size)}px`}
	>
		<div bind:this={element} class="size-full overflow-hidden"></div>
		{#if hint !== '' && blank}
			<p
				class="pointer-events-none absolute inset-x-0 top-0 m-0 font-body text-[19px] leading-(--lh) text-page-soft italic"
			>
				{hint}
			</p>
		{/if}
	</div>
	<span
		aria-hidden="true"
		class="absolute inset-x-0 bottom-6 flex items-center justify-center gap-3 font-label text-[13px] tracking-[.14em] text-page-soft"
	>
		<span class="h-px w-8 bg-linear-to-r from-transparent to-(--orn) opacity-60"></span>
		{page}
		<span class="h-px w-8 bg-linear-to-l from-transparent to-(--orn) opacity-60"></span>
	</span>
</div>

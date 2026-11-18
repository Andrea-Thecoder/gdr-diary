<script lang="ts">
	import { Editor, type JSONContent } from '@tiptap/core';
	import { untrack } from 'svelte';
	import { TextLimit } from '#lib/background/limit.js';
	import { diaryExtensions } from '#lib/book/extensions.js';
	import { isBlankDoc } from '#lib/book/pagination.js';
	import type { Typography } from '#lib/book/layout.js';

	interface Props {
		content: JSONContent;
		editable: boolean;
		typography: Typography;
		maxChars: number;
		label: string;
		hint?: string;
		class?: string;
		onchange?: ((content: JSONContent) => void) | undefined;
		onfull?: (() => void) | undefined;
		ontransaction?: (() => void) | undefined;
		oneditor?: ((editor: Editor | null) => void) | undefined;
	}

	let {
		content,
		editable,
		typography,
		maxChars,
		label,
		hint = '',
		class: className = '',
		onchange,
		onfull,
		ontransaction,
		oneditor
	}: Props = $props();

	let element = $state<HTMLDivElement | null>(null);
	let editor = $state.raw<Editor | null>(null);
	let blank = $state(untrack(() => isBlankDoc(content)));

	function changed(current: Editor): void {
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
			extensions: [
				...diaryExtensions(),
				TextLimit.configure({
					max: untrack(() => maxChars),
					onRefuse: () => {
						onfull?.();
					}
				})
			],
			content: untrack(() => content),
			editable: untrack(() => editable),
			editorProps: {
				attributes: { class: 'diary-prose background-prose', 'aria-label': untrack(() => label) }
			},
			onUpdate: ({ editor: current }) => {
				changed(current);
			},
			onTransaction: () => {
				ontransaction?.();
			}
		});
		editor = created;
		untrack(() => oneditor?.(created));
		return () => {
			untrack(() => oneditor?.(null));
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
	class={['relative', className]}
	style:--lh={`${String(typography.lineHeight)}px`}
	style:--diary-font={`'${typography.font}'`}
	style:--diary-size={`${String(typography.size)}px`}
>
	<div bind:this={element} data-background-text></div>
	{#if hint !== '' && blank}
		<p
			class="pointer-events-none absolute inset-x-0 top-0 m-0 font-body text-[19px] leading-(--lh) text-page-soft italic"
		>
			{hint}
		</p>
	{/if}
</div>

<style>
	:global(.background-prose) {
		height: auto;
		min-height: 40dvh;
	}
</style>

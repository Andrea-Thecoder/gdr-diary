<script lang="ts">
	import type { Editor } from '@tiptap/core';
	import Icon from '#lib/components/ui/Icon.svelte';
	import ToggleButton from '#lib/components/ui/ToggleButton.svelte';

	interface Props {
		editor: Editor | null;
		touched: number;
		class?: string;
	}

	let { editor, touched, class: className = '' }: Props = $props();

	interface Tool {
		label: string;
		glyph?: string;
		glyphClass?: string;
		path?: string;
		run: (editor: Editor) => void;
		active?: (editor: Editor) => boolean;
		enabled?: (editor: Editor) => boolean;
	}

	const groups: readonly (readonly Tool[])[] = [
		[
			{
				label: 'Titolo 1',
				glyph: 'H1',
				glyphClass: 'font-display text-[22px] font-bold',
				run: (e) => e.chain().focus().toggleTitle(1).run(),
				active: (e) => e.isActive('title', { level: 1 })
			},
			{
				label: 'Titolo 2',
				glyph: 'H2',
				glyphClass: 'font-display text-[19px] font-bold',
				run: (e) => e.chain().focus().toggleTitle(2).run(),
				active: (e) => e.isActive('title', { level: 2 })
			}
		],
		[
			{
				label: 'Grassetto',
				glyph: 'B',
				glyphClass: 'text-[20px] font-bold',
				run: (e) => e.chain().focus().toggleBold().run(),
				active: (e) => e.isActive('bold')
			},
			{
				label: 'Corsivo',
				glyph: 'I',
				glyphClass: 'text-[20px] italic',
				run: (e) => e.chain().focus().toggleItalic().run(),
				active: (e) => e.isActive('italic')
			},
			{
				label: 'Sottolineato',
				glyph: 'U',
				glyphClass: 'text-[20px] underline',
				run: (e) => e.chain().focus().toggleUnderline().run(),
				active: (e) => e.isActive('underline')
			}
		],
		[
			{
				label: 'Elenco puntato',
				path: 'M9 7h11M9 12h11M9 17h11M4.5 7h.01M4.5 12h.01M4.5 17h.01',
				run: (e) => e.chain().focus().toggleBulletList().run(),
				active: (e) => e.isActive('bulletList')
			},
			{
				label: 'Elenco numerato',
				path: 'M10 7h10M10 12h10M10 17h10M4 6l1.5-1v4M4 15.5c.5-1 2.5-1 2.5.3 0 1-2.5 1.7-2.5 2.7h2.7',
				run: (e) => e.chain().focus().toggleOrderedList().run(),
				active: (e) => e.isActive('orderedList')
			},
			{
				label: 'Citazione',
				path: 'M5 17c0-4 1-7 4-9M13 17c0-4 1-7 4-9M5 17h4v-4M13 17h4v-4',
				run: (e) => e.chain().focus().toggleBlockquote().run(),
				active: (e) => e.isActive('blockquote')
			}
		],
		[
			{
				label: 'Allinea a sinistra',
				path: 'M4 7h16M4 12h10M4 17h14',
				run: (e) => e.chain().focus().setTextAlign('left').run(),
				active: (e) => e.isActive({ textAlign: 'left' })
			},
			{
				label: 'Centra',
				path: 'M4 7h16M7 12h10M5 17h14',
				run: (e) => e.chain().focus().setTextAlign('center').run(),
				active: (e) => e.isActive({ textAlign: 'center' })
			},
			{
				label: 'Allinea a destra',
				path: 'M4 7h16M10 12h10M6 17h14',
				run: (e) => e.chain().focus().setTextAlign('right').run(),
				active: (e) => e.isActive({ textAlign: 'right' })
			},
			{
				label: 'Linea di separazione',
				path: 'M4 12h16M8 7l4-3 4 3M8 17l4 3 4-3',
				run: (e) => e.chain().focus().setHorizontalRule().run()
			}
		],
		[
			{
				label: 'Annulla',
				path: 'M9 14L4 9l5-5M4 9h10a6 6 0 010 12h-3',
				run: (e) => e.chain().focus().undo().run(),
				enabled: (e) => e.can().undo()
			},
			{
				label: 'Ripeti',
				path: 'M15 14l5-5-5-5M20 9H10a6 6 0 000 12h3',
				run: (e) => e.chain().focus().redo().run(),
				enabled: (e) => e.can().redo()
			}
		]
	];
</script>

<div
	role="toolbar"
	aria-label="Formattazione del testo"
	class={[
		'flex w-fit max-w-full items-center gap-1 overflow-x-auto rounded-[8px] border border-(color:--tool-border) bg-(--tool-bg) p-1.5 shadow-(--card-shadow) max-[899px]:gap-0',
		className
	]}
>
	{#each groups as group, index (index)}
		{#if index > 0}
			<span aria-hidden="true" class="mx-1 h-6 w-px flex-none bg-(--tool-border) max-[899px]:mx-0.5"
			></span>
		{/if}
		{#each group as tool (tool.label)}
			<ToggleButton
				label={tool.label}
				pressed={tool.active === undefined || editor === null
					? undefined
					: touched >= 0 && tool.active(editor)}
				disabled={editor === null ||
					(tool.enabled !== undefined && touched >= 0 && !tool.enabled(editor))}
				onclick={() => {
					if (editor !== null) {
						tool.run(editor);
					}
				}}
			>
				{#if tool.glyph !== undefined}
					<span class={tool.glyphClass}>{tool.glyph}</span>
				{:else if tool.path !== undefined}
					<Icon size={20} strokeWidth={1.8}>
						<path d={tool.path} />
					</Icon>
				{/if}
			</ToggleButton>
		{/each}
	{/each}
</div>

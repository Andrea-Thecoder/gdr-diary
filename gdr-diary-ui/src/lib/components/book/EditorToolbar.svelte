<script lang="ts">
	import type { Editor } from '@tiptap/core';
	import { isTextColor, TEXT_COLORS, type TextColor } from '#lib/book/text-color.js';
	import Icon from '#lib/components/ui/Icon.svelte';
	import ToggleButton from '#lib/components/ui/ToggleButton.svelte';

	interface Props {
		editor: Editor | null;
		touched: number;
		class?: string;
	}

	let { editor, touched, class: className = '' }: Props = $props();

	const colorsId = $props.id();

	let colorsOpen = $state(false);

	const currentColor = $derived.by<TextColor | null>(() => {
		if (editor === null || touched < 0) {
			return null;
		}
		const color: unknown = editor.getAttributes('textColor')['color'];
		return isTextColor(color) ? color : null;
	});

	function pickColor(editor: Editor, color: TextColor): void {
		if (editor.isActive('textColor', { color })) {
			editor.chain().focus().unsetTextColor().run();
		} else {
			editor.chain().focus().setTextColor(color).run();
		}
	}

	interface Tool {
		label: string;
		glyph?: string;
		glyphClass?: string;
		expands?: boolean;
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
			},
			{
				label: 'Colore del testo',
				expands: true,
				run: () => {
					colorsOpen = !colorsOpen;
				},
				active: (e) => e.isActive('textColor')
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
		'w-fit max-w-full rounded-[8px] border border-(color:--tool-border) bg-(--tool-bg) p-1.5 shadow-(--card-shadow)',
		className
	]}
>
	<div class="flex items-center gap-1 overflow-x-auto max-[899px]:gap-0">
		{#each groups as group, index (index)}
			{#if index > 0}
				<span
					aria-hidden="true"
					class="mx-1 h-6 w-px flex-none bg-(--tool-border) max-[899px]:mx-0.5"
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
					aria-expanded={tool.expands === true ? colorsOpen : undefined}
					aria-controls={tool.expands === true ? colorsId : undefined}
					onclick={() => {
						if (editor !== null) {
							tool.run(editor);
						}
					}}
				>
					{#if tool.expands === true}
						<span class="flex flex-col items-center gap-px">
							<Icon size={20} strokeWidth={1.8}>
								<circle cx="13.5" cy="6.5" r="0.6" />
								<circle cx="17.5" cy="10.5" r="0.6" />
								<circle cx="8.5" cy="7.5" r="0.6" />
								<circle cx="6.5" cy="12.5" r="0.6" />
								<path
									d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.93 0 1.65-.75 1.65-1.69 0-.44-.18-.84-.44-1.13-.29-.29-.44-.65-.44-1.12a1.64 1.64 0 0 1 1.67-1.67h2c3.05 0 5.55-2.5 5.55-5.55C21.97 6.01 17.46 2 12 2z"
								/>
							</Icon>
							<span
								aria-hidden="true"
								class="h-[3px] w-5 rounded-full"
								style:background={currentColor === null
									? 'currentColor'
									: `var(--ink-${currentColor})`}
							></span>
						</span>
					{:else if tool.glyph !== undefined}
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
	{#if colorsOpen}
		<div
			id={colorsId}
			role="group"
			aria-label="Scegli il colore"
			class="mt-1.5 flex flex-wrap items-center gap-1 border-t border-(--tool-border) pt-1.5"
		>
			<ToggleButton
				label="Colore automatico"
				pressed={editor !== null && touched >= 0 && currentColor === null}
				disabled={editor === null}
				onclick={() => {
					editor?.chain().focus().unsetTextColor().run();
				}}
			>
				<Icon size={20} strokeWidth={1.8}>
					<circle cx="12" cy="12" r="7" />
					<path d="M7 17L17 7" />
				</Icon>
			</ToggleButton>
			{#each TEXT_COLORS as color (color.key)}
				<ToggleButton
					label={color.label}
					pressed={editor !== null && touched >= 0 && currentColor === color.key}
					disabled={editor === null}
					onclick={() => {
						if (editor !== null) {
							pickColor(editor, color.key);
						}
					}}
				>
					<span
						aria-hidden="true"
						class="size-6 rounded-full border border-(--tool-border)"
						style:background={`var(--ink-${color.key})`}
					></span>
				</ToggleButton>
			{/each}
		</div>
	{/if}
</div>

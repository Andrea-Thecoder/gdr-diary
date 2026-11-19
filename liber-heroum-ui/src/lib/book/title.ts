import { Mark, mergeAttributes, type JSONContent } from '@tiptap/core';

export type TitleLevel = 1 | 2;

declare module '@tiptap/core' {
	interface Commands<ReturnType> {
		title: {
			toggleTitle: (level: TitleLevel) => ReturnType;
		};
	}
}

function levelOf(element: HTMLElement): TitleLevel {
	return element.getAttribute('data-title') === '2' ? 2 : 1;
}

export const Title = Mark.create({
	name: 'title',

	addAttributes() {
		return {
			level: {
				default: 1,
				parseHTML: (element: HTMLElement) => levelOf(element),
				renderHTML: (attributes: Record<string, unknown>) => ({
					'data-title': attributes['level'] === 2 ? '2' : '1'
				})
			}
		};
	},

	parseHTML() {
		return [{ tag: 'span[data-title]' }];
	},

	renderHTML({ HTMLAttributes }) {
		return ['span', mergeAttributes(HTMLAttributes), 0];
	},

	addCommands() {
		return {
			toggleTitle:
				(level) =>
				({ commands, editor }) =>
					editor.isActive('title', { level })
						? commands.unsetMark('title')
						: commands.setMark('title', { level })
		};
	},

	addKeyboardShortcuts() {
		return {
			'Mod-Alt-1': () => this.editor.commands.toggleTitle(1),
			'Mod-Alt-2': () => this.editor.commands.toggleTitle(2)
		};
	}
});

export function titlesFromHeadings(node: JSONContent): JSONContent {
	const content = node.content?.map((child) => titlesFromHeadings(child));
	if (node.type !== 'heading') {
		return content === undefined ? node : { ...node, content };
	}
	const level = node.attrs?.['level'] === 2 ? 2 : 1;
	const align: unknown = node.attrs?.['textAlign'];
	return {
		type: 'paragraph',
		...(typeof align === 'string' ? { attrs: { textAlign: align } } : {}),
		content: (content ?? []).map((child) =>
			child.type === 'text'
				? { ...child, marks: [...(child.marks ?? []), { type: 'title', attrs: { level } }] }
				: child
		)
	};
}

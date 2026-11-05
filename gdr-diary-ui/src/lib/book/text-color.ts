import { Mark, mergeAttributes } from '@tiptap/core';

export const TEXT_COLORS = [
	{ key: 'red', label: 'Rosso' },
	{ key: 'orange', label: 'Arancione' },
	{ key: 'green', label: 'Verde' },
	{ key: 'teal', label: 'Turchese' },
	{ key: 'blue', label: 'Blu' },
	{ key: 'purple', label: 'Viola' },
	{ key: 'magenta', label: 'Magenta' },
	{ key: 'brown', label: 'Marrone' }
] as const;

export type TextColor = (typeof TEXT_COLORS)[number]['key'];

export function isTextColor(value: unknown): value is TextColor {
	return TEXT_COLORS.some((color) => color.key === value);
}

declare module '@tiptap/core' {
	interface Commands<ReturnType> {
		textColor: {
			setTextColor: (color: TextColor) => ReturnType;
			unsetTextColor: () => ReturnType;
		};
	}
}

export const TextColorMark = Mark.create({
	name: 'textColor',

	addAttributes() {
		return {
			color: {
				default: null,
				parseHTML: (element: HTMLElement): TextColor | null => {
					const value = element.getAttribute('data-color');
					return isTextColor(value) ? value : null;
				},
				renderHTML: (attributes: Record<string, unknown>) =>
					isTextColor(attributes['color']) ? { 'data-color': attributes['color'] } : {}
			}
		};
	},

	parseHTML() {
		return [
			{
				tag: 'span[data-color]',
				getAttrs: (node) =>
					node instanceof HTMLElement && isTextColor(node.getAttribute('data-color')) ? null : false
			}
		];
	},

	renderHTML({ HTMLAttributes }) {
		return ['span', mergeAttributes(HTMLAttributes), 0];
	},

	addCommands() {
		return {
			setTextColor:
				(color) =>
				({ commands }) =>
					commands.setMark('textColor', { color }),
			unsetTextColor:
				() =>
				({ commands }) =>
					commands.unsetMark('textColor')
		};
	}
});

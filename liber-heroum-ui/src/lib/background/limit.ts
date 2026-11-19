import { Extension } from '@tiptap/core';
import type { Node as ProseNode } from '@tiptap/pm/model';
import { Plugin, PluginKey } from '@tiptap/pm/state';

export interface TextLimitOptions {
	max: number;
	onRefuse: () => void;
}

function codePoints(text: string): number {
	let count = 0;
	for (let index = 0; index < text.length; index += 1) {
		const unit = text.charCodeAt(index);
		if (unit < 0xdc00 || unit > 0xdfff) {
			count += 1;
		}
	}
	return count;
}

export function docTextLength(doc: ProseNode): number {
	let total = 0;
	doc.descendants((node) => {
		if (node.isText) {
			total += codePoints(node.text ?? '');
		}
	});
	return total;
}

export function exceedsLimit(before: number, after: number, max: number): boolean {
	return after > max && after > before;
}

export const TextLimit = Extension.create<TextLimitOptions>({
	name: 'textLimit',

	addOptions() {
		return {
			max: Number.POSITIVE_INFINITY,
			onRefuse: (): void => undefined
		};
	},

	addProseMirrorPlugins() {
		const { max, onRefuse } = this.options;
		return [
			new Plugin({
				key: new PluginKey('textLimit'),
				filterTransaction: (transaction, state) => {
					if (!transaction.docChanged) {
						return true;
					}
					if (exceedsLimit(docTextLength(state.doc), docTextLength(transaction.doc), max)) {
						onRefuse();
						return false;
					}
					return true;
				}
			})
		];
	}
});

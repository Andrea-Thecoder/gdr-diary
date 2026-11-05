import type { Attributes, Extensions } from '@tiptap/core';
import { OrderedList } from '@tiptap/extension-list';
import StarterKit from '@tiptap/starter-kit';
import TextAlign from '@tiptap/extension-text-align';
import { TextColorMark } from './text-color.js';
import { Title } from './title.js';

const ORDERED_LIST_TYPES: readonly string[] = ['1', 'a', 'A', 'i', 'I'];

const DiaryOrderedList = OrderedList.extend({
	addAttributes() {
		const attributes: Attributes = this.parent?.() ?? {};
		const original = attributes['type'];
		return {
			...attributes,
			type: {
				default: null,
				parseHTML: (element: HTMLElement): string | null => {
					const value: unknown = original?.parseHTML?.(element);
					return typeof value === 'string' && ORDERED_LIST_TYPES.includes(value) ? value : null;
				}
			}
		};
	}
});

export function diaryExtensions(): Extensions {
	return [
		StarterKit.configure({
			heading: false,
			code: false,
			codeBlock: false,
			strike: false,
			link: false,
			trailingNode: false,
			dropcursor: false,
			orderedList: false
		}),
		DiaryOrderedList,
		TextAlign.configure({ types: ['paragraph'] }),
		Title,
		TextColorMark
	];
}

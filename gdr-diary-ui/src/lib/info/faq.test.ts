import { describe, expect, it } from 'vitest';
import { FAQ_GROUPS, filterFaq, type FaqGroup } from './faq.js';

const groups: FaqGroup[] = [
	{
		id: 'a',
		title: 'A',
		items: [
			{ question: 'Posso usarlo da telefono?', answer: 'Sì, non serve installare nulla.' },
			{ question: 'Devo pagare?', answer: 'No, è gratis.' }
		]
	},
	{ id: 'b', title: 'B', items: [{ question: 'Come elimino l’account?', answer: 'Dal profilo.' }] }
];

describe('filterFaq', () => {
	it('keeps every group when the search is empty', () => {
		expect(filterFaq(groups, '')).toEqual(groups);
	});

	it('keeps every group when the search is only spaces', () => {
		expect(filterFaq(groups, '   ')).toEqual(groups);
	});

	it('finds a word in the question', () => {
		expect(filterFaq(groups, 'telefono').map((group) => group.id)).toEqual(['a']);
	});

	it('finds a word in the answer', () => {
		expect(filterFaq(groups, 'profilo').map((group) => group.id)).toEqual(['b']);
	});

	it('ignores capitals and accents', () => {
		expect(filterFaq(groups, 'SI').map((group) => group.id)).toEqual(['a']);
	});

	it('keeps only the matching questions of a group', () => {
		expect(filterFaq(groups, 'gratis')[0]?.items).toEqual([
			{ question: 'Devo pagare?', answer: 'No, è gratis.' }
		]);
	});

	it('returns nothing when no question matches', () => {
		expect(filterFaq(groups, 'astronave')).toEqual([]);
	});

	it('does not change the original groups', () => {
		filterFaq(groups, 'gratis');

		expect(groups[0]?.items).toHaveLength(2);
	});
});

describe('FAQ_GROUPS', () => {
	it('has unique group ids, usable as anchors', () => {
		const ids = FAQ_GROUPS.map((group) => group.id);

		expect(new Set(ids).size).toBe(ids.length);
	});

	it('has at least one question in every group', () => {
		expect(FAQ_GROUPS.map((group) => group.items.length > 0)).not.toContain(false);
	});
});

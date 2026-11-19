import { describe, expect, it } from 'vitest';
import { cellLabel, parsePlans, PLAN_GROUPS, PLAN_NOTE, PLANS } from './plans.js';

describe('cellLabel', () => {
	it('says a feature is included', () => {
		expect(cellLabel({ kind: 'yes' })).toBe('Incluso');
	});

	it('says a feature is not included', () => {
		expect(cellLabel({ kind: 'no' })).toBe('Non incluso');
	});

	it('gives back the limit written in the cell', () => {
		expect(cellLabel({ kind: 'text', text: '100 MB' })).toBe('100 MB');
	});
});

describe('plans', () => {
	it('has a free plan that is live and a premium plan that is coming soon', () => {
		expect(PLANS.map((plan) => [plan.id, plan.soon])).toEqual([
			['free', false],
			['premium', true]
		]);
	});

	it('has four points for each plan', () => {
		expect(PLANS.map((plan) => plan.points.length)).toEqual([4, 4]);
	});

	it('has rows in every group of the table', () => {
		expect(PLAN_GROUPS.map((group) => group.rows.length > 0)).not.toContain(false);
	});
});

const VALID_PLAN = {
	id: 'free',
	kind: 'k',
	name: 'n',
	price: '0',
	per: '',
	text: 't',
	points: ['a'],
	soon: false
};

function withRow(row: unknown): unknown {
	return { plans: [VALID_PLAN], groups: [{ title: 'g', rows: [row] }] };
}

describe('parsePlans', () => {
	it('reads true as an included feature', () => {
		const parsed = parsePlans(withRow({ label: 'x', free: true, premium: true }));
		expect(parsed.groups[0]?.rows[0]?.free).toEqual({ kind: 'yes' });
	});

	it('reads false as a feature that is not included', () => {
		const parsed = parsePlans(withRow({ label: 'x', free: false, premium: true }));
		expect(parsed.groups[0]?.rows[0]?.free).toEqual({ kind: 'no' });
	});

	it('reads a string as the limit written in the cell', () => {
		const parsed = parsePlans(withRow({ label: 'x', free: '3', premium: 'Molte' }));
		expect(parsed.groups[0]?.rows[0]?.premium).toEqual({ kind: 'text', text: 'Molte' });
	});

	it('treats a row as available now when soon is missing', () => {
		const parsed = parsePlans(withRow({ label: 'x', free: true, premium: true }));
		expect(parsed.groups[0]?.rows[0]?.soon).toBe(false);
	});

	it('keeps soon when the row says it', () => {
		const parsed = parsePlans(withRow({ label: 'x', free: true, premium: true, soon: true }));
		expect(parsed.groups[0]?.rows[0]?.soon).toBe(true);
	});

	it('says where a cell is missing', () => {
		expect(() => parsePlans(withRow({ label: 'x', free: true }))).toThrow(
			'plans.json: groups[0].rows[0].premium deve essere un testo'
		);
	});

	it('says where a label is missing', () => {
		expect(() => parsePlans(withRow({ free: true, premium: true }))).toThrow(
			'plans.json: groups[0].rows[0].label deve essere un testo'
		);
	});

	it('refuses a plan id that the page does not know', () => {
		expect(() => parsePlans({ plans: [{ ...VALID_PLAN, id: 'gold' }], groups: [] })).toThrow(
			'plans.json: plans[0].id deve essere uno tra free, premium'
		);
	});

	it('refuses a list of points that is not a list', () => {
		expect(() => parsePlans({ plans: [{ ...VALID_PLAN, points: 'a' }], groups: [] })).toThrow(
			'plans.json: plans[0].points deve essere una lista'
		);
	});

	it('reads the note under the table', () => {
		const parsed = parsePlans({
			...(withRow({ label: 'x', free: true, premium: true }) as object),
			note: '* Nota'
		});
		expect(parsed.note).toBe('* Nota');
	});

	it('has no note when the file does not give one', () => {
		expect(parsePlans(withRow({ label: 'x', free: true, premium: true })).note).toBe('');
	});

	it('refuses a note that is not a text', () => {
		expect(() => parsePlans({ plans: [], groups: [], note: 3 })).toThrow(
			'plans.json: note deve essere un testo'
		);
	});

	it('refuses a file that is not an object', () => {
		expect(() => parsePlans([])).toThrow('plans.json: il file deve essere un oggetto');
	});
});

describe('plans.json', () => {
	it('gives a value for both plans in every row of the table', () => {
		const cells = PLAN_GROUPS.flatMap((group) =>
			group.rows.flatMap((row) => [row.free, row.premium])
		);
		expect(cells.map((cell) => typeof cell.kind)).not.toContain('undefined');
	});

	it('explains the asterisk used in the table', () => {
		const labels = PLAN_GROUPS.flatMap((group) => group.rows.map((row) => row.label));
		expect(labels).toContain('Personaggi per account*');
		expect(PLAN_NOTE).toBe(
			'* Per personaggi si intende una combinazione di personaggi e/o master.'
		);
	});
});

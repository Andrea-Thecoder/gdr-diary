import data from './plans.json';

export type PlanCell = { kind: 'yes' } | { kind: 'no' } | { kind: 'text'; text: string };

export type PlanId = 'free' | 'premium';

export interface PlanRow {
	label: string;
	free: PlanCell;
	premium: PlanCell;
	soon: boolean;
}

export interface PlanGroup {
	title: string;
	rows: readonly PlanRow[];
}

export interface Plan {
	id: PlanId;
	kind: string;
	name: string;
	price: string;
	per: string;
	text: string;
	points: readonly string[];
	soon: boolean;
}

export interface PlansData {
	plans: readonly Plan[];
	groups: readonly PlanGroup[];
	note: string;
}

const PLAN_IDS: readonly string[] = ['free', 'premium'];

function fail(path: string, expected: string): never {
	throw new Error(`plans.json: ${path} deve essere ${expected}`);
}

type Raw<K extends string> = Partial<Record<K, unknown>>;

function asRecord<K extends string>(value: unknown, path: string): Raw<K> {
	if (typeof value !== 'object' || value === null || Array.isArray(value)) {
		return fail(path, 'un oggetto');
	}
	return value;
}

function asArray(value: unknown, path: string): readonly unknown[] {
	if (!Array.isArray(value)) {
		return fail(path, 'una lista');
	}
	return value as readonly unknown[];
}

function asString(value: unknown, path: string): string {
	if (typeof value !== 'string') {
		return fail(path, 'un testo');
	}
	return value;
}

function asBoolean(value: unknown, path: string): boolean {
	if (typeof value !== 'boolean') {
		return fail(path, 'vero o falso');
	}
	return value;
}

function optionalBoolean(value: unknown, path: string): boolean {
	return value === undefined ? false : asBoolean(value, path);
}

function optionalString(value: unknown, path: string): string {
	return value === undefined ? '' : asString(value, path);
}

function parseCell(value: unknown, path: string): PlanCell {
	if (value === true) {
		return { kind: 'yes' };
	}
	if (value === false) {
		return { kind: 'no' };
	}
	return { kind: 'text', text: asString(value, path) };
}

function parsePlanId(value: unknown, path: string): PlanId {
	const id = asString(value, path);
	if (!PLAN_IDS.includes(id)) {
		return fail(path, `uno tra ${PLAN_IDS.join(', ')}`);
	}
	return id as PlanId;
}

function parsePlan(value: unknown, index: number): Plan {
	const path = `plans[${index}]`;
	const plan = asRecord<keyof Plan>(value, path);
	return {
		id: parsePlanId(plan.id, `${path}.id`),
		kind: asString(plan.kind, `${path}.kind`),
		name: asString(plan.name, `${path}.name`),
		price: asString(plan.price, `${path}.price`),
		per: asString(plan.per, `${path}.per`),
		text: asString(plan.text, `${path}.text`),
		points: asArray(plan.points, `${path}.points`).map((point, i) =>
			asString(point, `${path}.points[${i}]`)
		),
		soon: asBoolean(plan.soon, `${path}.soon`)
	};
}

function parseRow(value: unknown, path: string): PlanRow {
	const row = asRecord<keyof PlanRow>(value, path);
	return {
		label: asString(row.label, `${path}.label`),
		free: parseCell(row.free, `${path}.free`),
		premium: parseCell(row.premium, `${path}.premium`),
		soon: optionalBoolean(row.soon, `${path}.soon`)
	};
}

function parseGroup(value: unknown, index: number): PlanGroup {
	const path = `groups[${index}]`;
	const group = asRecord<keyof PlanGroup>(value, path);
	return {
		title: asString(group.title, `${path}.title`),
		rows: asArray(group.rows, `${path}.rows`).map((row, i) => parseRow(row, `${path}.rows[${i}]`))
	};
}

export function parsePlans(raw: unknown): PlansData {
	const root = asRecord<keyof PlansData>(raw, 'il file');
	return {
		plans: asArray(root.plans, 'plans').map(parsePlan),
		groups: asArray(root.groups, 'groups').map(parseGroup),
		note: optionalString(root.note, 'note')
	};
}

const PARSED: PlansData = parsePlans(data);

export const PLANS: readonly Plan[] = PARSED.plans;

export const PLAN_GROUPS: readonly PlanGroup[] = PARSED.groups;

export const PLAN_NOTE: string = PARSED.note;

export function cellLabel(cell: PlanCell): string {
	switch (cell.kind) {
		case 'yes':
			return 'Incluso';
		case 'no':
			return 'Non incluso';
		case 'text':
			return cell.text;
	}
}

export interface ChoiceOption {
	value: string;
	label: string;
}

const INHERIT: ChoiceOption = { value: '', label: 'Come le impostazioni' };

export const FONTS: readonly ChoiceOption[] = [
	{ value: 'EB Garamond', label: 'EB Garamond' },
	{ value: 'Caveat', label: 'Caveat (a mano)' },
	{ value: 'Georgia', label: 'Georgia' }
];

const SIZES = [12, 14, 16, 18, 20, 24];

export function fontOptions(current: string): readonly ChoiceOption[] {
	const known = FONTS.some((font) => font.value === current);
	return [
		INHERIT,
		...FONTS,
		...(current === '' || known ? [] : [{ value: current, label: current }])
	];
}

export function sizeOptions(current: string): readonly ChoiceOption[] {
	const options = SIZES.map((size) => ({ value: String(size), label: `${String(size)} pt` }));
	const known = options.some((size) => size.value === current);
	return [
		INHERIT,
		...options,
		...(current === '' || known ? [] : [{ value: current, label: `${current} pt` }])
	];
}

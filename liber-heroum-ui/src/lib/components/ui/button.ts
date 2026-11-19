export type ButtonVariant = 'solid' | 'outline' | 'danger' | 'dangerOutline';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'block' | 'form';

const base =
	'inline-flex cursor-pointer items-center justify-center gap-3 rounded-[4px] border font-label uppercase hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60';

const solid =
	'border-(color:--btn-border) font-semibold text-btn-ink shadow-(--btn-shadow) surface-btn';

const solidSizes: Record<ButtonSize, string> = {
	sm: 'min-h-[44px] px-5 text-[13px] tracking-[.14em]',
	md: 'min-h-[54px] px-8 text-[15px] tracking-[.12em]',
	lg: 'min-h-[56px] px-9 text-[15px] tracking-[.12em]',
	block: 'min-h-[50px] w-full px-5 text-[14px] tracking-[.12em]',
	form: 'min-h-[52px] px-7 text-[14px] tracking-[.12em]'
};

const outline = 'border-(color:--nav-border) font-medium text-nav-ink';

const outlineSizes: Record<ButtonSize, string> = {
	sm: 'min-h-[44px] px-5 text-[13px] tracking-[.14em]',
	md: 'min-h-[44px] px-5 text-[13px] tracking-[.14em]',
	lg: 'min-h-[44px] px-5 text-[13px] tracking-[.14em]',
	block: 'min-h-[50px] w-full px-5 text-[14px] tracking-[.12em]',
	form: 'min-h-[52px] px-6 text-[14px] tracking-[.12em]'
};

const danger = 'border-(color:--danger) bg-danger font-semibold text-danger-ink';

const dangerOutline = 'border-(color:--danger) font-medium text-danger';

export function buttonClasses(variant: ButtonVariant, size: ButtonSize): string {
	switch (variant) {
		case 'solid':
			return `${base} ${solid} ${solidSizes[size]}`;
		case 'outline':
			return `${base} ${outline} ${outlineSizes[size]}`;
		case 'danger':
			return `${base} ${danger} ${outlineSizes[size]}`;
		case 'dangerOutline':
			return `${base} ${dangerOutline} ${outlineSizes[size]}`;
	}
}

export const cardLinkClasses = `${buttonClasses('solid', 'block')} no-underline after:absolute after:inset-0 after:rounded-[6px]`;

export const backLinkClasses =
	'inline-flex min-h-[44px] items-center gap-2 text-[18px] text-soft no-underline hover:opacity-80';

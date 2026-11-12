<script lang="ts">
	import type { Snippet } from 'svelte';
	import { untrack } from 'svelte';

	interface Props {
		from: number;
		to: number;
		front: Snippet;
		back: Snippet;
		onfinish: () => void;
		class?: string;
	}

	let { from, to, front, back, onfinish, class: className = '' }: Props = $props();

	const forward = untrack(() => to < from);
	const direction = forward ? '[animation-direction:normal]' : '[animation-direction:reverse]';
	const shade =
		'linear-gradient(90deg, color-mix(in srgb, var(--page-ink) 42%, transparent), color-mix(in srgb, var(--page-ink) 8%, transparent) 55%, color-mix(in srgb, white 30%, transparent) 85%, transparent)';
	const shadeBack =
		'linear-gradient(270deg, color-mix(in srgb, var(--page-ink) 42%, transparent), color-mix(in srgb, var(--page-ink) 8%, transparent) 55%, color-mix(in srgb, white 30%, transparent) 85%, transparent)';
</script>

<div
	class={['pointer-events-none absolute inset-0 z-20 [perspective:2600px]', className]}
	aria-hidden="true"
>
	<div
		class={[
			'absolute inset-0 leaf-cast-under bg-linear-to-r from-[color-mix(in_srgb,var(--page-ink)_45%,transparent)] via-[color-mix(in_srgb,var(--page-ink)_14%,transparent)] to-transparent to-70%',
			direction
		]}
	></div>
	<div
		class={[
			'absolute inset-y-0 right-full w-full leaf-cast-side bg-linear-to-l from-[color-mix(in_srgb,var(--page-ink)_50%,transparent)] via-[color-mix(in_srgb,var(--page-ink)_16%,transparent)] to-transparent to-75%',
			direction
		]}
	></div>
	<div
		class="absolute inset-0 origin-left leaf-swing will-change-transform [transform-style:preserve-3d]"
		style:--from={String(from)}
		style:--to={String(to)}
		onanimationend={(event) => {
			if (event.target === event.currentTarget && event.animationName === 'leaf-swing') {
				onfinish();
			}
		}}
	>
		<div class="absolute inset-0 shadow-(--page-shadow) [backface-visibility:hidden]">
			{@render front()}
			<div
				class={['pointer-events-none absolute inset-0 leaf-dim-front', direction]}
				style:background={shade}
			></div>
		</div>
		<div
			class="absolute inset-0 [transform:rotateY(180deg)] shadow-(--page-shadow) [backface-visibility:hidden]"
		>
			{@render back()}
			<div
				class={['pointer-events-none absolute inset-0 leaf-dim-back', direction]}
				style:background={shadeBack}
			></div>
		</div>
	</div>
</div>

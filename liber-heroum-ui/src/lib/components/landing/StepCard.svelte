<script lang="ts">
	import type { Snippet } from 'svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';

	interface Props {
		numeral: string;
		title: string;
		text: string;
		mockLabel: string;
		padded?: boolean;
		mock: Snippet;
	}

	let { numeral, title, text, mockLabel, padded = false, mock }: Props = $props();
</script>

<article
	class="min-w-0 flex-[1_1_280px] overflow-hidden rounded-[6px] border border-(color:--card-border) shadow-(--card-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
>
	<div
		role="img"
		aria-label={mockLabel}
		class={[
			'relative flex h-[196px] items-center justify-center surface-mock',
			padded && 'px-[22px]'
		]}
	>
		<div
			class="absolute top-4 left-4 flex size-[34px] items-center justify-center rounded-full border border-(color:--badge-border) font-label text-[15px] font-semibold text-badge-ink"
		>
			{numeral}
		</div>
		{@render mock()}
	</div>
	<div class="px-7 pt-7 pb-[34px]">
		<SubHeading class="mb-2.5">{title}</SubHeading>
		<p class="m-0 text-soft">{text}</p>
	</div>
</article>

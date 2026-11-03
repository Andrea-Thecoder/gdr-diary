<script lang="ts">
	import { COVER_HEIGHT, COVER_WIDTH, coverTints, titleSize } from '#lib/diaries/appearance.js';

	interface Props {
		title: string;
		color: string | null | undefined;
		coverUrl: string | null | undefined;
		size?: 'card' | 'preview' | 'thumb';
	}

	let { title, color, coverUrl, size = 'card' }: Props = $props();

	const WIDTHS = { card: COVER_WIDTH, preview: 96, thumb: 58 };

	const tints = $derived(coverTints(color));
	const scale = $derived(WIDTHS[size] / COVER_WIDTH);
	const hasCover = $derived(coverUrl !== null && coverUrl !== undefined && coverUrl !== '');
</script>

<div
	role="img"
	aria-label={`Copertina di ${title}: diario chiuso`}
	class={[
		'relative flex-none',
		size === 'card' &&
			'transition-[translate,rotate] duration-250 group-hover:-translate-y-1 group-hover:-rotate-[1.5deg] motion-reduce:transition-none motion-reduce:group-hover:transform-none'
	]}
	style:width={`${String(COVER_WIDTH * scale)}px`}
	style:height={`${String(COVER_HEIGHT * scale)}px`}
>
	<div
		class="absolute top-0 left-0 origin-top-left"
		style:width={`${String(COVER_WIDTH)}px`}
		style:height={`${String(COVER_HEIGHT)}px`}
		style:transform={size === 'card' ? undefined : `scale(${String(scale)})`}
	>
		<div
			aria-hidden="true"
			class="absolute top-[7px] right-[-7px] bottom-[-7px] left-[10px] rounded-[3px_6px_6px_3px] shadow-(--closed-pages-shadow)"
			style:background="var(--closed-pages)"
		></div>
		<div
			aria-hidden="true"
			class="absolute inset-0 overflow-hidden rounded-[3px_7px_7px_3px] shadow-(--closed-cover-shadow)"
			style:background={`linear-gradient(135deg, ${tints.light}, ${tints.dark})`}
		>
			{#if hasCover}
				<img src={coverUrl} alt="" class="absolute inset-0 size-full object-cover" />
			{:else}
				<div
					class="absolute top-3.5 right-3.5 bottom-3.5 left-8 flex flex-col items-center justify-center rounded-[4px] border-2 border-gold-frame-strong px-2.5 pb-6"
				>
					<span
						class="line-clamp-4 text-center font-display leading-[1.08] font-bold [overflow-wrap:anywhere] text-gold-ink [text-shadow:var(--closed-title-shadow)]"
						style:font-size={`${String(titleSize(title))}px`}
					>
						{title}
					</span>
					<span class="absolute bottom-3 flex gap-2">
						{#each [0, 1, 2] as diamond (diamond)}
							<span class="block size-1.5 rotate-45 bg-gold-dot"></span>
						{/each}
					</span>
				</div>
			{/if}
			<div class="absolute inset-y-0 left-0 w-5" style:background="var(--closed-spine)"></div>
			<div class="absolute inset-y-2 left-[17px] border-l border-dashed border-gold-dash"></div>
			<div class="absolute inset-0" style:background="var(--closed-gloss)"></div>
		</div>
	</div>
</div>

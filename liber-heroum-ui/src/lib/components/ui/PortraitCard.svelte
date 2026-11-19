<script lang="ts">
	import type { Snippet } from 'svelte';
	import SubHeading from './SubHeading.svelte';

	interface Props {
		title: string;
		glow?: string;
		clampTitle?: boolean;
		lift?: boolean;
		photo: Snippet;
		subtitle?: Snippet;
		action: Snippet;
	}

	let { title, glow, clampTitle = false, lift = true, photo, subtitle, action }: Props = $props();
</script>

<article
	style:--card-glow={glow}
	class={[
		'pcard group relative flex h-full min-w-0 flex-col overflow-hidden rounded-[6px] border border-(color:--card-border) shadow-(--pcard-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card',
		lift &&
			'transition-[transform,box-shadow] duration-250 hover:-translate-y-2 hover:shadow-[0_30px_48px_-16px_var(--card-glow,var(--accent))] motion-reduce:transition-none motion-reduce:hover:translate-y-0'
	]}
>
	<div class="relative">
		{@render photo()}
	</div>
	<div class="flex flex-1 flex-col px-6 pt-[22px] pb-[26px]">
		<SubHeading as="h2" class={clampTitle ? 'line-clamp-2 break-words' : 'break-words'}
			>{title}</SubHeading
		>
		{#if subtitle !== undefined}
			{@render subtitle()}
		{/if}
		<div class="mt-auto pt-5">
			{@render action()}
		</div>
	</div>
</article>

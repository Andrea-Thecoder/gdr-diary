<script lang="ts">
	import type { Snippet } from 'svelte';
	import SubHeading from './SubHeading.svelte';

	interface Props {
		title: string;
		intro?: string;
		action?: Snippet;
		class?: string;
		children: Snippet;
	}

	let { title, intro, action, class: className = '', children }: Props = $props();

	const titleId = $props.id();
</script>

<section
	aria-labelledby={titleId}
	class={[
		'rounded-[8px] border border-(color:--card-border) p-[clamp(20px,3vw,32px)] outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card',
		className
	]}
>
	<div class="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
		<div class="min-w-0">
			<SubHeading as="h2" id={titleId}>{title}</SubHeading>
			{#if intro !== undefined}
				<p class="mx-0 mt-2 mb-0 text-[18px] text-soft">{intro}</p>
			{/if}
		</div>
		{#if action !== undefined}
			<div class="flex-none">
				{@render action()}
			</div>
		{/if}
	</div>
	<div class="mt-6">
		{@render children()}
	</div>
</section>

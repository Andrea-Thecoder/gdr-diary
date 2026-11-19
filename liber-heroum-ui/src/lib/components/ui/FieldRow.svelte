<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		label: string;
		help?: string;
		labelId?: string;
		class?: string;
		children: Snippet;
	}

	let { label, help, labelId, class: className = '', children }: Props = $props();
</script>

<div
	class={[
		'grid grid-cols-[repeat(auto-fit,minmax(min(260px,100%),1fr))] gap-x-8 gap-y-2.5 border-t border-(color:--rule) py-5 first:border-t-0 first:pt-0',
		className
	]}
>
	<div>
		<p
			id={labelId}
			class="m-0 font-label text-[14px] font-semibold tracking-[.12em] text-label uppercase"
		>
			{label}
		</p>
		{#if help !== undefined}
			<p class="mx-0 mt-1.5 mb-0 text-[17px] text-soft">{help}</p>
		{/if}
	</div>
	<div class="min-w-0 self-center">
		{@render children()}
	</div>
</div>

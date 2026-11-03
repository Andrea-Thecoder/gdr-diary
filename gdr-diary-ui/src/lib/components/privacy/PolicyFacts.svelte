<script lang="ts">
	interface Fact {
		term: string;
		detail: string;
	}

	interface Props {
		items: readonly Fact[];
		class?: string;
	}

	let { items, class: className = '' }: Props = $props();
</script>

<dl class={['m-0 flex flex-col', className]}>
	{#each items as item (item.term)}
		<div class="border-t border-(color:--rule) py-5 first:border-t-0 first:pt-0">
			<dt class="font-display text-[26px] leading-[1.1] font-bold text-h3">{item.term}</dt>
			<dd class="m-0 mt-2 [overflow-wrap:anywhere]">{item.detail}</dd>
		</div>
	{/each}
</dl>

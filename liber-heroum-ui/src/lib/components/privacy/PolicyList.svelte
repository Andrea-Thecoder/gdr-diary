<script lang="ts">
	interface Props {
		items: readonly string[];
		class?: string;
	}

	let { items, class: className = '' }: Props = $props();
</script>

<ul class={['m-0 flex list-none flex-col gap-3 p-0', className]}>
	{#each items as item (item)}
		<li class="relative min-w-0 pl-7 [overflow-wrap:anywhere]">
			<span aria-hidden="true" class="absolute top-[0.6em] left-1 size-2 rotate-45 bg-orn"></span>
			{item}
		</li>
	{/each}
</ul>

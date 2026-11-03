<script lang="ts">
	import type { Snippet } from 'svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';

	interface Props {
		id: string;
		title: string;
		class?: string;
		children: Snippet;
	}

	let { id, title, class: className = '', children }: Props = $props();
</script>

<section
	aria-labelledby={id}
	class={['flex scroll-mt-6 flex-col gap-4 border-t border-(color:--rule) pt-8', className]}
>
	<SubHeading as="h2" {id}>{title}</SubHeading>
	{@render children()}
</section>

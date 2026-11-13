<script lang="ts">
	import type { Snippet } from 'svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';

	interface Props {
		title: string;
		children: Snippet;
	}

	let { title, children }: Props = $props();
</script>

<div>
	<Eyebrow class="text-label">{title}</Eyebrow>
	<div class="mt-1 flex flex-col text-[18px]">
		{@render children()}
	</div>
</div>

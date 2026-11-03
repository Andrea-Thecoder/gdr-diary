<script lang="ts">
	import TrashIcon from '#lib/components/ui/TrashIcon.svelte';

	interface Props {
		side: 'left' | 'right';
		label: string;
		onclick: () => void;
	}

	let { side, label, onclick }: Props = $props();
</script>

<button
	type="button"
	aria-label={label}
	title={label}
	{onclick}
	class={[
		'absolute top-0.5 z-10 flex size-[44px] cursor-pointer items-center justify-center rounded-full border-0 bg-transparent p-0 text-(--page-ink) opacity-40 transition-[opacity,color] duration-150 hover:text-danger hover:opacity-100 focus-visible:text-danger focus-visible:opacity-100 motion-reduce:transition-none',
		side === 'left' ? 'left-0.5' : 'right-0.5'
	]}
>
	<TrashIcon size={18} />
</button>

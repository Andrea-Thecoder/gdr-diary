<script lang="ts">
	import TrashIcon from './TrashIcon.svelte';

	interface Props {
		label: string;
		onclick: () => void;
	}

	let { label, onclick }: Props = $props();
</script>

<button
	type="button"
	aria-label={label}
	title={label}
	{onclick}
	class="relative z-10 flex size-[50px] flex-none cursor-pointer items-center justify-center rounded-[4px] border border-(color:--danger) text-danger hover:bg-danger hover:text-danger-ink"
>
	<TrashIcon />
</button>

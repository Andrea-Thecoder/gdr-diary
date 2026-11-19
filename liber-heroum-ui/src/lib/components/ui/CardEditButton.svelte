<script lang="ts">
	import QuillIcon from './QuillIcon.svelte';

	interface Props {
		label: string;
		onclick: () => void;
		class?: string;
	}

	let { label, onclick, class: className = 'top-3 right-3' }: Props = $props();
</script>

<button
	type="button"
	aria-label={label}
	title={label}
	{onclick}
	class={[
		'absolute z-10 flex size-[44px] cursor-pointer items-center justify-center rounded-full border surface-gold-chip hover:opacity-85',
		className
	]}
>
	<QuillIcon size={20} strokeWidth={1.6} />
</button>

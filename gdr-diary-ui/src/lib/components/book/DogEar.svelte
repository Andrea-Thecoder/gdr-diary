<script lang="ts">
	interface Props {
		side: 'left' | 'right';
		label: string;
		onclick: () => void;
		class?: string;
	}

	let { side, label, onclick, class: className = '' }: Props = $props();

	const flap =
		'linear-gradient(135deg, color-mix(in srgb, var(--page) 55%, white), var(--page) 55%, color-mix(in srgb, var(--page-ink) 16%, var(--page)))';
	const shadow = 'drop-shadow(-2px -2px 3px color-mix(in srgb, var(--page-ink) 45%, transparent))';
</script>

<button
	type="button"
	aria-label={label}
	title={label}
	{onclick}
	class={[
		'group absolute bottom-0 z-10 size-[72px] cursor-pointer border-0 bg-transparent p-0',
		side === 'left' ? 'left-0' : 'right-0',
		className
	]}
>
	<span
		aria-hidden="true"
		class={[
			'absolute bottom-0 block size-11 transition-[width,height] duration-200 group-hover:size-16 group-focus-visible:size-16 motion-reduce:transition-none',
			side === 'left' ? 'left-0 -scale-x-100' : 'right-0'
		]}
	>
		<span
			class="absolute inset-0 bg-(--ear-under)"
			style:clip-path="polygon(100% 0, 100% 100%, 0 100%)"
		></span>
		<span
			class="absolute inset-0"
			style:clip-path="polygon(0 0, 100% 0, 0 100%)"
			style:background={flap}
			style:filter={shadow}
		></span>
	</span>
</button>

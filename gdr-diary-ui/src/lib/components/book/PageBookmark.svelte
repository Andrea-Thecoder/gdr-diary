<script lang="ts">
	interface Props {
		side: 'left' | 'right';
		page: number;
		marked: boolean;
		disabled: boolean;
		onclick: () => void;
	}

	let { side, page, marked, disabled, onclick }: Props = $props();
</script>

<button
	type="button"
	aria-label={`Segnalibro, pagina ${String(page)}`}
	aria-pressed={marked}
	title={marked ? 'Togli il segnalibro' : 'Metti il segnalibro'}
	{disabled}
	{onclick}
	class={[
		'group absolute -top-2 z-10 flex h-16 w-11 cursor-pointer justify-center rounded-[2px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-(color:--accent) disabled:cursor-wait',
		side === 'left' ? 'left-3' : 'right-3'
	]}
>
	<span
		aria-hidden="true"
		class={[
			'block h-16 w-5 [clip-path:polygon(0_0,100%_0,100%_100%,50%_78%,0_100%)] surface-ribbon transition-opacity group-hover:opacity-80 group-focus-visible:opacity-100',
			marked ? 'opacity-100' : 'opacity-30'
		]}
	></span>
</button>

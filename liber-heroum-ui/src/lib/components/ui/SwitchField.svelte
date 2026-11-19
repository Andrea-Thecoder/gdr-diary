<script lang="ts">
	interface Props {
		checked: boolean;
		labelledby: string;
		onchange: (checked: boolean) => void;
	}

	let { checked, labelledby, onchange }: Props = $props();
</script>

<div class="flex min-h-[44px] items-center gap-3">
	<button
		type="button"
		role="switch"
		aria-checked={checked}
		aria-labelledby={labelledby}
		onclick={() => {
			onchange(!checked);
		}}
		class={[
			'relative h-8 w-14 flex-none cursor-pointer rounded-full border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-orn',
			checked
				? 'border-(color:--btn-border) shadow-(--btn-shadow) surface-btn'
				: 'border-(--field-border) bg-(--chip-bg)'
		]}
	>
		<span
			aria-hidden="true"
			class={[
				'absolute top-1/2 left-1 size-6 -translate-y-1/2 rounded-full transition-transform motion-reduce:transition-none',
				checked ? 'translate-x-6 bg-btn-ink' : 'bg-soft'
			]}
		></span>
	</button>
	<span class="text-[17px] text-soft">{checked ? 'Attivo' : 'Spento'}</span>
</div>

<script lang="ts">
	interface Props {
		label: string;
		value?: string;
		required?: boolean;
		multiline?: boolean;
		rows?: number;
		type?: 'text' | 'email';
		autocomplete?: 'off' | 'email';
		placeholder?: string;
		maxlength?: number;
		error?: string | null;
		class?: string;
	}

	let {
		label,
		value = $bindable(''),
		required = false,
		multiline = false,
		rows = 3,
		type = 'text',
		autocomplete,
		placeholder = '',
		maxlength,
		error = null,
		class: className = ''
	}: Props = $props();

	const id = $props.id();
	const errorId = `${id}-error`;
	const fieldClass =
		'w-full rounded-[6px] border border-(--field-border) bg-(--field-bg) px-3.5 text-[18px] text-ink placeholder:text-soft/75';
</script>

<div class={className}>
	<label
		for={id}
		class="mb-1.5 block font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
	>
		{label}{required ? ' *' : ''}
	</label>
	{#if multiline}
		<textarea
			{id}
			bind:value
			{placeholder}
			{maxlength}
			{rows}
			aria-invalid={error === null ? undefined : true}
			aria-describedby={error === null ? undefined : errorId}
			class={[fieldClass, 'resize-y py-3']}></textarea>
	{:else}
		<input
			{id}
			{type}
			{autocomplete}
			bind:value
			{required}
			{placeholder}
			{maxlength}
			aria-invalid={error === null ? undefined : true}
			aria-describedby={error === null ? undefined : errorId}
			class={[fieldClass, 'min-h-[50px]']}
		/>
	{/if}
	{#if error !== null}
		<p id={errorId} class="mt-1.5 mb-0 text-base text-label">{error}</p>
	{/if}
</div>

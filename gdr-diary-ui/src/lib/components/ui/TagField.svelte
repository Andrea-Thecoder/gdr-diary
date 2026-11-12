<script lang="ts">
	import TagChip from './TagChip.svelte';

	interface Props {
		label: string;
		tags: readonly string[];
		max: number;
		maxlength: number;
		suggestions?: readonly string[];
		disabled?: boolean;
		placeholder?: string;
		hint?: string | null;
		error?: string | null;
		removeLabel: (tag: string) => string;
		onadd: (text: string) => boolean;
		onremove: (tag: string) => void;
		onedit?: (text: string) => void;
		class?: string;
	}

	let {
		label,
		tags,
		max,
		maxlength,
		suggestions = [],
		disabled = false,
		placeholder = '',
		hint = null,
		error = null,
		removeLabel,
		onadd,
		onremove,
		onedit,
		class: className = ''
	}: Props = $props();

	const id = $props.id();
	const listId = `${id}-list`;
	const noteId = `${id}-note`;

	let text = $state('');

	const full = $derived(tags.length >= max);
	const note = $derived(error ?? hint);

	function commit(): void {
		if (text.trim() !== '' && onadd(text)) {
			text = '';
		}
		onedit?.(text);
	}

	function typed(event: Event): void {
		if (event instanceof InputEvent && event.inputType === 'insertReplacementText') {
			commit();
			return;
		}
		if (text.includes(',')) {
			const parts = text.split(',');
			const rest = parts.pop() ?? '';
			let kept = '';
			for (const part of parts) {
				if (!onadd(part)) {
					kept = part;
					break;
				}
			}
			text = kept === '' ? rest : kept;
		}
		onedit?.(text);
	}

	function pressed(event: KeyboardEvent): void {
		if (event.key === 'Enter') {
			event.preventDefault();
			commit();
		}
	}
</script>

<div class={className}>
	<label
		for={id}
		class="mb-1.5 block font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
	>
		{label}
	</label>
	<div
		class="flex min-h-[50px] flex-wrap items-center gap-2 rounded-[6px] border border-(--field-border) bg-(--field-bg) px-2.5 py-1.5 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-orn"
	>
		{#each tags as tag (tag)}
			<TagChip
				label={tag}
				removeLabel={disabled ? undefined : removeLabel(tag)}
				onremove={disabled
					? undefined
					: () => {
							onremove(tag);
						}}
			/>
		{/each}
		<input
			{id}
			type="text"
			bind:value={text}
			{maxlength}
			disabled={disabled || full}
			placeholder={full ? '' : placeholder}
			list={listId}
			autocomplete="off"
			enterkeyhint="done"
			aria-invalid={error === null ? undefined : true}
			aria-describedby={note === null ? undefined : noteId}
			oninput={typed}
			onkeydown={pressed}
			onblur={commit}
			class="h-11 min-w-[140px] flex-1 bg-transparent text-[18px] text-ink outline-none placeholder:text-soft/75 disabled:cursor-not-allowed"
		/>
	</div>
	<datalist id={listId}>
		{#each suggestions as suggestion (suggestion)}
			<option value={suggestion}></option>
		{/each}
	</datalist>
	{#if note !== null}
		<p
			id={noteId}
			role={error === null ? undefined : 'alert'}
			class={['mt-1.5 mb-0 text-base', error === null ? 'text-soft' : 'text-label']}
		>
			{note}
		</p>
	{/if}
</div>

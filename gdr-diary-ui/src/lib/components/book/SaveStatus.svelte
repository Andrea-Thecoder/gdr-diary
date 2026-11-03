<script lang="ts">
	import type { SaveState } from '#lib/book/book.svelte.js';
	import Icon from '#lib/components/ui/Icon.svelte';

	interface Props {
		state: SaveState;
		class?: string;
	}

	let { state, class: className = '' }: Props = $props();

	const labels: Record<SaveState, string> = {
		saved: 'Salvato',
		pending: 'Da salvare',
		saving: 'Salvataggio…',
		unsaved: 'Non salvato',
		offline: 'Offline',
		conflict: 'Conflitto'
	};
</script>

<span
	role="status"
	class={[
		'inline-flex min-h-8 items-center gap-2 rounded-full border px-3 text-[16px]',
		state === 'unsaved' || state === 'offline' || state === 'conflict'
			? 'border-danger text-danger'
			: 'border-(--field-border) text-soft',
		className
	]}
>
	{#if state === 'saved'}
		<Icon size={16} strokeWidth={2.2} class="text-orn">
			<path d="M5 12.5l4.5 4.5L19 7.5" />
		</Icon>
	{:else if state === 'pending'}
		<span aria-hidden="true" class="block size-2.5 rounded-full border-2 border-orn"></span>
	{:else if state === 'saving'}
		<span
			aria-hidden="true"
			class="block size-4 animate-spin rounded-full border-2 border-orn border-t-transparent motion-reduce:animate-none"
		></span>
	{:else if state === 'offline'}
		<Icon size={16} strokeWidth={2}>
			<path
				d="M12 20h.01M8.5 16.43a5 5 0 0 1 7 0M5 12.86a10 10 0 0 1 5.17-2.69M19 12.86a10 10 0 0 0-2-1.52M2 8.82a15 15 0 0 1 4.18-2.65M22 8.82a15 15 0 0 0-11.29-3.76M2 2l20 20"
			/>
		</Icon>
	{:else}
		<Icon size={16} strokeWidth={2}>
			<path d="M12 4l9 16H3zM12 10v4M12 17.2v.1" />
		</Icon>
	{/if}
	{labels[state]}
</span>

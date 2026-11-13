<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		checked?: boolean;
		error?: string | null;
		class?: string;
		children: Snippet;
	}

	let {
		checked = $bindable(false),
		error = null,
		class: className = '',
		children
	}: Props = $props();

	const errorId = $props.id();
</script>

<div class={className}>
	<label class="flex min-h-[44px] cursor-pointer items-start gap-3 text-[17px] text-soft">
		<input
			type="checkbox"
			bind:checked
			aria-invalid={error === null ? undefined : true}
			aria-describedby={error === null ? undefined : errorId}
			class="mt-1.5 size-[22px] flex-none cursor-pointer accent-(--orn)"
		/>
		<span class="min-w-0">{@render children()}</span>
	</label>
	{#if error !== null}
		<p id={errorId} role="alert" class="mt-1 mb-0 text-base text-label">{error}</p>
	{/if}
</div>

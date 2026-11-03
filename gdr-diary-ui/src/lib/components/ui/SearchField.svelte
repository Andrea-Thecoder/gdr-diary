<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from './Icon.svelte';

	interface Props {
		label: string;
		value?: string;
		maxlength?: number | undefined;
		icon?: Snippet;
		class?: string;
	}

	let { label, value = $bindable(''), maxlength, icon, class: className = '' }: Props = $props();

	const id = $props.id();
</script>

<div
	class={[
		'flex min-h-[50px] min-w-0 items-center gap-2.5 rounded-[6px] border border-(--field-border) bg-(--field-bg) px-3.5 text-soft focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-orn',
		className
	]}
>
	{#if icon !== undefined}
		{@render icon()}
	{:else}
		<Icon size={20} strokeWidth={1.8} class="flex-none">
			<circle cx="11" cy="11" r="6.5" />
			<path d="M16 16l4.5 4.5" />
		</Icon>
	{/if}
	<label for={id} class="sr-only">{label}</label>
	<input
		{id}
		type="search"
		bind:value
		{maxlength}
		placeholder={label}
		class="h-12 min-w-0 flex-1 bg-transparent text-[18px] text-ink outline-none placeholder:text-soft/75"
	/>
</div>

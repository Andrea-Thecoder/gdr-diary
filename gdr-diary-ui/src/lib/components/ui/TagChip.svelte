<script lang="ts">
	import Icon from './Icon.svelte';

	interface Props {
		label: string;
		detail?: string | undefined;
		removeLabel?: string | undefined;
		onselect?: (() => void) | undefined;
		onremove?: (() => void) | undefined;
		class?: string;
	}

	let { label, detail, removeLabel, onselect, onremove, class: className = '' }: Props = $props();
</script>

<span
	class={[
		'inline-flex max-w-full min-w-0 items-center rounded-full border border-(--field-border) bg-(--chip-bg) text-ink',
		className
	]}
>
	{#if onselect === undefined}
		<span
			class={[
				'min-w-0 py-2 pl-3.5 text-base [overflow-wrap:anywhere]',
				onremove === undefined ? 'pr-3.5' : 'pr-0.5'
			]}
		>
			{label}
		</span>
	{:else}
		<button
			type="button"
			onclick={onselect}
			class="inline-flex min-h-[44px] min-w-0 cursor-pointer items-center gap-2 rounded-full px-3.5 text-left text-base hover:opacity-80"
		>
			<span class="min-w-0 [overflow-wrap:anywhere]">{label}</span>
			{#if detail !== undefined}
				<span class="flex-none text-[14px] text-soft">{detail}</span>
			{/if}
		</button>
	{/if}
	{#if onremove !== undefined}
		<button
			type="button"
			onclick={onremove}
			aria-label={removeLabel}
			title={removeLabel}
			class="inline-flex size-[44px] flex-none cursor-pointer items-center justify-center rounded-full text-soft hover:text-danger"
		>
			<Icon size={14} strokeWidth={2}>
				<path d="M6 6l12 12M18 6L6 18" />
			</Icon>
		</button>
	{/if}
</span>

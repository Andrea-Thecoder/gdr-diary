<script lang="ts" generics="T extends string">
	import type { Snippet } from 'svelte';

	interface Option {
		value: T;
		label: string;
	}

	interface Props {
		legend: string;
		options: readonly Option[];
		value: T;
		onselect: (value: T) => void;
		icon?: Snippet<[T]>;
		class?: string;
	}

	let { legend, options, value, onselect, icon, class: className = '' }: Props = $props();

	const name = $props.id();
</script>

<fieldset class={['m-0 min-w-0 border-0 p-0', className]}>
	<legend class="sr-only">{legend}</legend>
	<div
		class="inline-flex max-w-full flex-wrap gap-1 rounded-full border border-(--field-border) bg-(--chip-bg) p-1"
	>
		{#each options as option (option.value)}
			<label class="relative block min-w-0 cursor-pointer">
				<input
					type="radio"
					{name}
					value={option.value}
					checked={value === option.value}
					onchange={() => {
						onselect(option.value);
					}}
					class="peer sr-only"
				/>
				<span
					class="flex min-h-[44px] items-center justify-center gap-1.5 rounded-full border border-transparent px-3 text-[16px] text-soft capitalize transition-colors peer-checked:border-(color:--btn-border) peer-checked:text-btn-ink peer-checked:shadow-(--btn-shadow) peer-checked:surface-btn peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-orn hover:text-ink peer-checked:hover:text-btn-ink"
				>
					{#if icon !== undefined}
						<span class="flex-none">{@render icon(option.value)}</span>
					{/if}
					{option.label}
				</span>
			</label>
		{/each}
	</div>
</fieldset>

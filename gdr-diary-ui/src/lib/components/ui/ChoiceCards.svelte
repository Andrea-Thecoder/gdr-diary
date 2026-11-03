<script lang="ts" generics="T extends string">
	import type { Snippet } from 'svelte';

	interface Option {
		value: T;
		title: string;
		hint: string;
	}

	interface Props {
		legend: string;
		options: readonly Option[];
		value: T;
		onselect: (value: T) => void;
		icon: Snippet<[T]>;
	}

	let { legend, options, value, onselect, icon }: Props = $props();

	const name = $props.id();
</script>

<fieldset class="m-0 min-w-0 border-0 p-0">
	<legend
		class="mb-3 p-0 font-label text-[12px] font-semibold tracking-[.16em] text-label uppercase"
	>
		{legend}
	</legend>
	<div class="grid grid-cols-[repeat(auto-fit,minmax(min(220px,100%),1fr))] gap-4">
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
					class="flex min-h-[88px] items-start gap-3.5 rounded-[6px] border border-(color:--card-border) px-5 py-4 text-ink surface-card peer-checked:border-2 peer-checked:border-orn peer-focus-visible:outline-2 peer-focus-visible:outline-offset-3 peer-focus-visible:outline-orn hover:opacity-90"
				>
					<span class="mt-0.5 flex-none text-orn">
						{@render icon(option.value)}
					</span>
					<span class="min-w-0">
						<span class="block font-display text-[26px] leading-[1.1] font-bold capitalize">
							{option.title}
						</span>
						<span class="mt-1 block text-[17px] text-soft">{option.hint}</span>
					</span>
				</span>
			</label>
		{/each}
	</div>
</fieldset>

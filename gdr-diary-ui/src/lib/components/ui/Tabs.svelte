<script lang="ts" generics="T extends string">
	interface Tab {
		id: T;
		label: string;
	}

	interface Props {
		label: string;
		tabs: readonly Tab[];
		value: T;
		idPrefix: string;
		onselect: (id: T) => void;
	}

	let { label, tabs, value, idPrefix, onselect }: Props = $props();

	const buttons = $state<Record<string, HTMLButtonElement | undefined>>({});

	function move(event: KeyboardEvent, index: number): void {
		const last = tabs.length - 1;
		const targets: Record<string, number> = {
			ArrowRight: index === last ? 0 : index + 1,
			ArrowLeft: index === 0 ? last : index - 1,
			Home: 0,
			End: last
		};
		const target = targets[event.key];
		const next = target === undefined ? undefined : tabs[target];
		if (next === undefined) {
			return;
		}
		event.preventDefault();
		onselect(next.id);
		buttons[next.id]?.focus();
	}
</script>

<div
	role="tablist"
	aria-label={label}
	class="flex flex-wrap gap-x-7 gap-y-1 border-b border-(color:--card-border)"
>
	{#each tabs as tab, index (tab.id)}
		{@const selected = tab.id === value}
		<button
			bind:this={buttons[tab.id]}
			type="button"
			role="tab"
			id={`${idPrefix}-tab-${tab.id}`}
			aria-selected={selected}
			aria-controls={`${idPrefix}-panel-${tab.id}`}
			tabindex={selected ? 0 : -1}
			onclick={() => {
				onselect(tab.id);
			}}
			onkeydown={(event) => {
				move(event, index);
			}}
			class={[
				'-mb-px min-h-[44px] cursor-pointer border-0 border-b-2 bg-transparent px-0 pt-1 pb-2 font-display text-[clamp(17px,2.2vw,21px)] leading-[1.1] font-bold hover:opacity-85',
				selected ? 'border-orn text-ink' : 'border-transparent text-soft'
			]}
		>
			{tab.label}
		</button>
	{/each}
</div>

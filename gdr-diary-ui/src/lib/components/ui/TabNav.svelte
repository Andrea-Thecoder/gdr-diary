<script lang="ts" generics="T extends string">
	interface Tab {
		id: T;
		label: string;
		suffix?: string | undefined;
		href: string;
		count?: number | undefined;
		countLabel?: string | undefined;
	}

	interface Props {
		label: string;
		tabs: readonly Tab[];
		value: T;
		class?: string;
	}

	let { label, tabs, value, class: className = '' }: Props = $props();
</script>

<nav
	aria-label={label}
	class={[
		'flex [scrollbar-width:none] gap-1 overflow-x-auto shadow-[inset_0_-1px_0_var(--rule)] [&::-webkit-scrollbar]:hidden',
		className
	]}
>
	<!-- eslint-disable svelte/no-navigation-without-resolve -- gli href arrivano già risolti con resolve() da chi usa il componente -->
	{#each tabs as tab (tab.id)}
		{@const selected = tab.id === value}
		<a
			href={tab.href}
			aria-current={selected ? 'page' : undefined}
			class={[
				'inline-flex min-h-[56px] flex-none items-center gap-2 border-b-[3px] px-3.5 font-label text-[13px] font-semibold tracking-[.1em] whitespace-nowrap uppercase no-underline hover:text-ink min-[561px]:gap-2.5 min-[561px]:px-[22px] min-[561px]:text-[14px] min-[561px]:tracking-[.14em]',
				selected ? 'border-orn text-ink' : 'border-transparent text-soft'
			]}
		>
			<span
				>{tab.label}{#if tab.suffix !== undefined}<span class="max-[560px]:hidden"
						>{tab.suffix}</span
					>{/if}</span
			>
			{#if tab.count !== undefined}
				<span
					class={[
						'inline-flex h-[26px] min-w-[28px] items-center justify-center rounded-full border bg-(--tool-bg) px-2 text-[13px] font-semibold tracking-normal text-ink',
						selected ? 'border-orn' : 'border-(color:--rule)'
					]}
				>
					<span aria-hidden="true">{tab.count}</span>
					{#if tab.countLabel !== undefined}
						<span class="sr-only">{tab.countLabel}</span>
					{/if}
				</span>
			{/if}
		</a>
	{/each}
	<!-- eslint-enable svelte/no-navigation-without-resolve -->
</nav>

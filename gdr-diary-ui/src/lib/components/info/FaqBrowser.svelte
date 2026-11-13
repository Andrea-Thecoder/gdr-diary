<script lang="ts">
	import { filterFaq, type FaqGroup } from '#lib/info/faq.js';
	import Icon from '#lib/components/ui/Icon.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';

	interface Props {
		groups: readonly FaqGroup[];
		query: string;
	}

	let { groups, query }: Props = $props();

	const shown = $derived(filterFaq(groups, query));
	const searching = $derived(query.trim() !== '');
	let picked = $state<string | null>(null);
	const active = $derived(
		shown.some((group) => group.id === picked) ? picked : (shown[0]?.id ?? null)
	);
</script>

<div
	class="grid items-start gap-[clamp(32px,5vw,64px)] min-[901px]:grid-cols-[minmax(0,260px)_minmax(0,1fr)]"
>
	<nav
		aria-label="Argomenti"
		class="flex flex-wrap gap-2 min-[901px]:sticky min-[901px]:top-6 min-[901px]:flex-col min-[901px]:gap-1"
	>
		{#each shown as group (group.id)}
			<a
				href={`#${group.id}`}
				aria-current={group.id === active ? 'true' : undefined}
				onclick={() => {
					picked = group.id;
				}}
				class="flex min-h-[48px] items-center justify-between gap-2.5 rounded-full border border-(color:--rule) px-4 font-label text-[13px] font-semibold tracking-[.12em] text-ink uppercase no-underline hover:bg-(--tool-bg) aria-[current=true]:border-orn aria-[current=true]:bg-(--tool-bg) min-[901px]:border-transparent"
			>
				<span>{group.title}</span>
				<span class="text-[12px] tracking-normal text-soft">{group.items.length}</span>
			</a>
		{/each}
	</nav>
	<div class="flex min-w-0 flex-col gap-12">
		{#each shown as group, groupIndex (group.id)}
			{@const titleId = `faq-${group.id}`}
			<section id={group.id} aria-labelledby={titleId} class="scroll-mt-6">
				<SubHeading as="h2" id={titleId}>{group.title}</SubHeading>
				<div class="mt-4 flex flex-col gap-3">
					{#each group.items as item, itemIndex (item.question)}
						<details
							open={searching || (groupIndex === 0 && itemIndex === 0)}
							class="group rounded-[6px] border border-(color:--card-border) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
						>
							<summary
								class="flex min-h-[64px] cursor-pointer list-none items-center justify-between gap-5 px-[clamp(18px,3vw,24px)] py-4 text-[clamp(19px,2vw,21px)] leading-[1.3] font-medium text-ink focus-visible:outline-2 focus-visible:outline-offset-3 focus-visible:outline-orn [&::-webkit-details-marker]:hidden"
							>
								{item.question}
								<Icon
									size={22}
									strokeWidth={2}
									class="flex-none text-orn transition-transform group-open:rotate-180"
								>
									<path d="M6 9l6 6 6-6" />
								</Icon>
							</summary>
							<p class="m-0 max-w-[42em] px-[clamp(18px,3vw,24px)] pb-[22px] text-[19px] text-soft">
								{item.answer}
							</p>
						</details>
					{/each}
				</div>
			</section>
		{:else}
			<p role="status" class="m-0 text-soft">
				Nessuna domanda corrisponde a «{query.trim()}». Prova con un’altra parola, oppure scrivimi.
			</p>
		{/each}
	</div>
</div>

<script lang="ts">
	import type { PlanGroup } from '#lib/info/plans.js';
	import PlanCellMark from './PlanCellMark.svelte';

	interface Props {
		groups: readonly PlanGroup[];
		note?: string;
	}

	let { groups, note = '' }: Props = $props();

	const cellClass = 'px-2 py-3 text-center min-[561px]:px-4';
</script>

<div class="flex flex-col gap-3">
	<div
		class="overflow-x-auto rounded-[6px] border border-(color:--card-border) shadow-(--card-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
	>
		<table class="w-full border-collapse text-left text-[18px]">
			<caption class="sr-only">Confronto tra il piano gratuito e il piano Premium</caption>
			<thead>
				<tr class="border-b border-(color:--rule)">
					<th scope="col" class="px-4 py-3"><span class="sr-only">Funzione</span></th>
					<th
						scope="col"
						class={[
							cellClass,
							'w-[84px] font-label text-[12px] tracking-[.14em] text-label uppercase min-[561px]:w-[140px]'
						]}
					>
						Gratuito
					</th>
					<th
						scope="col"
						class={[
							cellClass,
							'w-[84px] font-label text-[12px] tracking-[.14em] text-label uppercase min-[561px]:w-[140px]'
						]}
					>
						Premium
					</th>
				</tr>
			</thead>
			{#each groups as group (group.title)}
				<tbody>
					<tr>
						<th
							colspan="3"
							scope="colgroup"
							class="bg-(--tool-bg) px-4 py-2.5 font-label text-[13px] font-semibold tracking-[.14em] text-label uppercase"
						>
							{group.title}
						</th>
					</tr>
					{#each group.rows as row (row.label)}
						<tr class="border-t border-(color:--rule)">
							<th scope="row" class="px-4 py-3 font-normal text-ink">
								{row.label}
								{#if row.soon}
									<span
										class="ml-2 inline-block rounded-full border border-(color:--rule) px-2 py-0.5 align-middle font-label text-[11px] tracking-[.1em] text-soft uppercase"
									>
										In arrivo
									</span>
								{/if}
							</th>
							<td class={cellClass}><PlanCellMark value={row.free} /></td>
							<td class={cellClass}><PlanCellMark value={row.premium} /></td>
						</tr>
					{/each}
				</tbody>
			{/each}
		</table>
	</div>
	{#if note}
		<p class="m-0 text-[16px] text-soft">{note}</p>
	{/if}
</div>

<script lang="ts">
	import { onMount } from 'svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import { formatAmount, statusLabel } from '#lib/payments/format.js';
	import { PaymentList } from '#lib/payments/payments.svelte.js';
	import { formatDate } from '#lib/sheets/format.js';

	const list = new PaymentList();

	onMount(() => {
		void list.reload();
	});
</script>

<SettingsSection
	title="Pagamenti"
	intro="Quello che hai pagato con questo account, con le ricevute."
>
	{#if list.status === 'loading'}
		<p role="status" class="m-0 text-[17px] text-soft">Carico i pagamenti…</p>
	{:else if list.status === 'error'}
		<p role="alert" class="m-0 text-[17px] text-danger">{list.error}</p>
	{:else if list.items.length === 0}
		<p class="m-0 text-[17px] text-soft">Non hai ancora fatto pagamenti.</p>
	{:else}
		<ul class="m-0 flex list-none flex-col p-0">
			{#each list.items as payment (payment.id)}
				<li
					class="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-b border-(color:--rule) py-4 first:pt-0 last:border-b-0"
				>
					<div class="min-w-0 flex-[1_1_220px]">
						<p class="m-0 text-[18px] [overflow-wrap:anywhere] text-ink">{payment.description}</p>
						<p class="m-0 mt-1 text-base text-soft">
							{formatDate(payment.paidAt) || 'Data non disponibile'} · {statusLabel(payment.status)}
						</p>
					</div>
					<div class="flex flex-none items-center gap-4">
						<span class="text-[18px] text-ink tabular-nums">
							{formatAmount(payment.amountCents, payment.currency)}
						</span>
						{#if payment.receiptUrl}
							<!-- eslint-disable svelte/no-navigation-without-resolve -- la ricevuta sta presso il provider dei pagamenti, non è una rotta dell'app -->
							<a
								href={payment.receiptUrl}
								target="_blank"
								rel="noopener noreferrer"
								class="inline-flex min-h-[44px] items-center text-ink underline underline-offset-4 hover:opacity-80"
							>
								Ricevuta
							</a>
							<!-- eslint-enable svelte/no-navigation-without-resolve -->
						{/if}
					</div>
				</li>
			{/each}
		</ul>
		{#if list.hasMore}
			<Button
				variant="outline"
				size="sm"
				class="mt-4"
				disabled={list.loadingMore}
				onclick={() => {
					void list.loadMore();
				}}
			>
				{list.loadingMore ? 'Carico…' : 'Mostra altri pagamenti'}
			</Button>
		{/if}
	{/if}
</SettingsSection>

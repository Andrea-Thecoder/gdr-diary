<script lang="ts">
	import AuthButton from '#lib/components/landing/AuthButton.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';
	import type { Plan } from '#lib/info/plans.js';

	interface Props {
		plan: Plan;
	}

	let { plan }: Props = $props();

	const titleId = $props.id();
</script>

<article
	aria-labelledby={titleId}
	class={[
		'relative flex flex-col gap-5 rounded-[6px] border p-[clamp(22px,3vw,32px)] shadow-(--card-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card',
		plan.soon ? 'border-orn' : 'border-(color:--card-border)'
	]}
>
	{#if plan.soon}
		<span
			class="absolute top-5 right-5 rounded-full border border-(--badge-border) px-3 py-1 font-label text-[12px] font-semibold tracking-[.14em] text-badge-ink uppercase"
		>
			In arrivo
		</span>
	{/if}
	<div class={plan.soon ? 'pr-28' : ''}>
		<Eyebrow class="text-label">{plan.kind}</Eyebrow>
		<SubHeading as="h2" id={titleId} class="mt-1 text-[clamp(34px,4vw,44px)]"
			>{plan.name}</SubHeading
		>
	</div>
	<p
		class="m-0 flex flex-wrap items-baseline gap-x-2 font-display text-[clamp(28px,3.4vw,36px)] leading-none font-bold text-ink"
	>
		{plan.price}
		{#if plan.per !== ''}
			<span class="font-body text-[18px] font-normal text-soft">{plan.per}</span>
		{/if}
	</p>
	<p class="m-0 text-soft">{plan.text}</p>
	<ul class="m-0 flex list-none flex-col gap-3 p-0">
		{#each plan.points as point (point)}
			<li class="flex items-start gap-3">
				<Icon size={22} strokeWidth={2} class="mt-0.5 flex-none text-orn">
					<path d="M5 12.5l4.5 4.5L19 7.5" />
				</Icon>
				{point}
			</li>
		{/each}
	</ul>
	<div class="mt-auto pt-2">
		{#if plan.soon}
			<Button variant="outline" size="block" disabled>In arrivo</Button>
		{:else}
			<AuthButton action="register" size="lg" class="w-full">Inizia gratis</AuthButton>
		{/if}
	</div>
</article>

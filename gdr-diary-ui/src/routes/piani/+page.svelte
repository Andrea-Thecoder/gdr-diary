<script lang="ts">
	import { resolve } from '$app/paths';
	import InfoPage from '#lib/components/info/InfoPage.svelte';
	import PlanCard from '#lib/components/info/PlanCard.svelte';
	import PlanTable from '#lib/components/info/PlanTable.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';
	import { PLAN_GROUPS, PLAN_NOTE, PLANS } from '#lib/info/plans.js';
</script>

<svelte:head>
	<title>Piani · Diario GDR</title>
	<meta
		name="description"
		content="Il piano gratuito basta per tenere i tuoi diari. Il Premium, quando arriverà, aggiungerà spazio e qualche extra."
	/>
</svelte:head>

<InfoPage
	eyebrow="Piani"
	title="Gratis per scrivere, Premium per di più."
	lead="Il piano gratuito basta per tenere i tuoi diari. Il Premium, quando arriverà, aggiungerà spazio e qualche extra."
>
	<div class="flex flex-col gap-[clamp(40px,6vw,72px)]">
		<div class="grid gap-7 min-[761px]:grid-cols-2">
			{#each PLANS as plan (plan.id)}
				<PlanCard {plan} />
			{/each}
		</div>
		<section aria-labelledby="confronto" class="flex flex-col gap-4">
			<SubHeading as="h2" id="confronto">Il confronto, voce per voce</SubHeading>
			<p class="m-0 text-soft">
				Le voci e i limiti di questa tabella sono ancora provvisori: non sono una promessa.
			</p>
			<PlanTable groups={PLAN_GROUPS} note={PLAN_NOTE} />
		</section>
		<SettingsSection
			title="Hai dubbi su cosa ti serve?"
			intro="Comincia dal piano gratuito: è già completo per scrivere. Per tutto il resto, c’è la pagina delle domande frequenti."
		>
			<a href={resolve('/faq')} class={[buttonClasses('outline', 'md'), 'no-underline']}>
				Domande frequenti
			</a>
		</SettingsSection>
	</div>
</InfoPage>

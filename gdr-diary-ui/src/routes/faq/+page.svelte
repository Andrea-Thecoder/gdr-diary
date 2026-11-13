<script lang="ts">
	import { resolve } from '$app/paths';
	import FaqBrowser from '#lib/components/info/FaqBrowser.svelte';
	import InfoPage from '#lib/components/info/InfoPage.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';
	import SearchField from '#lib/components/ui/SearchField.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import { FAQ_GROUPS } from '#lib/info/faq.js';

	let query = $state('');
</script>

<svelte:head>
	<title>Domande frequenti · Diario GDR</title>
	<meta
		name="description"
		content="Le risposte alle domande più frequenti su Diario GDR: come si scrive, le schede, l’account e i piani."
	/>
</svelte:head>

<InfoPage
	eyebrow="Domande frequenti"
	title="Le risposte che cerchi."
	lead="Le cose che mi chiedono più spesso, divise per argomento. Clicca su una domanda per aprirla."
>
	{#snippet extra()}
		<SearchField label="Cerca nelle domande" bind:value={query} class="max-w-[32rem]" />
	{/snippet}
	<div class="flex flex-col gap-[clamp(40px,6vw,72px)]">
		<FaqBrowser groups={FAQ_GROUPS} {query} />
		<SettingsSection
			title="Non hai trovato la risposta?"
			intro="Dimmi cosa cercavi: se manca, la aggiungo. Per una domanda diretta puoi anche scrivermi."
		>
			<div class="flex flex-wrap gap-4">
				<a href={resolve('/feedback')} class={[buttonClasses('solid', 'md'), 'no-underline']}>
					Lascia un feedback
				</a>
				<a href={resolve('/contatti')} class={[buttonClasses('outline', 'md'), 'no-underline']}>
					Contatti
				</a>
			</div>
		</SettingsSection>
	</div>
</InfoPage>

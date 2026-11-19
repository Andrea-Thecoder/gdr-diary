<script lang="ts">
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import ProgressBar from '#lib/components/ui/ProgressBar.svelte';
	import SectionHeading from '#lib/components/ui/SectionHeading.svelte';
	import StepCard from './StepCard.svelte';
</script>

<Container
	as="section"
	id="come-funziona"
	aria-labelledby="come-title"
	class="py-[clamp(56px,9vw,112px)]"
>
	<Eyebrow class="mb-4 text-label">Come funziona</Eyebrow>
	<SectionHeading id="come-title" class="max-w-[15em]">
		Tre passi dalla taverna alla prima pagina.
	</SectionHeading>

	<div class="mt-[52px] flex flex-wrap gap-[26px]">
		<StepCard
			numeral="I"
			title="Forgia il personaggio"
			text="Un nome, un volto, la campagna a cui appartiene. Ogni personaggio ha i suoi diari, separati da quelli degli altri."
			mockLabel="Scheda di un personaggio con iniziale, nome e campagna"
			padded
		>
			{#snippet mock()}
				<div
					class="flex w-full max-w-[290px] items-center gap-3.5 rounded-[4px] bg-(--mock-page) p-4 shadow-(--mock-page-shadow)"
				>
					<div
						class="flex size-[58px] flex-none items-center justify-center rounded-full border-2 border-(color:--avatar-ring) font-display text-[32px] font-bold text-avatar-ink surface-avatar"
					>
						L
					</div>
					<div class="min-w-0">
						<div class="font-display text-[23px] leading-[1.1] font-bold text-page-ink">
							Lyra Vento d'Argento
						</div>
						<div class="mt-1 text-[15px] text-page-soft">Le Ceneri di Valkar</div>
					</div>
				</div>
			{/snippet}
		</StepCard>

		<StepCard
			numeral="II"
			title="Rilega un diario"
			text="Scegli un titolo e il colore della copertina. Personalizza ogni diario in modo da essere unico"
			mockLabel="Due copertine di diario con rifiniture dorate"
		>
			{#snippet mock()}
				<div
					class="h-[144px] w-[104px] translate-x-2.5 -rotate-5 rounded-[3px_9px_9px_3px] border-l-[9px] border-l-black/30 p-2 shadow-(--mini-cover-shadow) surface-cover-1"
				>
					<div
						class="flex h-full items-center justify-center border border-(color:--cover-frame) p-1.5 text-center font-display text-[19px] leading-[1.05] font-bold text-cover-ink"
					>
						Cronache del Nord
					</div>
				</div>
				<div
					class="h-[144px] w-[104px] -translate-x-2.5 translate-y-2 rotate-4 rounded-[3px_9px_9px_3px] border-l-[9px] border-l-black/30 p-2 shadow-(--mini-cover-shadow) surface-cover-2"
				>
					<div
						class="flex h-full items-center justify-center border border-(color:--cover-frame) p-1.5 text-center font-display text-[19px] leading-[1.05] font-bold text-cover-ink"
					>
						Taccuino di viaggio
					</div>
				</div>
			{/snippet}
		</StepCard>

		<StepCard
			numeral="III"
			title="Scrivi, pagina dopo pagina"
			text="Personalizza il tuo diario e racconta ogni momento della tua avventura, con fino a 100 pagine a disposizione."
			mockLabel="Una pagina a righe con una frase scritta a mano e l'indicazione pagina 7 di 100"
			padded
		>
			{#snippet mock()}
				<div
					class="w-full max-w-[250px] rounded-[3px] bg-(--mock-page) px-[18px] pt-3.5 pb-3 shadow-(--mock-page-shadow)"
				>
					<div class="font-hand text-[23px] leading-[1.2] text-page-ink">
						Giorno 3. Siamo ancora vivi.
					</div>
					<div class="my-3 h-px bg-(--page-rule)"></div>
					<div class="my-3 h-px bg-(--page-rule)"></div>
					<div class="mt-3 mb-2.5 h-px bg-(--page-rule)"></div>
					<ProgressBar value={7} max={100} label="pag. 7 / 100" />
				</div>
			{/snippet}
		</StepCard>
	</div>
</Container>

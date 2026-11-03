<script lang="ts">
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import SectionHeading from '#lib/components/ui/SectionHeading.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';

	const points = [
		{
			title: 'Due pagine alla volta',
			text: 'Il diario si apre come un libro: pagina di sinistra, pagina di destra, e si sfoglia avanti e indietro.'
		},
		{
			title: 'Testo ricco, nessuna distrazione',
			text: "Titoli, grassetto, corsivo, elenchi. L'editor c'è quando serve e si toglie di mezzo quando scrivi."
		},
		{
			title: 'Solo tuo',
			text: 'Personaggi, diari e pagine li vedi solo tu, dopo l’accesso.'
		}
	];
</script>

<section
	id="scrittura"
	aria-labelledby="scrittura-title"
	class="border-y border-(color:--band-border) surface-band"
>
	<Container class="flex flex-wrap items-center gap-x-16 gap-y-14 py-[clamp(56px,9vw,112px)]">
		<div class="min-w-0 flex-[1_1_380px]">
			<Eyebrow class="mb-4 text-band-label">La scrittura</Eyebrow>
			<SectionHeading id="scrittura-title">
				Scrivi come uno scriba, rileggi come un libro antico.
			</SectionHeading>
			<p class="mx-0 mt-7 mb-0 text-[21px] text-soft">
				<span
					class="float-left pt-2 pr-3 font-display text-[68px] leading-[.78] font-bold text-dropcap"
					>P</span
				>agina dopo pagina, il diario si riempie di ciò che il tuo personaggio ha visto, perso e
				giurato.
			</p>
			<div class="clear-both mt-[30px] flex flex-col gap-6">
				{#each points as point (point.title)}
					<div class="flex gap-4">
						<div aria-hidden="true" class="mt-[11px] size-2.5 flex-none rotate-45 bg-orn"></div>
						<div>
							<SubHeading size="sm" class="mb-1">{point.title}</SubHeading>
							<p class="m-0 text-soft">{point.text}</p>
						</div>
					</div>
				{/each}
			</div>
		</div>

		<div
			role="img"
			aria-label="Anteprima dell'editor: barra degli strumenti, una pagina scritta e i comandi per sfogliare"
			class="mx-auto max-w-[560px] min-w-0 flex-[1_1_420px]"
		>
			<div
				class="flex w-fit flex-wrap gap-1.5 rounded-[6px] border border-(color:--tool-border) bg-(--tool-bg) p-1.5 text-tool-ink"
			>
				<div
					class="flex size-[38px] items-center justify-center font-display text-[22px] font-bold"
				>
					H
				</div>
				<div
					class="flex size-[38px] items-center justify-center rounded-[4px] bg-(--tool-active) text-[19px] font-medium"
				>
					B
				</div>
				<div class="flex size-[38px] items-center justify-center text-[19px] italic">I</div>
				<div class="flex size-[38px] items-center justify-center text-[19px] underline">U</div>
				<div class="flex size-[38px] items-center justify-center">
					<svg
						width="20"
						height="20"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="1.8"
						stroke-linecap="round"
						aria-hidden="true"
					>
						<circle cx="5" cy="7" r="1.2" />
						<circle cx="5" cy="12" r="1.2" />
						<circle cx="5" cy="17" r="1.2" />
						<path d="M10 7h10M10 12h10M10 17h10" />
					</svg>
				</div>
			</div>
			<div
				class="mt-3.5 min-h-[300px] rounded-[4px] bg-(--page) p-[clamp(20px,4vw,34px)] shadow-(--page-shadow)"
			>
				<div class="font-label text-[10px] tracking-[.18em] text-page-soft uppercase">Pagina 7</div>
				<div
					class="mt-1 mb-3.5 font-display text-[clamp(25px,3.4vw,34px)] leading-[1.1] font-bold text-page-ink"
				>
					Giorno 3, nella palude
				</div>
				<p class="m-0 font-hand text-[clamp(19px,2.6vw,24px)] leading-[1.4] text-page-ink">
					Fango fino alle ginocchia. Brann dice che la mappa è sbagliata, la mappa dice che Brann è
					sbagliato.
				</p>
				<div class="mt-3 font-hand text-[clamp(19px,2.6vw,24px)] leading-normal text-page-ink">
					- trovare una barca<br />- non fidarsi del ponte<span
						class="ml-[3px] inline-block h-[1.1em] w-0.5 bg-fill align-text-bottom"
					></span>
				</div>
			</div>
			<div class="mt-5 flex items-center justify-center gap-[18px]">
				<div
					class="flex size-11 items-center justify-center rounded-full border border-pager text-pager"
				>
					<svg
						width="18"
						height="18"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
						stroke-linejoin="round"
						aria-hidden="true"
					>
						<path d="M15 5l-7 7 7 7" />
					</svg>
				</div>
				<div class="font-label text-[13px] tracking-[.12em] text-soft uppercase">
					Pagine 7 e 8 di 100
				</div>
				<div
					class="flex size-11 items-center justify-center rounded-full border border-pager text-pager"
				>
					<svg
						width="18"
						height="18"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
						stroke-linejoin="round"
						aria-hidden="true"
					>
						<path d="M9 5l7 7-7 7" />
					</svg>
				</div>
			</div>
		</div>
	</Container>
</section>

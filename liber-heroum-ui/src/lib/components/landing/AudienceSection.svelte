<script lang="ts">
	import Container from '#lib/components/ui/Container.svelte';
	import Eyebrow from '#lib/components/ui/Eyebrow.svelte';
	import SectionHeading from '#lib/components/ui/SectionHeading.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';

	const audiences = [
		{
			title: 'Il diario del tuo personaggio',
			text: 'La cronaca di ogni sessione, gli indizi, le promesse fatte al tavolo. Tutto quello che il tuo personaggio ricorderebbe.',
			icon: 'book'
		},
		{
			title: 'Gli appunti del master',
			text: 'Crea un personaggio Master e usa i suoi diari per trame, personaggi non giocanti e segreti della campagna.',
			icon: 'crown'
		},
		{
			title: 'Anche dal telefono',
			text: 'L’interfaccia si adatta allo schermo: scrivi tra un turno e l’altro senza aprire il portatile.',
			icon: 'phone'
		}
	] as const;
</script>

<section id="per-chi" aria-labelledby="perchi-title" class="text-s4-ink surface-s4">
	<Container class="py-[clamp(56px,9vw,112px)]">
		<Eyebrow class="mb-4 text-s4-label">Per chi è</Eyebrow>
		<SectionHeading id="perchi-title" tone="dark-band" class="max-w-[14em]">
			Per l'avventuriero e per il master.
		</SectionHeading>

		<div class="mt-[52px] flex flex-wrap gap-[26px]">
			{#each audiences as audience (audience.title)}
				<article
					class="min-w-0 flex-[1_1_280px] rounded-[6px] border border-(color:--s4-card-border) px-[30px] pt-[34px] pb-[38px] outline-1 -outline-offset-8 outline-(color:--s4-card-outline) surface-s4-card"
				>
					<svg
						width="44"
						height="44"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="1.3"
						stroke-linecap="round"
						stroke-linejoin="round"
						aria-hidden="true"
						class="text-s4-icon"
					>
						{#if audience.icon === 'book'}
							<path d="M5 4h11a3 3 0 013 3v13H8a3 3 0 01-3-3V4z" />
							<path d="M5 17a3 3 0 013-3h11" />
							<path d="M9 8h6" />
						{:else if audience.icon === 'crown'}
							<path d="M4 18l1.5-9 4.5 4 2-7 2 7 4.5-4L20 18H4z" />
							<path d="M4 21h16" />
						{:else}
							<rect x="7" y="2.5" width="10" height="19" rx="2.2" />
							<path d="M11 18.5h2" />
						{/if}
					</svg>
					<SubHeading tone="dark-band" class="mt-[18px] mb-2.5">{audience.title}</SubHeading>
					<p class="m-0 text-s4-soft">{audience.text}</p>
				</article>
			{/each}
		</div>
	</Container>
</section>

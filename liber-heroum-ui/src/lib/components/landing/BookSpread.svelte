<script lang="ts">
	import QuillIcon from '#lib/components/ui/QuillIcon.svelte';

	const corners = [
		'top-1.5 left-1.5 rounded-tl-[6px] border-t-2 border-l-2',
		'top-1.5 right-1.5 rounded-tr-[8px] border-t-2 border-r-2',
		'bottom-1.5 left-1.5 rounded-bl-[6px] border-b-2 border-l-2',
		'bottom-1.5 right-1.5 rounded-br-[8px] border-r-2 border-b-2'
	];
</script>

<div
	role="img"
	aria-label="Un diario con copertina rifinita in oro, aperto su due pagine scritte a mano, con un segnalibro e un sigillo di cera"
	class="relative mx-auto max-w-[560px] min-w-0 flex-[1_1_420px] px-4 pt-[18px] pb-7"
>
	<svg
		viewBox="0 0 200 200"
		fill="none"
		aria-hidden="true"
		class="pointer-events-none absolute top-1/2 left-1/2 z-0 h-auto w-[128%] -translate-x-1/2 -translate-y-1/2 opacity-(--ring-o)"
	>
		<g data-ring="outer" class="ring-outer">
			<circle
				cx="100"
				cy="100"
				r="96"
				stroke-width=".5"
				stroke-dasharray="1 3"
				class="stroke-(color:--ring-a)"
			/>
			<circle cx="100" cy="100" r="88" stroke-width=".6" class="stroke-(color:--ring-b)" />
			<path
				d="M100 4v10M100 186v10M4 100h10M186 100h10M32 32l7 7M161 161l7 7M168 32l-7 7M39 161l-7 7"
				stroke-width=".8"
				stroke-linecap="round"
				class="stroke-(color:--ring-a)"
			/>
		</g>
		<g data-ring="inner" class="ring-inner">
			<circle
				cx="100"
				cy="100"
				r="80"
				stroke-width=".4"
				stroke-dasharray="6 4"
				class="stroke-(color:--ring-a)"
			/>
			<path
				d="M100 22L167.5 139H32.5ZM100 178L167.5 61H32.5Z"
				stroke-width=".5"
				stroke-linejoin="round"
				class="stroke-(color:--ring-b)"
			/>
		</g>
	</svg>

	<div
		class="absolute top-1.5 right-[16%] z-[3] h-[92px] w-[22px] [clip-path:polygon(0_0,100%_0,100%_100%,50%_84%,0_100%)] surface-ribbon"
	></div>

	<div
		class="relative z-[1] -rotate-[1.5deg] rounded-[10px_16px_16px_10px] p-4 shadow-(--book-shadow) surface-cover"
	>
		{#each corners as corner (corner)}
			<div class="absolute size-5 border-(color:--cover-corner) {corner}"></div>
		{/each}
		<div class="flex min-h-[clamp(270px,50vw,360px)] overflow-hidden rounded-[4px] bg-(--page)">
			<div class="min-w-0 flex-[1_1_50%] p-[clamp(14px,3vw,30px)] shadow-(--fold-left)">
				<div class="font-label text-[10px] tracking-[.18em] text-page-soft uppercase">
					Sessione 14
				</div>
				<div
					class="mt-1 mb-3 font-display text-[clamp(21px,3vw,29px)] leading-[1.1] font-bold text-page-ink"
				>
					La torre di Valkar
				</div>
				<p class="m-0 font-hand text-[clamp(17px,2.4vw,23px)] leading-[1.35] text-page-ink">
					La porta era sigillata, e Brann giurava di averla lasciata aperta. Lyra ha riso. Io no.
					Stanotte dormiamo a turni.
				</p>
				<div class="mt-3.5 text-center text-[13px] text-page-soft">26</div>
			</div>
			<div class="min-w-0 flex-[1_1_50%] p-[clamp(14px,3vw,30px)] shadow-(--fold-right)">
				<div class="font-label text-[10px] tracking-[.18em] text-page-soft uppercase">
					Da ricordare
				</div>
				<p
					class="mx-0 mt-2.5 mb-0 font-hand text-[clamp(17px,2.4vw,23px)] leading-[1.4] text-page-ink"
				>
					Il sigillo sulla porta.<br />Il debito con Brann: 40 monete.<br />Chiedere al master del
					mappamondo.
				</p>
				<div class="mt-3.5 text-center text-[13px] text-page-soft">27</div>
			</div>
		</div>
	</div>

	<div
		class="absolute bottom-1 -left-0.5 z-[4] flex size-[78px] items-center justify-center rounded-full shadow-(--seal-shadow) surface-seal"
	>
		<QuillIcon size={34} class="text-seal-ink" />
	</div>
</div>

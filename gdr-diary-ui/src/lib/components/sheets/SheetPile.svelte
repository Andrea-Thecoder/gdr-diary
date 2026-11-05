<script lang="ts">
	interface Props {
		title: string;
		color: string;
	}

	let { title, color }: Props = $props();
</script>

<div
	role="img"
	aria-label={`Anteprima di ${title}: scheda in PDF`}
	class="relative h-[258px] w-[196px] flex-none"
>
	<div
		aria-hidden="true"
		class="absolute inset-0 rotate-[4.5deg] rounded-[3px] bg-[#E4D6B2] shadow-[0_10px_18px_-10px_rgba(0,0,0,.6)]"
	></div>
	<div
		aria-hidden="true"
		class="absolute inset-0 -rotate-3 rounded-[3px] bg-[#F1E6C8] shadow-[0_10px_18px_-10px_rgba(0,0,0,.6)]"
	></div>
	<div
		aria-hidden="true"
		class="absolute inset-0 overflow-hidden rounded-[3px] bg-[#FBF7EC] shadow-[0_22px_22px_-12px_rgba(0,0,0,.6),0_0_0_1px_rgba(60,40,20,.25)]"
	>
		<svg viewBox="0 0 196 258" aria-hidden="true" class="absolute inset-0 size-full">
			<rect x="0" y="0" width="196" height="42" fill={color} />
			<rect x="14" y="12" width="98" height="9" rx="2" fill="#F3D98B" />
			<rect x="14" y="26" width="62" height="5" rx="2" fill="#FFFFFF" opacity=".6" />
			<rect
				x="130"
				y="10"
				width="52"
				height="22"
				rx="2"
				fill="none"
				stroke="#F3D98B"
				stroke-width="1.2"
			/>
			<g fill="none" stroke="#8A7650" stroke-width="1.2">
				<rect x="14" y="54" width="36" height="36" rx="5" />
				<rect x="14" y="98" width="36" height="36" rx="5" />
				<rect x="14" y="142" width="36" height="36" rx="5" />
				<rect x="60" y="116" width="122" height="62" rx="3" />
				<rect x="14" y="190" width="168" height="52" rx="3" />
			</g>
			<g fill="#CDBB8F">
				<rect x="22" y="76" width="20" height="3" rx="1.5" />
				<rect x="22" y="120" width="20" height="3" rx="1.5" />
				<rect x="22" y="164" width="20" height="3" rx="1.5" />
			</g>
			<path
				d="M60 62H182M60 76H172M60 90H182M60 104H150M68 130H172M68 144H160M68 158H176M22 204H172M22 218H150M22 232H164"
				stroke="#CDBB8F"
				stroke-width="3"
				stroke-linecap="round"
				fill="none"
			/>
		</svg>
	</div>
	<span
		aria-hidden="true"
		class="absolute right-[-12px] bottom-5 rounded-[3px] bg-[#A8271F] px-2.5 py-1 font-label text-[12px] font-semibold tracking-[.14em] text-[#FFF3E0] shadow-[0_5px_10px_rgba(0,0,0,.45)]"
	>
		PDF
	</span>
</div>

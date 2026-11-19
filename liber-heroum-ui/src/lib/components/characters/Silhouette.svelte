<script lang="ts">
	import type { Silhouette } from '#lib/characters/appearance.js';

	interface Props {
		kind: Silhouette;
	}

	let { kind }: Props = $props();
</script>

<svg
	viewBox="0 0 200 240"
	preserveAspectRatio="xMidYMax meet"
	aria-hidden="true"
	class="absolute inset-0 size-full"
>
	{#each [{ fill: 'var(--silhouette-glow)', opacity: 0.6, transform: 'translate(-2.5 -1.5)' }, { fill: 'var(--silhouette-body)', opacity: 0.94, transform: undefined }] as layer, index (index)}
		<g style:fill={layer.fill} opacity={layer.opacity} transform={layer.transform}>
			{#if kind === 'woman'}
				<path
					d="M58 112C46 62 76 26 100 26C126 26 154 64 142 112C152 152 142 196 154 240L46 240C58 196 48 152 58 112Z"
				/>
				<ellipse cx="100" cy="98" rx="26" ry="32" />
				<path d="M90 124L110 124L112 152C128 158 160 176 172 240L28 240C40 176 72 158 88 152Z" />
			{:else if kind === 'master'}
				<path
					d="M100 20C93 40 64 48 64 80C64 98 71 112 80 122L120 122C129 112 136 98 136 80C136 48 107 40 100 20Z"
				/>
				<path d="M82 118L118 118C150 130 172 172 186 240L14 240C28 172 50 130 82 118Z" />
				<rect x="160" y="70" width="6" height="170" rx="3" />
				<circle cx="163" cy="60" r="11" />
			{:else}
				<path
					d="M69 92C69 60 86 46 101 46C117 46 131 60 131 92C131 112 123 134 100 136C77 134 69 112 69 92Z"
				/>
				<path d="M86 126L114 126L114 154C150 160 180 178 188 240L12 240C20 178 50 160 86 154Z" />
			{/if}
		</g>
	{/each}
</svg>

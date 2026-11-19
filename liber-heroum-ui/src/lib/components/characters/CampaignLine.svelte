<script lang="ts">
	import Icon from '#lib/components/ui/Icon.svelte';

	interface Props {
		campaign: string;
	}

	let { campaign }: Props = $props();
</script>

{#if campaign !== ''}
	<p class="mx-0 mt-1.5 mb-0 flex items-center gap-2 text-[18px] text-soft">
		<Icon size={16} strokeWidth={1.8} class="flex-none text-orn">
			<path d="M6 21V4M6 5h12l-3 4 3 4H6" />
		</Icon>
		<span class="min-w-0 break-words">{campaign}</span>
	</p>
{:else}
	<p class="mx-0 mt-1.5 mb-0 text-[18px] text-soft italic">Senza campagna</p>
{/if}

<script lang="ts">
	interface Props {
		class?: string;
	}

	let { class: className = '' }: Props = $props();
</script>

<p class={['m-0 text-[16px] max-[760px]:hidden', className]}>
	<kbd class="rounded-[4px] border border-(--stage-border) px-1.5 py-0.5 font-label text-[12px]">
		Ctrl
	</kbd>
	+
	<kbd class="rounded-[4px] border border-(--stage-border) px-1.5 py-0.5 font-label text-[12px]">
		S
	</kbd>
	per salvare subito
</p>

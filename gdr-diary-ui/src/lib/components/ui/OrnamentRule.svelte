<script lang="ts">
	interface Props {
		class?: string;
	}

	let { class: className = '' }: Props = $props();
</script>

<div aria-hidden="true" class={['flex items-center gap-4', className]}>
	<div class="h-px flex-1 bg-(--rule)"></div>
	<div class="size-2.5 rotate-45 bg-orn"></div>
	<div class="h-px flex-1 bg-(--rule)"></div>
</div>

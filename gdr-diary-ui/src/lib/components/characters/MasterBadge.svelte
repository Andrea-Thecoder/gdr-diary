<script lang="ts">
	import Icon from '#lib/components/ui/Icon.svelte';

	interface Props {
		class?: string;
	}

	let { class: className = '' }: Props = $props();
</script>

<span
	role="img"
	aria-label="Master"
	title="Master"
	class={[
		'flex size-9 items-center justify-center rounded-full border surface-gold-chip',
		className
	]}
>
	<Icon size={18} strokeWidth={1.8}>
		<path d="M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5L3 8z" />
		<path d="M5.5 19h13" />
	</Icon>
</span>

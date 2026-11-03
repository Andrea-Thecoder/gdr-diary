<script lang="ts">
	import { resolve } from '$app/paths';
	import QuillIcon from './QuillIcon.svelte';

	interface Props {
		class?: string;
	}

	let { class: className = '' }: Props = $props();
</script>

<a
	href={resolve('/')}
	class={['inline-flex min-h-[44px] items-center gap-2.5 text-ink no-underline', className]}
>
	<QuillIcon size={30} class="text-orn" />
	<span class="font-display text-[clamp(24px,7vw,32px)] leading-none font-bold">Diario GDR</span>
</a>

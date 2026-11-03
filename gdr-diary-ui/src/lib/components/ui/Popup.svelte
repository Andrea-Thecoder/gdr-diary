<script lang="ts">
	import type { Snippet } from 'svelte';
	import { createBackdropPress } from '#lib/ui/outside.js';
	import Icon from './Icon.svelte';
	import IconButton from './IconButton.svelte';

	interface Props {
		tone?: 'status' | 'alert';
		placement?: 'center' | 'corner';
		duration?: number;
		onclose: () => void;
		children: Snippet;
	}

	let {
		tone = 'status',
		placement = 'center',
		duration = 4000,
		onclose,
		children
	}: Props = $props();

	const corner = $derived(placement === 'corner');

	let paused = $state(false);
	let backdrop = $state<HTMLDivElement | null>(null);

	const press = createBackdropPress(
		(target) => target === backdrop,
		() => {
			onclose();
		}
	);

	function closeOnEscape(event: KeyboardEvent): void {
		if (event.key === 'Escape') {
			onclose();
		}
	}

	$effect(() => {
		if (paused) {
			return;
		}
		const timer = setTimeout(onclose, duration);
		return () => {
			clearTimeout(timer);
		};
	});
</script>

<svelte:window onkeydown={closeOnEscape} />

<div
	bind:this={backdrop}
	role="presentation"
	onpointerdown={corner ? undefined : press.onpointerdown}
	onclick={corner ? undefined : press.onclick}
	class={[
		'fixed inset-0 z-50 p-4',
		corner
			? 'pointer-events-none flex items-end justify-end pr-[max(16px,env(safe-area-inset-right))] pb-[max(16px,env(safe-area-inset-bottom))]'
			: 'grid place-items-center'
	]}
>
	<div
		role={tone}
		onpointerenter={() => {
			paused = true;
		}}
		onpointerleave={() => {
			paused = false;
		}}
		onfocusin={() => {
			paused = true;
		}}
		onfocusout={() => {
			paused = false;
		}}
		class={[
			'flex w-full max-w-[460px] items-start gap-4 rounded-[8px] border border-(color:--card-border) p-6 text-[19px] text-ink shadow-(--modal-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card',
			corner && 'pointer-events-auto'
		]}
	>
		<div class="min-w-0 flex-1 pt-2.5 [overflow-wrap:anywhere]">
			{@render children()}
		</div>
		<IconButton label="Chiudi" onclick={onclose} class="flex-none rounded-full">
			<Icon size={18} strokeWidth={2}>
				<path d="M6 6l12 12M18 6L6 18" />
			</Icon>
		</IconButton>
	</div>
</div>

<script lang="ts">
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { accountName } from '#lib/auth/display.js';
	import UserAvatar from './UserAvatar.svelte';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { profileAvatar } from '#lib/profile/avatar.svelte.js';
	import { outsideClick } from '#lib/ui/outside.js';

	const panelId = $props.id();

	let open = $state(false);
	let trigger = $state<HTMLButtonElement | null>(null);

	const label = $derived(accountName(auth.user));

	function close(): void {
		open = false;
	}

	function closeOnEscape(event: KeyboardEvent): void {
		if (open && event.key === 'Escape') {
			open = false;
			trigger?.focus();
		}
	}

	function logout(): void {
		void auth.logout();
	}
</script>

<svelte:window onkeydown={closeOnEscape} />

<div use:outsideClick={close} class="relative">
	<button
		bind:this={trigger}
		type="button"
		aria-label="Menu dell’account"
		aria-expanded={open}
		aria-controls={open ? panelId : undefined}
		onclick={() => {
			open = !open;
		}}
		class="flex min-h-[44px] cursor-pointer items-center gap-1.5 rounded-full border-0 bg-transparent p-0 text-nav-ink hover:opacity-80"
	>
		<UserAvatar
			{label}
			url={profileAvatar.url}
			class="size-[44px] border-(color:--nav-border) font-label text-[15px] font-semibold"
		/>
		<Icon
			size={16}
			strokeWidth={2}
			class={open ? 'rotate-180 transition-transform' : 'transition-transform'}
		>
			<path d="M6 9l6 6 6-6" />
		</Icon>
	</button>

	{#if open}
		<div
			id={panelId}
			class="absolute top-[calc(100%+10px)] right-0 z-40 w-[min(320px,calc(100vw-32px))] rounded-[8px] border border-(color:--card-border) text-ink shadow-(--modal-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card"
		>
			<a
				href={resolve('/(app)/profile')}
				onclick={close}
				class="flex items-center gap-3 border-b border-(color:--rule) px-5 py-4 text-ink no-underline hover:opacity-80"
			>
				<UserAvatar
					{label}
					url={profileAvatar.url}
					class="size-11 border-(color:--nav-border) font-label text-[16px] font-semibold"
				/>
				<span class="min-w-0 flex-1">
					<span
						class="block font-label text-[11px] font-medium tracking-[.16em] text-label uppercase"
					>
						Il tuo profilo
					</span>
					<span class="block truncate text-[17px]">{label}</span>
				</span>
				<ArrowIcon size={16} />
			</a>
			<a
				href={resolve('/(app)/settings')}
				onclick={close}
				class="flex min-h-[52px] items-center gap-3 border-b border-(color:--rule) px-5 text-[18px] text-ink no-underline hover:opacity-80"
			>
				<Icon size={20} strokeWidth={1.6} class="flex-none text-orn">
					<circle cx="12" cy="12" r="3" />
					<path
						d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"
					/>
				</Icon>
				Impostazioni
			</a>
			<div class="p-4">
				<Button variant="outline" size="block" onclick={logout}>Esci</Button>
			</div>
		</div>
	{/if}
</div>

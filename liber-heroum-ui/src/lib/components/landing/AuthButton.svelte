<script lang="ts">
	import type { Snippet } from 'svelte';
	import { resolve } from '$app/paths';
	import { auth } from '#lib/auth/auth.svelte.js';
	import { loginToApp } from '#lib/auth/login.js';
	import { registerToApp } from '#lib/auth/register.js';
	import Button from '#lib/components/ui/Button.svelte';
	import { buttonClasses } from '#lib/components/ui/button.js';

	interface Props {
		action?: 'login' | 'register';
		variant?: 'solid' | 'outline';
		size?: 'sm' | 'md' | 'lg';
		class?: string;
		children: Snippet;
	}

	let {
		action = 'login',
		variant = 'solid',
		size = 'md',
		class: className = '',
		children
	}: Props = $props();

	const disabled = $derived(auth.status === 'loading' || auth.status === 'error');
</script>

{#if auth.status !== 'authenticated'}
	<Button
		{variant}
		{size}
		{disabled}
		onclick={action === 'register' ? registerToApp : loginToApp}
		class={className}
	>
		{@render children()}
	</Button>
{:else if action === 'register'}
	<a
		href={resolve('/(app)/characters')}
		class={[buttonClasses('solid', size), 'no-underline', className]}
	>
		I tuoi personaggi
	</a>
{/if}

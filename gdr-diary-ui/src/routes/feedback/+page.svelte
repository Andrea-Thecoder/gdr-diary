<script lang="ts">
	import { afterNavigate } from '$app/navigation';
	import { resolve } from '$app/paths';
	import FeedbackForm from '#lib/components/feedback/FeedbackForm.svelte';
	import InfoPage from '#lib/components/info/InfoPage.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import { screenOf } from '#lib/feedback/feedback.js';

	const linkClass =
		'flex min-h-[44px] items-center text-[19px] text-ink underline underline-offset-4 hover:opacity-80';

	let sent = $state(false);
	let from = $state('');

	afterNavigate((navigation) => {
		from = screenOf(navigation.from?.route.id);
	});
</script>

<svelte:head>
	<title>Feedback · Diario GDR</title>
	<meta
		name="description"
		content="Hai un problema o un’idea? Scrivilo e lascia che il nostro servitore inosservato lo recapiti alle persone giuste."
	/>
</svelte:head>

<InfoPage
	eyebrow="Feedback"
	title={sent ? 'Fatto.' : 'Raccontaci la tua esperienza'}
	lead={sent
		? 'Il servitore inosservato ha ricevuto il tuo messaggio.'
		: 'Hai un problema o un’idea? Scrivilo e lascia che il nostro servitore inosservato lo recapiti alle persone giuste.'}
>
	<div
		class={[
			'grid items-start gap-7',
			!sent && 'min-[901px]:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]'
		]}
	>
		<FeedbackForm bind:sent {from} />
		{#if !sent}
			<aside class="flex flex-col gap-7">
				<SettingsSection title="Cerchi altro?">
					<nav aria-label="Altre pagine" class="flex flex-col">
						<a href={resolve('/faq')} class={linkClass}>Le domande frequenti</a>
						<a href={resolve('/contatti')} class={linkClass}>Scrivimi direttamente</a>
					</nav>
				</SettingsSection>
			</aside>
		{/if}
	</div>
</InfoPage>

<script lang="ts">
	import { onDestroy, untrack } from 'svelte';
	import AccountHeader from '#lib/components/app/AccountHeader.svelte';
	import Container from '#lib/components/ui/Container.svelte';
	import FieldRow from '#lib/components/ui/FieldRow.svelte';
	import RangeField from '#lib/components/ui/RangeField.svelte';
	import SaveBar from '#lib/components/ui/SaveBar.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import SelectField from '#lib/components/ui/SelectField.svelte';
	import SettingsSection from '#lib/components/ui/SettingsSection.svelte';
	import SubHeading from '#lib/components/ui/SubHeading.svelte';
	import SwitchField from '#lib/components/ui/SwitchField.svelte';
	import ThemePicker from '#lib/components/ui/ThemePicker.svelte';
	import { FONTS } from '#lib/diaries/typography.js';
	import { savePreferencesToAccount } from '#lib/settings/account.js';
	import {
		AUTO_SAVE_DELAY_MAX,
		AUTO_SAVE_DELAY_MIN,
		changedPreferences,
		FONT_SIZE_MAX,
		FONT_SIZE_MIN,
		LINE_SPACING_LABELS,
		LINE_SPACINGS,
		type LineSpacing,
		type Preferences
	} from '#lib/settings/preferences.js';
	import { preferences } from '#lib/settings/preferences.svelte.js';
	import { THEME_SETTING } from '#lib/settings/settings.js';
	import type { ThemePreference } from '#lib/theme/theme.js';
	import { theme } from '#lib/theme/theme.svelte.js';

	const ids = $props.id();
	const lineOptions = LINE_SPACINGS.map((value) => ({ value, label: LINE_SPACING_LABELS[value] }));

	let draft = $state<Preferences>(untrack(() => ({ ...preferences.value })));
	theme.init();

	let savedTheme = $state<ThemePreference>(untrack(() => theme.preference));
	let saving = $state(false);
	let saveError = $state<string | null>(null);

	const changes = $derived({
		...changedPreferences(preferences.value, draft),
		...(theme.preference === savedTheme ? {} : { [THEME_SETTING]: theme.preference })
	});
	const dirty = $derived(Object.keys(changes).length > 0);

	function restoreSavedTheme(): void {
		if (theme.preference !== savedTheme) {
			theme.select(savedTheme);
		}
	}

	onDestroy(restoreSavedTheme);

	async function submit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		if (!dirty) {
			return;
		}
		saving = true;
		saveError = null;
		try {
			await savePreferencesToAccount(changes);
			draft = { ...preferences.value };
			savedTheme = theme.preference;
		} catch (cause) {
			saveError = cause instanceof Error ? cause.message : 'Non riesco a salvare le impostazioni.';
		} finally {
			saving = false;
		}
	}

	function cancel(): void {
		draft = { ...preferences.value };
		theme.select(savedTheme);
		saveError = null;
	}
</script>

<svelte:window onpagehide={restoreSavedTheme} />

<svelte:head>
	<title>Impostazioni · Diario GDR</title>
</svelte:head>

<AccountHeader
	title="Impostazioni"
	subtitle="Scegli come appaiono le pagine e come si comporta l’app. Le modifiche valgono per tutti i tuoi diari."
/>

<Container
	class="flex flex-wrap items-start gap-x-[clamp(24px,4vw,48px)] gap-y-8 pt-[clamp(36px,5vw,56px)] pb-[clamp(64px,9vw,112px)]"
>
	<nav
		aria-label="In questa pagina"
		class="w-full min-w-0 flex-[1_1_240px] rounded-[8px] border border-(color:--card-border) p-[clamp(20px,3vw,28px)] outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card max-[899px]:max-w-none max-[899px]:basis-full min-[900px]:max-w-[300px]"
	>
		<SubHeading as="h2">In questa pagina</SubHeading>
		<ul
			class="m-0 mt-3 flex list-none flex-col p-0 max-[899px]:flex-row max-[899px]:flex-wrap max-[899px]:gap-3"
		>
			{#each [{ id: 'generale', label: 'Generale' }, { id: 'scrittura', label: 'Scrittura' }, { id: 'aspetto', label: 'Aspetto' }] as item (item.id)}
				<li>
					<a
						href={`#${item.id}`}
						class="flex min-h-[44px] items-center gap-3 text-[18px] text-ink no-underline hover:opacity-80 max-[899px]:rounded-full max-[899px]:border max-[899px]:border-(color:--nav-border) max-[899px]:px-5"
					>
						<span aria-hidden="true" class="size-2 rotate-45 bg-orn"></span>
						{item.label}
					</a>
				</li>
			{/each}
		</ul>
	</nav>

	<form onsubmit={submit} class="flex min-w-0 flex-[2_1_420px] flex-col gap-6">
		<div id="generale" class="scroll-mt-6">
			<SettingsSection title="Generale" intro="Come si comporta l’app mentre lavori.">
				<FieldRow
					label="Salvataggio automatico"
					help="Salva da solo mentre scrivi."
					labelId={`${ids}-auto`}
				>
					<SwitchField
						checked={draft.autoSave}
						labelledby={`${ids}-auto`}
						onchange={(value: boolean) => {
							draft.autoSave = value;
						}}
					/>
				</FieldRow>
				{#if draft.autoSave}
					<FieldRow
						label="Attesa prima di salvare"
						help="Quanti secondi di pausa nella scrittura servono prima che il salvataggio parta da solo."
						labelId={`${ids}-delay`}
					>
						<RangeField
							value={draft.autoSaveDelay}
							min={AUTO_SAVE_DELAY_MIN}
							max={AUTO_SAVE_DELAY_MAX}
							unit="s"
							labelledby={`${ids}-delay`}
							onchange={(value: number) => {
								draft.autoSaveDelay = value;
							}}
						/>
					</FieldRow>
				{/if}
			</SettingsSection>
		</div>

		<div id="scrittura" class="scroll-mt-6">
			<SettingsSection title="Scrittura" intro="Come si leggono e si scrivono le pagine.">
				<FieldRow
					label="Font dei diari"
					help="Il carattere delle pagine. Un diario può averne uno suo."
					labelId={`${ids}-font`}
				>
					<SelectField label="Font dei diari" hideLabel bind:value={draft.font} options={FONTS} />
					<p
						class="mx-0 mt-3 mb-0 truncate text-ink"
						style:font-family={`'${draft.font}', Georgia, serif`}
						style:font-size={`${String(draft.fontSize)}px`}
					>
						Il mio diario comincia qui.
					</p>
				</FieldRow>
				<FieldRow
					label="Dimensione del testo"
					help="Quanto sono grandi le lettere nelle pagine."
					labelId={`${ids}-size`}
				>
					<RangeField
						value={draft.fontSize}
						min={FONT_SIZE_MIN}
						max={FONT_SIZE_MAX}
						unit="pt"
						labelledby={`${ids}-size`}
						onchange={(value: number) => {
							draft.fontSize = value;
						}}
					/>
				</FieldRow>
				<FieldRow
					label="Interlinea"
					help="Lo spazio tra una riga e l’altra."
					labelId={`${ids}-line`}
				>
					<SegmentedControl
						legend="Interlinea"
						options={lineOptions}
						value={draft.lineSpacing}
						onselect={(value: LineSpacing) => {
							draft.lineSpacing = value;
						}}
					/>
				</FieldRow>
			</SettingsSection>
		</div>

		<div id="aspetto" class="scroll-mt-6">
			<SettingsSection title="Aspetto" intro="Impostazioni che riguardano l'aspetto grafico.">
				<FieldRow label="Tema" help="Sistema segue le impostazioni del tuo dispositivo.">
					<ThemePicker />
				</FieldRow>
				<FieldRow
					label="Effetto sfoglio"
					help="L’animazione quando giri pagina."
					labelId={`${ids}-turn`}
				>
					<SwitchField
						checked={draft.pageTurn}
						labelledby={`${ids}-turn`}
						onchange={(value: boolean) => {
							draft.pageTurn = value;
						}}
					/>
				</FieldRow>
				<FieldRow
					label="Animazioni ridotte"
					help="Meno movimento in tutta l’app."
					labelId={`${ids}-motion`}
				>
					<SwitchField
						checked={draft.reduceMotion}
						labelledby={`${ids}-motion`}
						onchange={(value: boolean) => {
							draft.reduceMotion = value;
						}}
					/>
				</FieldRow>
			</SettingsSection>
		</div>

		{#if dirty}
			<SaveBar
				message="Hai modifiche non salvate. Si inviano solo le voci cambiate."
				{saving}
				error={saveError}
				oncancel={cancel}
			/>
		{/if}
	</form>
</Container>

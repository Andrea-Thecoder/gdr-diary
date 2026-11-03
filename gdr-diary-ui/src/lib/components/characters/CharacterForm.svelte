<script lang="ts">
	import { untrack } from 'svelte';
	import {
		AVATAR_TYPES,
		COLOR_PRESETS,
		DEFAULT_COLOR,
		PORTRAIT_FRAME,
		isHexColor,
		silhouetteFor
	} from '#lib/characters/appearance.js';
	import {
		createCharacter,
		updateCharacter,
		type AvatarChange,
		type CharacterDetail,
		type CreatedCharacter
	} from '#lib/characters/api.js';
	import {
		DEFAULT_KIND,
		KIND_OPTIONS,
		kindCopy,
		type CharacterKind
	} from '#lib/characters/kind.js';
	import { getApi } from '#lib/api/index.js';
	import ArrowIcon from '#lib/components/ui/ArrowIcon.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import ChoiceCards from '#lib/components/ui/ChoiceCards.svelte';
	import ColorField from '#lib/components/ui/ColorField.svelte';
	import DialogHeader from '#lib/components/ui/DialogHeader.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import ImageUpload from '#lib/components/ui/ImageUpload.svelte';
	import LinkButton from '#lib/components/ui/LinkButton.svelte';
	import StepIndicator from '#lib/components/ui/StepIndicator.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import CharacterPortrait from './CharacterPortrait.svelte';
	import KindIcon from './KindIcon.svelte';

	interface Props {
		titleId: string;
		character?: CharacterDetail | undefined;
		onsaved: (result: CreatedCharacter) => void;
		oncancel: () => void;
		submitting?: boolean;
	}

	let { titleId, character, onsaved, oncancel, submitting = $bindable(false) }: Props = $props();

	const initial = untrack(() => character);
	const editing = initial !== undefined;
	const existingAvatar = initial?.avatarUrl ?? null;

	const formId = $props.id();

	let step = $state<1 | 2>(editing ? 2 : 1);
	let chosenKind = $state<CharacterKind>(initial?.kind ?? DEFAULT_KIND);
	let name = $state(initial?.name ?? '');
	let campaignName = $state(initial?.campaignName ?? '');
	let description = $state(initial?.description ?? '');
	let hex = $state<string>(
		initial?.backgroundColor !== null &&
			initial?.backgroundColor !== undefined &&
			isHexColor(initial.backgroundColor)
			? initial.backgroundColor
			: DEFAULT_COLOR
	);
	let removeExisting = $state(false);
	let avatar = $state<File | null>(null);
	let avatarPreview = $state<string | null>(null);
	let nameError = $state<string | null>(null);
	let submitError = $state<string | null>(null);

	const previewKind = $derived(silhouetteFor(initial?.id ?? formId, chosenKind));
	const copy = $derived(kindCopy(chosenKind));
	const headerTitle = $derived(
		editing ? 'Modifica personaggio' : step === 1 ? 'Nuovo personaggio' : copy.title
	);
	const headerSubtitle = $derived(
		editing
			? 'Cambia quello che vuoi: il tipo, personaggio o master, non si può cambiare.'
			: step === 1
				? 'Prima scegli che cosa vuoi creare, poi tocca ai dettagli.'
				: 'Basta un nome per cominciare: il resto lo aggiungi quando vuoi.'
	);
	const shownAvatar = $derived(avatarPreview ?? (removeExisting ? null : existingAvatar));
	const hexValid = $derived(isHexColor(hex));
	const hexError = $derived(hexValid ? null : 'Usa il formato #RRGGBB, per esempio #3B2A6B.');
	const previewColor = $derived(hexValid ? hex : DEFAULT_COLOR);

	$effect(() => {
		const url = avatarPreview;
		return () => {
			if (url !== null) {
				URL.revokeObjectURL(url);
			}
		};
	});

	function chooseFile(file: File): void {
		avatar = file;
		avatarPreview = URL.createObjectURL(file);
	}

	function avatarChange(): AvatarChange {
		if (avatar !== null) {
			return { type: 'replace', file: avatar };
		}
		return removeExisting ? { type: 'remove' } : { type: 'keep' };
	}

	function removeFile(): void {
		removeExisting = existingAvatar !== null;
		avatar = null;
		avatarPreview = null;
	}

	function cancel(): void {
		if (!submitting) {
			oncancel();
		}
	}

	async function submit(event: SubmitEvent): Promise<void> {
		event.preventDefault();
		if (step === 1) {
			step = 2;
			return;
		}
		submitError = null;
		nameError = name.trim() === '' ? 'Il nome è obbligatorio.' : null;
		if (nameError !== null || !hexValid) {
			return;
		}
		submitting = true;
		try {
			const fields = { name, description, campaignName, backgroundColor: hex.toUpperCase() };
			const result =
				character?.id === undefined
					? await createCharacter(getApi(), { kind: chosenKind, ...fields }, avatar)
					: await updateCharacter(getApi(), character.id, fields, avatarChange());
			onsaved(result);
		} catch (cause) {
			submitError =
				cause instanceof Error
					? cause.message
					: editing
						? 'Non riesco a salvare il personaggio.'
						: 'Non riesco a creare il personaggio.';
		} finally {
			submitting = false;
		}
	}
</script>

<form onsubmit={submit} novalidate class="p-5 min-[560px]:p-8 min-[560px]:pb-7">
	{#if !editing}
		<StepIndicator {step} total={2} class="mb-5" />
	{/if}
	<DialogHeader {titleId} title={headerTitle} subtitle={headerSubtitle} onclose={cancel} />

	{#if step === 1}
		<div class="mt-6">
			<ChoiceCards
				legend="Che cosa crei?"
				options={KIND_OPTIONS}
				value={chosenKind}
				onselect={(value: CharacterKind) => {
					chosenKind = value;
				}}
			>
				{#snippet icon(value)}
					<KindIcon kind={value} />
				{/snippet}
			</ChoiceCards>
		</div>

		<p
			class="mx-0 mt-5 mb-0 flex items-start gap-3 rounded-[6px] bg-(--chip-bg) px-4 py-3 text-[17px] text-soft"
		>
			<Icon size={20} class="mt-0.5 flex-none text-orn">
				<circle cx="12" cy="12" r="9" />
				<path d="M12 11v5M12 8v.01" />
			</Icon>
			<span
				>La scelta è definitiva e non modificabile; per cambiarla, è necessario crearne una nuova</span
			>
		</p>

		<div class="mt-7 flex flex-wrap items-center justify-between gap-3">
			<Button variant="outline" size="form" onclick={cancel}>Annulla</Button>
			<Button type="submit" size="form">
				Continua
				<ArrowIcon size={18} />
			</Button>
		</div>
	{:else}
		{#if !editing}
			<div
				class="mt-6 flex min-h-[52px] items-center justify-between gap-3 rounded-[6px] bg-(--chip-bg) py-1 pr-3 pl-4"
			>
				<span class="flex min-w-0 items-center gap-3 text-ink">
					<span class="flex-none text-orn"><KindIcon kind={chosenKind} size={22} /></span>
					<span class="font-display text-[22px] leading-none font-bold">{copy.label}</span>
				</span>
				<LinkButton
					onclick={() => {
						step = 1;
					}}
				>
					Cambia
				</LinkButton>
			</div>
		{/if}

		<ImageUpload
			hasImage={shownAvatar !== null}
			accept={AVATAR_TYPES}
			chooseLabel="Carica un’immagine"
			changeLabel="Cambia immagine"
			hint="Facoltativa. PNG, JPEG o WebP. Senza immagine compare una silhouette."
			rejectMessage="Formato non supportato: usa un file PNG, JPEG o WebP."
			crop={PORTRAIT_FRAME}
			onfile={chooseFile}
			onremove={removeFile}
		>
			{#snippet preview()}
				<CharacterPortrait
					label={shownAvatar === null
						? 'Anteprima dell’avatar: silhouette in penombra'
						: avatarPreview === null
							? 'Immagine attuale del personaggio'
							: 'Anteprima dell’avatar scelto'}
					color={previewColor}
					avatarUrl={shownAvatar}
					silhouette={previewKind}
					class="size-24 flex-none rounded-full border-2 border-orn"
				/>
			{/snippet}
		</ImageUpload>

		<div class="mt-[22px] flex flex-col gap-[18px]">
			<TextField
				label="Nome"
				required
				bind:value={name}
				placeholder={copy.namePlaceholder}
				maxlength={255}
				error={nameError}
			/>
			<TextField
				label={copy.campaignLabel}
				bind:value={campaignName}
				placeholder="Il Nord Spezzato"
				maxlength={255}
			/>
			<TextField
				label="Descrizione"
				multiline
				bind:value={description}
				placeholder={copy.descriptionPlaceholder}
				maxlength={2000}
			/>
			<ColorField
				label="Colore della scheda"
				bind:value={hex}
				presets={COLOR_PRESETS}
				error={hexError}
			/>
		</div>

		{#if submitError !== null}
			<p role="alert" class="mx-0 mt-5 mb-0 text-base text-label">{submitError}</p>
		{/if}

		<div
			class={[
				'mt-7 flex flex-wrap items-center gap-3',
				editing ? 'justify-end' : 'justify-between'
			]}
		>
			{#if editing}
				<Button variant="outline" size="form" onclick={cancel}>Annulla</Button>
			{:else}
				<Button
					variant="outline"
					size="form"
					disabled={submitting}
					onclick={() => {
						step = 1;
					}}
				>
					<ArrowIcon direction="left" size={18} />
					Indietro
				</Button>
			{/if}
			<Button type="submit" size="form" disabled={submitting}>
				{#if editing}
					{submitting ? 'Salvo…' : 'Salva le modifiche'}
				{:else}
					{submitting ? 'Forgio…' : copy.submit}
				{/if}
			</Button>
		</div>
	{/if}
</form>

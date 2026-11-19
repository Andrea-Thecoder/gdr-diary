<script lang="ts">
	interface Props {
		class?: string;
	}

	let { class: className = '' }: Props = $props();
</script>

<div
	role="img"
	aria-label="Un rotolo di pergamena"
	class={['relative h-[82px] w-[58px] flex-none', className]}
>
	<div
		aria-hidden="true"
		class="absolute inset-x-[6px] top-2 bottom-2 bg-(--page) shadow-[0_10px_14px_-8px_var(--roll-drop)]"
	></div>
	<div
		aria-hidden="true"
		class="absolute inset-x-0 top-0 h-4 rounded-[8px] shadow-[0_3px_4px_var(--roll-drop)]"
		style:background="var(--roll-bg)"
	></div>
	<div
		aria-hidden="true"
		class="absolute inset-x-0 bottom-0 h-4 rounded-[8px] shadow-[0_3px_4px_var(--roll-drop)]"
		style:background="var(--roll-bg)"
	></div>
	<div
		aria-hidden="true"
		class="absolute inset-x-4 top-[26px] h-0.5 bg-(--page-rule) shadow-[0_8px_0_var(--page-rule),0_16px_0_var(--page-rule)]"
	></div>
</div>

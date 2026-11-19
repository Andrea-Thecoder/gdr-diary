<script lang="ts">
	interface Props {
		diameter: number;
		edge: 'top' | 'bottom';
		class?: string;
	}

	let { diameter, edge, class: className = '' }: Props = $props();
</script>

<div
	aria-hidden="true"
	data-roll={edge}
	class={[
		'scroll-roll pointer-events-none absolute right-[calc(var(--ov,7px)*-1)] left-[calc(var(--ov,7px)*-1)]',
		edge === 'top' ? 'scroll-roll-top top-0' : 'scroll-roll-bottom bottom-0',
		className
	]}
	style:height={`${String(diameter)}px`}
></div>

<style>
	.scroll-roll {
		border-radius: 7px;
		background: var(--roll-bg);
	}

	.scroll-roll::before {
		content: '';
		position: absolute;
		top: 18%;
		right: 6%;
		left: 6%;
		height: 1px;
		border-radius: 1px;
		background: var(--roll-shine);
	}

	.scroll-roll::after {
		content: '';
		position: absolute;
		right: calc(var(--ov, 7px) + 2px);
		left: calc(var(--ov, 7px) + 2px);
		height: 44px;
	}

	.scroll-roll-top {
		box-shadow: 0 12px 16px -8px var(--roll-drop);
	}

	.scroll-roll-top::after {
		top: 100%;
		margin-top: 2px;
		background: linear-gradient(180deg, var(--roll-shade), transparent);
	}

	.scroll-roll-bottom {
		box-shadow: 0 -12px 16px -8px var(--roll-drop);
	}

	.scroll-roll-bottom::after {
		bottom: 100%;
		margin-bottom: 2px;
		background: linear-gradient(0deg, var(--roll-shade), transparent);
	}
</style>

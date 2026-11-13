import { page } from 'vitest/browser';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { Plan } from '#lib/info/plans.js';

const { register, auth } = vi.hoisted(() => {
	const register = vi.fn(() => Promise.resolve());
	return { register, auth: { status: 'anonymous', register, login: vi.fn() } };
});

vi.mock('#lib/auth/auth.svelte.js', () => ({ auth }));

import PlanCard from './PlanCard.svelte';

const free: Plan = {
	id: 'free',
	kind: 'Per cominciare',
	name: 'Gratuito',
	price: '0 €',
	per: 'per sempre',
	text: 'Tutto ciò che serve.',
	points: ['Primo punto', 'Secondo punto'],
	soon: false
};

const premium: Plan = {
	id: 'premium',
	kind: 'Per chi scrive tanto',
	name: 'Premium',
	price: 'Prezzo da definire',
	per: '',
	text: 'Più spazio.',
	points: ['Terzo punto'],
	soon: true
};

describe('PlanCard', () => {
	beforeEach(() => {
		register.mockClear();
		auth.status = 'anonymous';
	});

	it('shows the name, the price and how long it lasts', async () => {
		render(PlanCard, { plan: free });

		await expect.element(page.getByRole('heading', { level: 2 })).toHaveTextContent('Gratuito');
		await expect.element(page.getByText('0 €')).toBeVisible();
		await expect.element(page.getByText('per sempre')).toBeVisible();
	});

	it('lists the points of the plan', async () => {
		render(PlanCard, { plan: free });

		await expect.element(page.getByRole('listitem').first()).toHaveTextContent('Primo punto');
		expect(page.getByRole('listitem').elements()).toHaveLength(2);
	});

	it('starts the registration from the free plan', async () => {
		render(PlanCard, { plan: free });

		await page.getByRole('button', { name: 'Inizia gratis' }).click();

		expect(register).toHaveBeenCalledOnce();
	});

	it('leads someone who is signed in to the characters', async () => {
		auth.status = 'authenticated';
		render(PlanCard, { plan: free });

		await expect
			.element(page.getByRole('link', { name: 'I tuoi personaggi' }))
			.toHaveAttribute('href', '/characters');
	});

	it('shows a disabled button and a badge on the plan that is coming soon', async () => {
		render(PlanCard, { plan: premium });

		await expect.element(page.getByRole('button', { name: 'In arrivo' })).toBeDisabled();
		await expect.element(page.getByText('In arrivo', { exact: true }).first()).toBeVisible();
	});

	it('does not write a duration when the plan has none', () => {
		render(PlanCard, { plan: premium });

		expect(page.getByText('per sempre').elements()).toHaveLength(0);
	});
});

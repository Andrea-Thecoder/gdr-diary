import { page } from 'vitest/browser';
import { describe, expect, it } from 'vitest';
import { render } from 'vitest-browser-svelte';
import '../../../routes/layout.css';
import type { PlanGroup } from '#lib/info/plans.js';
import PlanTable from './PlanTable.svelte';

const groups: PlanGroup[] = [
	{
		title: 'Scrittura',
		rows: [
			{ label: 'Diari', free: { kind: 'yes' }, premium: { kind: 'yes' }, soon: false },
			{ label: 'Temi', free: { kind: 'no' }, premium: { kind: 'yes' }, soon: false }
		]
	},
	{
		title: 'Extra',
		rows: [
			{
				label: 'Esportazione',
				free: { kind: 'text', text: '3' },
				premium: { kind: 'text', text: 'Molte' },
				soon: true
			}
		]
	}
];

describe('PlanTable', () => {
	it('has a caption and one column header for each plan', async () => {
		render(PlanTable, { groups });

		await expect
			.element(
				page.getByRole('table', { name: 'Confronto tra il piano gratuito e il piano Premium' })
			)
			.toBeInTheDocument();
		await expect.element(page.getByRole('columnheader', { name: 'Gratuito' })).toBeVisible();
		await expect.element(page.getByRole('columnheader', { name: 'Premium' })).toBeVisible();
	});

	it('shows a title row for each group', () => {
		render(PlanTable, { groups });

		const titles = Array.from(document.querySelectorAll('th[colspan="3"]')).map((cell) =>
			cell.textContent.trim()
		);

		expect(titles).toEqual(['Scrittura', 'Extra']);
	});

	it('says in words whether a feature is included', async () => {
		render(PlanTable, { groups });
		const row = page.getByRole('row', { name: /Temi/ });

		await expect.element(row.getByRole('cell').first()).toHaveTextContent('Non incluso');
		await expect.element(row.getByRole('cell').last()).toHaveTextContent('Incluso');
	});

	it('writes the limit when a cell has one', async () => {
		render(PlanTable, { groups });
		const row = page.getByRole('row', { name: /Esportazione/ });

		await expect.element(row.getByRole('cell').first()).toHaveTextContent('3');
		await expect.element(row.getByRole('cell').last()).toHaveTextContent('Molte');
	});

	it('marks a feature that is not ready yet', async () => {
		render(PlanTable, { groups });

		await expect
			.element(page.getByRole('row', { name: /Esportazione/ }).getByText('In arrivo'))
			.toBeVisible();
		expect(page.getByRole('row', { name: /Diari/ }).getByText('In arrivo').elements()).toHaveLength(
			0
		);
	});

	it('shows the note under the table when there is one', async () => {
		render(PlanTable, { groups, note: '* Una nota' });

		await expect.element(page.getByText('* Una nota')).toBeVisible();
	});

	it('shows no note when there is none', () => {
		render(PlanTable, { groups });

		expect(document.querySelectorAll('table + p')).toHaveLength(0);
	});
});

import type { Payment } from './api.js';

export type PaymentStatus = NonNullable<Payment['status']>;

const STATUS_LABELS: Record<PaymentStatus, string> = {
	PENDING: 'In attesa',
	SUCCEEDED: 'Pagato',
	FAILED: 'Non riuscito',
	REFUNDED: 'Rimborsato'
};

export function statusLabel(status: Payment['status']): string {
	return status === undefined ? '' : STATUS_LABELS[status];
}

export function formatAmount(cents: number | undefined, currency: string | undefined): string {
	if (cents === undefined || currency === undefined) {
		return '';
	}
	return new Intl.NumberFormat('it-IT', { style: 'currency', currency }).format(cents / 100);
}

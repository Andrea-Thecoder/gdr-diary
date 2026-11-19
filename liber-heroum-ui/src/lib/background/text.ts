import type { JSONContent } from '@tiptap/core';

export function textLength(node: JSONContent | undefined): number {
	if (node === undefined) {
		return 0;
	}
	if (node.type === 'text') {
		return Array.from(node.text ?? '').length;
	}
	return (node.content ?? []).reduce((total, child) => total + textLength(child), 0);
}

export function formatCount(value: number): string {
	return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

export function fillPercent(count: number, max: number): number {
	return Math.min(100, (Math.max(0, count) / max) * 100);
}

export function isNearlyFull(count: number, max: number): boolean {
	return fillPercent(count, max) >= 90;
}

export function fillLabel(count: number, max: number): string {
	if (count <= 0) {
		return 'Il rotolo è ancora bianco';
	}
	if (count >= max) {
		return 'Il rotolo è pieno';
	}
	return `Il rotolo è pieno al ${String(Math.max(1, Math.round(fillPercent(count, max))))}%`;
}

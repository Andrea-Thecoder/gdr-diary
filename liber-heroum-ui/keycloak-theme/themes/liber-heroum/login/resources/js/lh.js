function setupPasswordToggles() {
	for (const button of document.querySelectorAll('[data-lh-password-toggle]')) {
		const input = document.getElementById(button.dataset.lhPasswordToggle);
		if (!(input instanceof HTMLInputElement)) {
			continue;
		}
		button.addEventListener('click', () => {
			const reveal = input.type === 'password';
			input.type = reveal ? 'text' : 'password';
			button.setAttribute(
				'aria-label',
				(reveal ? button.dataset.labelHide : button.dataset.labelShow) ?? ''
			);
			button.querySelector('[data-icon-show]')?.toggleAttribute('hidden', reveal);
			button.querySelector('[data-icon-hide]')?.toggleAttribute('hidden', !reveal);
		});
	}
}

function setSubmitButtons(form, disabled) {
	for (const button of form.querySelectorAll('button[type="submit"]')) {
		button.disabled = disabled;
	}
}

function setupSingleSubmit() {
	const forms = document.querySelectorAll('form[data-lh-single-submit]');
	for (const form of forms) {
		form.addEventListener('submit', () => {
			setTimeout(() => {
				setSubmitButtons(form, true);
			}, 0);
		});
	}
	window.addEventListener('pageshow', (event) => {
		if (event.persisted) {
			for (const form of forms) {
				setSubmitButtons(form, false);
			}
		}
	});
}

function setupAutoSubmit() {
	const form = document.querySelector('form[data-lh-auto-submit]');
	if (!(form instanceof HTMLFormElement)) {
		return;
	}
	let sent = false;
	const send = () => {
		if (!sent) {
			sent = true;
			form.submit();
		}
	};
	window.addEventListener('pageshow', (event) => {
		if (event.persisted) {
			sent = true;
		}
	});
	setTimeout(send, 0);
}

function flowReturnUrl() {
	const raw = new URLSearchParams(window.location.search).get('client_data');
	if (raw === null) {
		return null;
	}
	try {
		const base64 = raw.replace(/-/g, '+').replace(/_/g, '/');
		const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
		const url = new URL(JSON.parse(atob(padded)).ru);
		return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null;
	} catch {
		return null;
	}
}

function setupRedirect() {
	const container = document.querySelector('[data-lh-redirect], [data-lh-redirect-from-flow]');
	if (container === null) {
		return;
	}
	let target = container.dataset.lhRedirect ?? null;
	if (target === null) {
		target = flowReturnUrl();
		const fallback = container.querySelector('[data-lh-flow-redirect]');
		if (target === null || fallback === null) {
			return;
		}
		fallback.querySelector('a')?.setAttribute('href', target);
		fallback.removeAttribute('hidden');
	}
	const seconds = Number(container.dataset.lhRedirectAfter ?? '3');
	setTimeout(() => {
		window.location.assign(target);
	}, seconds * 1000);
}

setupPasswordToggles();
setupSingleSubmit();
setupAutoSubmit();
setupRedirect();

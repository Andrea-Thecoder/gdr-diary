import { z } from 'zod';

const envSchema = z.object({
	VITE_KEYCLOAK_URL: z.url(),
	VITE_KEYCLOAK_REALM: z.string().min(1),
	VITE_KEYCLOAK_CLIENT_ID: z.string().min(1),
	VITE_API_BASE_URL: z.url()
});

export type AppEnv = z.infer<typeof envSchema>;

export function parseEnv(source: Record<string, unknown>): AppEnv {
	const result = envSchema.safeParse(source);
	if (!result.success) {
		const problems = result.error.issues
			.map((issue) => `${issue.path.join('.')}: ${issue.message}`)
			.join('; ');
		throw new Error(`Invalid environment variables (${problems}). See .env.example`);
	}
	return result.data;
}

export function parseThemeCookieDomain(value: unknown): string | undefined {
	const result = z.string().trim().min(1).safeParse(value);
	return result.success ? result.data : undefined;
}

export function getThemeCookieDomain(): string | undefined {
	return parseThemeCookieDomain(import.meta.env['VITE_THEME_COOKIE_DOMAIN']);
}

export interface PrivacyContact {
	name: string;
	email: string;
}

const privacyContactSchema = z.object({
	name: z.string().trim().min(1),
	email: z.email()
});

export function parsePrivacyContact(source: Record<string, unknown>): PrivacyContact | undefined {
	const result = privacyContactSchema.safeParse({
		name: source['VITE_PRIVACY_CONTROLLER_NAME'],
		email: source['VITE_PRIVACY_CONTACT_EMAIL']
	});
	return result.success ? result.data : undefined;
}

export function getPrivacyContact(): PrivacyContact | undefined {
	return parsePrivacyContact({ ...import.meta.env });
}

let cached: AppEnv | undefined;

export function getEnv(): AppEnv {
	cached ??= parseEnv({ ...import.meta.env });
	return cached;
}

import prettier from 'eslint-config-prettier';
import path from 'node:path';
import js from '@eslint/js';
import svelte from 'eslint-plugin-svelte';
import { defineConfig, includeIgnoreFile } from 'eslint/config';
import globals from 'globals';
import ts from 'typescript-eslint';

const gitignorePath = path.resolve(import.meta.dirname, '.gitignore');

export default defineConfig(
	includeIgnoreFile(gitignorePath),
	{ ignores: ['src/lib/api/schema.d.ts'] },
	js.configs.recommended,
	ts.configs.strictTypeChecked,
	ts.configs.stylisticTypeChecked,
	svelte.configs.recommended,
	prettier,
	svelte.configs.prettier,
	{
		languageOptions: { globals: { ...globals.browser, ...globals.node } },
		rules: {
			'no-undef': 'off'
		}
	},
	{
		files: ['**/*.ts'],
		rules: { 'prefer-const': 'error' }
	},
	{
		files: ['**/*.ts', '**/*.svelte', '**/*.svelte.ts'],
		languageOptions: {
			parserOptions: {
				projectService: true,
				extraFileExtensions: ['.svelte'],
				tsconfigRootDir: import.meta.dirname
			}
		},
		rules: {
			'@typescript-eslint/no-explicit-any': 'error',
			'@typescript-eslint/consistent-type-imports': 'error',
			'@typescript-eslint/explicit-function-return-type': [
				'error',
				{ allowExpressions: true, allowTypedFunctionExpressions: true }
			],
			'@typescript-eslint/no-non-null-assertion': 'error',
			'@typescript-eslint/switch-exhaustiveness-check': 'error',
			'@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
			eqeqeq: ['error', 'always'],
			'no-console': ['warn', { allow: ['warn', 'error'] }],
			'no-var': 'error'
		}
	},
	{
		files: ['**/*.svelte', '**/*.svelte.ts', '**/*.svelte.js'],
		languageOptions: {
			parserOptions: {
				projectService: true,
				extraFileExtensions: ['.svelte'],
				parser: ts.parser
			}
		}
	},
	{
		files: ['**/*.svelte'],
		rules: {
			'prefer-const': 'off',
			'svelte/prefer-const': 'error',
			'svelte/button-has-type': 'error',
			'svelte/no-at-html-tags': 'error',
			'svelte/require-each-key': 'error',
			'svelte/no-target-blank': 'error',
			'svelte/block-lang': ['error', { script: 'ts', style: null }]
		}
	},
	{
		files: ['*.config.js', '*.config.ts', 'keycloak-theme/**/*.{js,mjs}'],
		...ts.configs.disableTypeChecked
	}
);

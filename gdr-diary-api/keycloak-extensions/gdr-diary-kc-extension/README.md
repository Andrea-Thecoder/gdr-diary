# gdr-diary-kc-extension

Keycloak extension that replaces the built-in `UPDATE_EMAIL` required action.

Why: with the built-in action, a change of email that waits for its confirmation link keeps
`UPDATE_EMAIL` among the user's required actions. Every login (and the silent `check-sso` of the
SPA) is then stopped at Keycloak's "confirmation sent" page until the link is opened. This version
lets the user sign in and use the app while the change is pending; the pending address stays in
`kc.email.pending` and the confirmation link works as usual.

It also adds `POST /realms/<realm>/gdr-email/resend`, which sends the confirmation mail again to the
pending address (`kc.email.pending`). It needs the user's access token (`Authorization: Bearer`), allows
one send every 60 seconds and answers `sent`, `no_pending_email` (409), `too_soon` (429) or
`email_failed` (502). Cross-origin calls are allowed for the origins listed in the token.

`LatestUpdateEmailActionTokenHandler` replaces the handler of the confirmation link so that only the
latest link works: older ones (before a resend, or for an address that is no longer pending) are refused.

## Build and use

Docker is required: the build runs in a Maven container. From the root of `gdr-diary-api`:

    sh keycloak-extensions/build-keycloak-extensions.sh

The script compiles every extension and copies the jar to `docker-compose/keycloak/providers`. Then
recreate Keycloak so that it loads the new jar:

    cd docker-compose
    docker compose up -d --force-recreate keycloak

The `update-email` feature must be enabled in Keycloak (it is, in `docker-compose/docker-compose.yml`).

## Keycloak version

The jar is compiled against `keycloak.version` in this `pom.xml` (26.7.0), which must be the same
version as the Keycloak image in `docker-compose/docker-compose.yml`. After a Keycloak upgrade, change
both and rebuild: if the build fails, the error says which method changed.

See also the [Runbook](../../RUNBOOK.md#ricostruire-lestensione-di-keycloak).

# Swagger e OpenAPI: endpoint

Aggiornato il 27 ottobre 2026.

Il backend `gdr-diary-api` documenta l'API con springdoc-openapi, scritto dal codice (code first). Gira sulla porta 8080 con context path `/api/v1/gdr-diary-api`. Non ci sono property `springdoc.*` personalizzate, quindi i percorsi sono quelli di default prefissati dal context path. Sono pubblici (`permitAll` in `SecurityConfig`) e non serve il token: in produzione vanno chiusi o limitati.

Indirizzi in sviluppo locale:

- Swagger UI: http://localhost:8080/api/v1/gdr-diary-api/swagger-ui.html
- OpenAPI in JSON: http://localhost:8080/api/v1/gdr-diary-api/v3/api-docs
- OpenAPI in YAML: http://localhost:8080/api/v1/gdr-diary-api/v3/api-docs.yaml

## Endpoint attuali

Tutti richiedono un token. `/me` e `/me/**` bastano a un utente autenticato; gli altri richiedono il ruolo `user` o `admin` (403 altrimenti).

- `/me`: `GET`, `POST`, `DELETE`
- `/me/settings`: `PATCH`
- `/me/avatar`: `PUT`, `DELETE`
- `/characters`: `GET`, `POST`
- `/characters/{id}`: `GET`, `PUT`, `DELETE`
- `/characters/{id}/avatar`: `PUT`, `DELETE`
- `/characters/{characterId}/diaries`: `GET`, `POST`
- `/diaries/{id}`: `GET`, `PUT`, `DELETE`
- `/diaries/{id}/cover`: `PUT`, `DELETE`
- `/diaries/{diaryId}/pages`: `GET`, `POST`
- `/pages/{id}`: `PUT`, `DELETE`

Note:

- `PUT /me/email` non esiste più: l'email si allinea con `POST /me` e il cambio lo fa Keycloak.
- `DELETE /me` cancella i dati dell'utente nel backend. L'utente su Keycloak non viene toccato (vedi `claude/stato-lavori.md`, voce 1 di "Da fare").

## Copia del contratto

Il file `swagger.json` di questo Project è una copia dell'OpenAPI del 19 ottobre e non è aggiornata: contiene ancora `PUT /me/email` e non ha `DELETE /me`. Anche `openapi/swagger.json` del frontend (e quindi `src/lib/api/schema.d.ts`) va riallineato: ha `DELETE /me` scritto a mano e non ha le nuove descrizioni di `POST` e `PUT` delle pagine e del campo `content`, cambiate quando è stato introdotto il controllo del contenuto delle pagine.

Per riallinearlo, con l'app in esecuzione: scaricare `/v3/api-docs`, sostituire `openapi/swagger.json` nel frontend, lanciare `npm run api:generate` e aggiornare anche la copia in questo Project.

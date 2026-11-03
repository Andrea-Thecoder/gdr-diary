package it.gdr.diary.keycloak;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class ResendEmailChangeProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public ResendEmailChangeProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new ResendEmailChangeResource(session);
    }

    @Override
    public void close() {
    }
}

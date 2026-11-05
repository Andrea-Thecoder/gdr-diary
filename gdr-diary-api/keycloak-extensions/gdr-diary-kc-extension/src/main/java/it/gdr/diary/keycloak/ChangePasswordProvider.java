package it.gdr.diary.keycloak;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class ChangePasswordProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public ChangePasswordProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new ChangePasswordResource(session);
    }

    @Override
    public void close() {
    }
}

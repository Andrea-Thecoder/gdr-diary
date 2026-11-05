package it.gdr.diary.keycloak;

import java.util.HashMap;
import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.AuthenticationFlowModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderEvent;

/**
 * Turns on "Always set password on register form" in every realm that is created or imported.
 *
 * The realm JSON cannot set it without redefining all the authentication flows, and when it is off the
 * registration page does not ask for a password: the user is born without one and Keycloak makes them
 * choose it at the first login. This listener does nothing at runtime; it only registers, when
 * Keycloak starts, a handler for the creation of a realm.
 */
public class RegistrationPasswordDefaultsFactory implements EventListenerProviderFactory {

    public static final String ID = "gdr-registration-defaults";

    static final String FORM_FLOW = "registration form";
    static final String PASSWORD_AUTHENTICATOR = "registration-password-action";
    static final String CONFIG_ALIAS = "registration-password-always";
    static final String OPTION = "always_set_password_on_register_form";

    private static final Logger LOG = Logger.getLogger(RegistrationPasswordDefaultsFactory.class);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new EventListenerProvider() {
            @Override
            public void onEvent(Event event) {
            }

            @Override
            public void onEvent(AdminEvent event, boolean includeRepresentation) {
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(this::onProviderEvent);
    }

    @Override
    public void close() {
    }

    private void onProviderEvent(ProviderEvent event) {
        if (event instanceof RealmModel.RealmPostCreateEvent created) {
            apply(created.getCreatedRealm());
        }
    }

    static void apply(RealmModel realm) {
        try {
            AuthenticationFlowModel form = realm.getFlowByAlias(FORM_FLOW);
            if (form == null) {
                return;
            }
            realm.getAuthenticationExecutionsStream(form.getId())
                .filter(execution -> PASSWORD_AUTHENTICATOR.equals(execution.getAuthenticator()))
                .filter(execution -> execution.getAuthenticatorConfig() == null)
                .toList()
                .forEach(execution -> {
                    AuthenticatorConfigModel config = new AuthenticatorConfigModel();
                    config.setAlias(CONFIG_ALIAS);
                    Map<String, String> values = new HashMap<>();
                    values.put(OPTION, "true");
                    config.setConfig(values);
                    config = realm.addAuthenticatorConfig(config);
                    execution.setAuthenticatorConfig(config.getId());
                    realm.updateAuthenticatorExecution(execution);
                    LOG.infof("Realm %s: password required on the registration form", realm.getName());
                });
        } catch (RuntimeException cause) {
            LOG.warnf(cause, "Realm %s: could not turn on the password on the registration form; "
                + "turn it on by hand (see the Runbook)", realm.getName());
        }
    }
}

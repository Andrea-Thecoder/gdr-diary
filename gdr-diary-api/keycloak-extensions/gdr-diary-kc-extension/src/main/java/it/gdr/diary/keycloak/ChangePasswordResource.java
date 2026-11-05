package it.gdr.diary.keycloak;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.authentication.actiontoken.execactions.ExecuteActionsActionToken;
import org.keycloak.common.util.Time;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.Urls;
import org.keycloak.services.cors.Cors;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;

/**
 * Asks for a password change that only happens once the user opens the link in the email:
 * nothing changes when this endpoint answers, the new password is chosen on the Keycloak page
 * the link leads to.
 */
public class ChangePasswordResource {

    static final String ACTION = "UPDATE_PASSWORD";
    static final int COOLDOWN_SECONDS = 60;

    private static final String COOLDOWN_PREFIX = "gdr.password.request.";

    private final KeycloakSession session;

    public ChangePasswordResource(KeycloakSession session) {
        this.session = session;
    }

    @OPTIONS
    @Path("request")
    public Response preflight() {
        return Cors.builder()
            .preflight()
            .allowAllOrigins()
            .allowedMethods("POST")
            .auth()
            .add(Response.ok());
    }

    @POST
    @Path("request")
    @Produces(MediaType.APPLICATION_JSON)
    public Response request(@QueryParam("redirect_uri") String redirectUri) {
        KeycloakContext context = session.getContext();
        RealmModel realm = context.getRealm();
        AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session)
            .setRealm(realm)
            .setUriInfo(context.getUri())
            .setConnection(context.getConnection())
            .setHeaders(context.getRequestHeaders())
            .authenticate();
        if (auth == null) {
            return reply(null, Response.status(Response.Status.UNAUTHORIZED), "unauthorized");
        }
        AccessToken token = auth.token();
        UserModel user = auth.user();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return reply(token, Response.status(Response.Status.CONFLICT), "no_email");
        }
        String cooldown = COOLDOWN_PREFIX + user.getId();
        if (!session.singleUseObjects().putIfAbsent(cooldown, COOLDOWN_SECONDS)) {
            return reply(
                token,
                Response.status(429).header("Retry-After", String.valueOf(COOLDOWN_SECONDS)),
                "too_soon");
        }
        int lifespan = realm.getActionTokenGeneratedByUserLifespan(ExecuteActionsActionToken.TOKEN_TYPE);
        String clientId = auth.client().getClientId();
        ExecuteActionsActionToken actionToken = new ExecuteActionsActionToken(
            user.getId(), user.getEmail(), Time.currentTime() + lifespan, List.of(ACTION),
            allowedRedirect(auth.client(), redirectUri), clientId);
        String key = actionToken.serialize(session, realm, context.getUri());
        String link = Urls.actionTokenBuilder(context.getUri().getBaseUri(), key, clientId, "", "")
            .replaceQueryParam("tab_id")
            .replaceQueryParam("client_data")
            .build(realm.getName())
            .toString();
        try {
            session.getProvider(EmailTemplateProvider.class)
                .setRealm(realm)
                .setUser(user)
                .sendPasswordReset(link, TimeUnit.SECONDS.toMinutes(lifespan));
        } catch (EmailException cause) {
            session.singleUseObjects().remove(cooldown);
            return reply(token, Response.status(Response.Status.BAD_GATEWAY), "email_failed");
        }
        return reply(token, Response.ok(), "sent");
    }

    private static String allowedRedirect(ClientModel client, String candidate) {
        String root = client.getRootUrl();
        if (candidate == null || candidate.isBlank() || root == null || root.isBlank()) {
            return null;
        }
        String base = root.endsWith("/") ? root : root + "/";
        return candidate.equals(root) || candidate.startsWith(base) ? candidate : null;
    }

    private Response reply(AccessToken token, Response.ResponseBuilder builder, String code) {
        Cors cors = Cors.builder().auth().allowedMethods("POST");
        if (token != null) {
            cors = cors.checkAllowedOrigins(token);
        }
        return cors.add(builder.type(MediaType.APPLICATION_JSON).entity(Map.of("status", code)));
    }
}

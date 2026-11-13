package it.gdr.diary.services;

import it.gdr.diary.config.KeycloakAdminConfig;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;

    private final Keycloak keycloak;
    private final KeycloakAdminConfig config;
    private final Sleeper sleeper;

    public void deleteKeycloakUser(String keycloakId) {
        int attempts = config.getDeleteRetries() + 1;
        Duration delay = config.getDeleteRetryInitialDelay();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (tryDeleteKeycloakUser(keycloakId, attempt, attempts)) {
                return;
            }
            if (attempt < attempts) {
                if (!pause(delay)) {
                    break;
                }
                delay = delay.multipliedBy(2);
            }
        }
        log.error("Keycloak user {} could not be deleted, giving up", keycloakId);
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The account could not be deleted on the authentication server, try again later");
    }

    private boolean tryDeleteKeycloakUser(String keycloakId, int attempt, int attempts) {
        try (Response response = keycloak.realm(config.getRealm()).users().delete(keycloakId)) {
            int status = response.getStatus();
            if (status == NO_CONTENT || status == NOT_FOUND) {
                return true;
            }
            log.warn("Keycloak user {} not deleted (attempt {}/{}): HTTP {}", keycloakId, attempt, attempts, status);
        } catch (RuntimeException e) {
            log.warn("Keycloak user {} not deleted (attempt {}/{}): {}", keycloakId, attempt, attempts, e.toString());
        }
        return false;
    }

    private boolean pause(Duration delay) {
        try {
            sleeper.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to retry the Keycloak user deletion");
            return false;
        }
    }
}

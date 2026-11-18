package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.gdr.diary.config.KeycloakAdminConfig;
import it.gdr.diary.services.KeycloakUserService;
import it.gdr.diary.services.Sleeper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class KeycloakUserServiceTest {

    private static final String KEYCLOAK_ID = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final String REALM = "gdr-diary-dev";

    @Mock
    private Keycloak keycloak;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private Sleeper sleeper;

    private final KeycloakAdminConfig config = new KeycloakAdminConfig();

    private KeycloakUserService service;

    @BeforeEach
    void setUp() {
        config.setRealm(REALM);
        service = new KeycloakUserService(keycloak, config, sleeper);
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private static Response response(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        return response;
    }

    private void givenDeleteReturns(Response first, Response... others) {
        when(usersResource.delete(KEYCLOAK_ID)).thenReturn(first, others);
    }

    @Test
    void deleteKeycloakUser_noContent_callsKeycloakOnceWithoutWaitingAndClosesResponse() throws Exception {
        Response ok = response(204);
        givenDeleteReturns(ok);

        service.deleteKeycloakUser(KEYCLOAK_ID);

        verify(usersResource, times(1)).delete(KEYCLOAK_ID);
        verify(sleeper, never()).sleep(any());
        verify(ok).close();
    }

    @Test
    void deleteKeycloakUser_notFound_isConsideredDone() throws Exception {
        givenDeleteReturns(response(404));

        service.deleteKeycloakUser(KEYCLOAK_ID);

        verify(usersResource, times(1)).delete(KEYCLOAK_ID);
        verify(sleeper, never()).sleep(any());
    }

    @Test
    void deleteKeycloakUser_failsTwiceThenSucceeds_retriesWaitingTwiceAndClosesEveryResponse() throws Exception {
        Response first = response(500);
        Response second = response(503);
        Response third = response(204);
        givenDeleteReturns(first, second, third);

        service.deleteKeycloakUser(KEYCLOAK_ID);

        verify(usersResource, times(3)).delete(KEYCLOAK_ID);
        verify(sleeper, times(2)).sleep(any());
        verify(first).close();
        verify(second).close();
        verify(third).close();
    }

    @Test
    void deleteKeycloakUser_alwaysFails_triesFourTimesWithDoublingDelaysThenThrowsBadGateway() throws Exception {
        givenDeleteReturns(response(500));

        Throwable thrown = catchThrowable(() -> service.deleteKeycloakUser(KEYCLOAK_ID));

        verify(usersResource, times(4)).delete(KEYCLOAK_ID);
        InOrder order = inOrder(sleeper);
        order.verify(sleeper).sleep(Duration.ofMillis(500));
        order.verify(sleeper).sleep(Duration.ofSeconds(1));
        order.verify(sleeper).sleep(Duration.ofSeconds(2));
        order.verifyNoMoreInteractions();
        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                        .containsExactly(HttpStatus.BAD_GATEWAY, "The account could not be deleted on the authentication server, try again later"));
    }

    @Test
    void deleteKeycloakUser_customRetriesAndDelay_usesConfiguredValues() throws Exception {
        config.setDeleteRetries(1);
        config.setDeleteRetryInitialDelay(Duration.ofMillis(10));
        givenDeleteReturns(response(500));

        catchThrowable(() -> service.deleteKeycloakUser(KEYCLOAK_ID));

        verify(usersResource, times(2)).delete(KEYCLOAK_ID);
        verify(sleeper, times(1)).sleep(Duration.ofMillis(10));
    }

    @Test
    void deleteKeycloakUser_zeroRetries_callsKeycloakOnceWithoutWaitingAndThrows() throws Exception {
        config.setDeleteRetries(0);
        givenDeleteReturns(response(500));

        Throwable thrown = catchThrowable(() -> service.deleteKeycloakUser(KEYCLOAK_ID));

        verify(usersResource, times(1)).delete(KEYCLOAK_ID);
        verify(sleeper, never()).sleep(any());
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 403, 409, 500, 502, 503})
    void deleteKeycloakUser_unexpectedStatus_isRetried(int status) {
        givenDeleteReturns(response(status), response(204));

        service.deleteKeycloakUser(KEYCLOAK_ID);

        verify(usersResource, times(2)).delete(KEYCLOAK_ID);
    }

    static Stream<RuntimeException> clientFailures() {
        return Stream.of(new ProcessingException("connection refused"), new IllegalStateException("boom"));
    }

    @ParameterizedTest
    @MethodSource("clientFailures")
    void deleteKeycloakUser_clientThrows_isRetried(RuntimeException failure) {
        when(usersResource.delete(KEYCLOAK_ID)).thenThrow(failure).thenReturn(response(204));

        service.deleteKeycloakUser(KEYCLOAK_ID);

        verify(usersResource, times(2)).delete(KEYCLOAK_ID);
    }

    @Test
    void deleteKeycloakUser_alwaysThrows_throwsBadGateway() {
        when(usersResource.delete(KEYCLOAK_ID)).thenThrow(new ProcessingException("connection refused"));

        assertThatThrownBy(() -> service.deleteKeycloakUser(KEYCLOAK_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void deleteKeycloakUser_interruptedWhileWaiting_stopsRetryingRestoresFlagAndThrowsBadGateway() throws Exception {
        givenDeleteReturns(response(500));
        doThrow(new InterruptedException()).when(sleeper).sleep(eq(Duration.ofMillis(500)));

        Throwable thrown = catchThrowable(() -> service.deleteKeycloakUser(KEYCLOAK_ID));

        verify(usersResource, times(1)).delete(KEYCLOAK_ID);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}

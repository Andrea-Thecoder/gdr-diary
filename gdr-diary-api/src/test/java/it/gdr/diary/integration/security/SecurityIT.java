package it.gdr.diary.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.KeycloakBrowserLogin;
import it.gdr.diary.integration.support.TestContainers;
import it.gdr.diary.integration.support.TestJwt;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class SecurityIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "secret";
    private static final String NO_ROLE_USER = "norole1";
    private static final String NEW_USER = "newuser1";
    private static final String OTHER_CLIENT = "other-client";

    private static String noRoleUserId;
    private static String newUserId;
    private static String otherClientId;

    @LocalServerPort
    private int port;

    private static RealmResource realm() {
        return TestContainers.KEYCLOAK.getKeycloakAdminClient().realm(TestContainers.REALM);
    }

    private static String createKeycloakUser(String username) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmailVerified(true);
        user.setEnabled(true);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(PASSWORD);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));
        return CreatedResponseUtil.getCreatedId(realm().users().create(user));
    }

    @BeforeAll
    static void createKeycloakFixtures() {
        newUserId = createKeycloakUser(NEW_USER);
        noRoleUserId = createKeycloakUser(NO_ROLE_USER);
        String usersGroupId = realm().getGroupByPath("/users").getId();
        realm().users().get(noRoleUserId).leaveGroup(usersGroupId);

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(OTHER_CLIENT);
        client.setPublicClient(true);
        client.setDirectAccessGrantsEnabled(true);
        client.setStandardFlowEnabled(false);
        otherClientId = CreatedResponseUtil.getCreatedId(realm().clients().create(client));
    }

    @AfterAll
    static void deleteKeycloakFixtures() {
        realm().users().delete(noRoleUserId).close();
        realm().users().delete(newUserId).close();
        realm().clients().get(otherClientId).remove();
    }

    private static String accessToken(String clientId, String username) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", PASSWORD);
        String response = RestClient.create().post()
                .uri(TestContainers.issuerUri() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        return JsonPath.read(response, "$.access_token");
    }

    private static String claims(String token) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String keycloakUserId(String username) {
        return realm().users().searchByUsername(username, true).getFirst().getId();
    }

    @Test
    void accessToken_frontendClient_containsBackendAudience() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(JsonPath.<Object>read(claims(token), "$.aud").toString()).contains("gdr-diary-backend");
    }

    @Test
    void accessToken_frontendClient_containsRealmRoles() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(JsonPath.<List<String>>read(claims(token), "$.realm_access.roles")).contains("user");
    }

    @Test
    void accessToken_newUser_receivesUserRoleFromDefaultGroup() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NEW_USER);

        assertThat(JsonPath.<List<String>>read(claims(token), "$.realm_access.roles")).contains("user");
    }

    @Test
    void accessToken_userOutsideDefaultGroup_hasNoApplicationRole() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(JsonPath.<List<String>>read(claims(token), "$.realm_access.roles")).doesNotContain("user", "admin");
    }

    @Test
    void findAllGameCharacters_noToken_returnsUnauthorized() {
        assertThat(mvc.get().uri("/characters")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void findAllGameCharacters_userToken_returnsOk() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(mvc.get().uri("/characters").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    @Test
    void findAllGameCharacters_adminToken_returnsOk() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "admin1");

        assertThat(mvc.get().uri("/characters").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    @Test
    void findAllGameCharacters_tokenWithoutRoles_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.get().uri("/characters").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void findAllGameCharacters_tokenOfClientWithoutAudience_returnsUnauthorized() {
        String token = accessToken(OTHER_CLIENT, "user1");

        assertThat(mvc.get().uri("/characters").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void findAllGameCharacters_forgedSignature_returnsUnauthorized() {
        String[] userToken = accessToken(TestContainers.FRONTEND_CLIENT, "user1").split("\\.");
        String[] adminToken = accessToken(TestContainers.FRONTEND_CLIENT, "admin1").split("\\.");
        String forged = userToken[0] + "." + userToken[1] + "." + adminToken[2];

        assertThat(mvc.get().uri("/characters").header(HttpHeaders.AUTHORIZATION, bearer(forged))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void findAllGameCharacters_tamperedClaims_returnsUnauthorized() {
        String[] parts = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER).split("\\.");
        String elevated = claims(String.join(".", parts)).replace("\"realm_access\":{\"roles\":[", "\"realm_access\":{\"roles\":[\"admin\",");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(elevated.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThat(mvc.get().uri("/characters").header(HttpHeaders.AUTHORIZATION, bearer(tampered))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createAppUser_tokenWithoutRoles_returnsOk() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.post().uri("/me").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    @Test
    void createAppUser_keycloakToken_storesKeycloakUserIdAndEmail() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        mvc.post().uri("/me").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();

        assertThat(jdbc.queryForMap("select keycloak_id, email from app_user"))
                .containsEntry("keycloak_id", keycloakUserId("user1"))
                .containsEntry("email", "user1@example.com");
    }

    @Test
    void accessToken_browserLoginWithPkce_containsRecentAuthTime() {
        String token = KeycloakBrowserLogin.accessToken("user1", PASSWORD);

        long authTime = ((Number) JsonPath.read(claims(token), "$.auth_time")).longValue();

        assertThat(Instant.ofEpochSecond(authTime)).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
    }

    @Test
    void deleteAppUser_freshBrowserLogin_returnsOk() {
        String token = KeycloakBrowserLogin.accessToken(NEW_USER, PASSWORD);
        mvc.post().uri("/me").header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();

        assertThat(mvc.delete().uri("/me").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    @Test
    void createAppUser_noToken_returnsUnauthorized() {
        assertThat(mvc.post().uri("/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteAppUser_noToken_returnsUnauthorized() {
        assertThat(mvc.delete().uri("/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getApiDocs_noToken_returnsOk() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }

    @Test
    void getDiaryById_tokenWithoutRoles_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.get().uri("/diaries/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void findAllDiaryPagesByDiary_tokenWithoutRoles_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.get().uri("/diaries/{id}/pages", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteDiaryPage_tokenWithoutRoles_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.delete().uri("/pages/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void preflight_allowedOrigin_returnsAllowOriginHeader() {
        assertThat(mvc.options().uri("/characters")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .headers().hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    @Test
    void getActuatorHealth_noToken_returnsOk() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void getActuatorHealth_noToken_hidesDetails() {
        assertThat(mvc.get().uri("/actuator/health")).bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void getActuatorHealth_userToken_hidesDetails() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(mvc.get().uri("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void getActuatorHealth_adminToken_showsDetails() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "admin1");

        assertThat(mvc.get().uri("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .bodyJson().hasPath("$.components.db");
    }

    @Test
    void getActuatorPrometheus_noToken_returnsUnauthorized() {
        assertThat(mvc.get().uri("/actuator/prometheus")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getActuatorMetrics_noToken_returnsUnauthorized() {
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getActuatorHealth_tokenWithoutRoles_returnsOk() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.get().uri("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    @Test
    void getActuatorPrometheus_tokenWithoutRoles_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.get().uri("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void getActuatorMetrics_tokenWithoutRoles_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        assertThat(mvc.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void getActuatorHealth_userToken_returnsOk() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(mvc.get().uri("/actuator/health").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    @Test
    void getSwaggerUiIndex_noToken_returnsOk() {
        assertThat(mvc.get().uri("/swagger-ui/index.html")).hasStatusOk();
    }

    @Test
    void getGameCharacterById_adminTokenOnOtherUsersCharacter_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "admin1");

        assertThat(mvc.get().uri("/characters/{id}", othersCharacter).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private String exposedHeadersOfStepUpResponse() {
        return mvc.delete().uri("/me")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .with(TestJwt.authenticatedAt(Instant.now().minus(Duration.ofMinutes(10))))
                .exchange().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
    }

    @Test
    void preflight_deleteMe_allowsDeleteMethod() {
        String allowedMethods = mvc.options().uri("/me")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")
                .exchange().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);

        assertThat(allowedMethods).contains("DELETE");
    }

    @Test
    void deleteAppUser_crossOriginOldLogin_returnsAllowOriginHeader() {
        assertThat(mvc.delete().uri("/me")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .with(TestJwt.authenticatedAt(Instant.now().minus(Duration.ofMinutes(10)))))
                .headers().hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
    }

    @Test
    void deleteAppUser_crossOriginOldLogin_exposesWwwAuthenticateHeader() {
        assertThat(exposedHeadersOfStepUpResponse()).contains(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void getActuatorPrometheus_userToken_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(mvc.get().uri("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void getActuatorPrometheus_adminToken_returnsNotFound() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "admin1");

        assertThat(mvc.get().uri("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getActuatorMetrics_userToken_returnsForbidden() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        assertThat(mvc.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void getActuatorMetrics_adminToken_returnsOk() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "admin1");

        assertThat(mvc.get().uri("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, bearer(token))).hasStatusOk();
    }

    static Stream<Arguments> meEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/me"),
                Arguments.of(HttpMethod.POST, "/me"),
                Arguments.of(HttpMethod.PATCH, "/me/settings"),
                Arguments.of(HttpMethod.PUT, "/me/avatar"),
                Arguments.of(HttpMethod.DELETE, "/me/avatar"));
    }

    private HttpResponse<String> realGet(String path, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/gdr-diary-api" + path)).GET();
        Stream.iterate(0, index -> index < headers.length, index -> index + 2)
                .forEach(index -> request.header(headers[index], headers[index + 1]));
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("meEndpoints")
    void meEndpoint_tokenWithoutRoles_isNotForbidden(HttpMethod method, String path) {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        MvcTestResult result = mvc.method(method).uri(path).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void getSwaggerConfig_noToken_returnsOk() {
        assertThat(mvc.get().uri("/v3/api-docs/swagger-config")).hasStatusOk();
    }

    @Test
    void findAllGameCharacters_userTokenOverRealHttp_setsNoSessionCookie() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, "user1");

        HttpResponse<String> response = realGet("/characters", HttpHeaders.AUTHORIZATION, bearer(token));

        assertThat(response.headers().allValues(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void findAllGameCharacters_noTokenOverRealHttp_setsNoSessionCookie() {
        HttpResponse<String> response = realGet("/characters");

        assertThat(response.headers().allValues(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void findAllGameCharacters_tokenWithoutRolesOverRealHttp_setsNoSessionCookie() {
        String token = accessToken(TestContainers.FRONTEND_CLIENT, NO_ROLE_USER);

        HttpResponse<String> response = realGet("/characters", HttpHeaders.AUTHORIZATION, bearer(token));

        assertThat(response.headers().allValues(HttpHeaders.SET_COOKIE)).isEmpty();
    }
}

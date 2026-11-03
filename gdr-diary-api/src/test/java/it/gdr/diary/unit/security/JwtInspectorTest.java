package it.gdr.diary.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.gdr.diary.security.AppRole;
import it.gdr.diary.security.JwtInspector;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class JwtInspectorTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

    private final JwtInspector inspector = new JwtInspector();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwtWithRealmAccess(Object realmAccess) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).claim("realm_access", realmAccess).build();
    }

    private static Jwt jwtWithRoles(String... roles) {
        return jwtWithRealmAccess(Map.of("roles", List.of(roles)));
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @Test
    void extractRoles_knownRealmRoles_returnsRoles() {
        Set<AppRole> roles = JwtInspector.extractRoles(jwtWithRoles("user", "admin"));

        assertThat(roles).containsExactlyInAnyOrder(AppRole.USER, AppRole.ADMIN);
    }

    @Test
    void extractRoles_mixedCaseRoles_matchesIgnoringCase() {
        Set<AppRole> roles = JwtInspector.extractRoles(jwtWithRoles("User", "ADMIN"));

        assertThat(roles).containsExactlyInAnyOrder(AppRole.USER, AppRole.ADMIN);
    }

    @Test
    void extractRoles_unknownRoles_discardsThem() {
        Set<AppRole> roles = JwtInspector.extractRoles(jwtWithRoles("user", "offline_access", "uma_authorization", "master"));

        assertThat(roles).containsExactly(AppRole.USER);
    }

    @Test
    void extractRoles_nonStringRoles_ignoresThem() {
        Set<AppRole> roles = JwtInspector.extractRoles(jwtWithRealmAccess(Map.of("roles", List.of(1, Map.of("name", "admin"), "user"))));

        assertThat(roles).containsExactly(AppRole.USER);
    }

    @Test
    void extractRoles_rolesNotAList_returnsEmpty() {
        Set<AppRole> roles = JwtInspector.extractRoles(jwtWithRealmAccess(Map.of("roles", "admin")));

        assertThat(roles).isEmpty();
    }

    @Test
    void extractRoles_realmAccessWithoutRoles_returnsEmpty() {
        Set<AppRole> roles = JwtInspector.extractRoles(jwtWithRealmAccess(Map.of()));

        assertThat(roles).isEmpty();
    }

    @Test
    void extractRoles_noRealmAccessClaim_returnsEmpty() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();

        Set<AppRole> roles = JwtInspector.extractRoles(jwt);

        assertThat(roles).isEmpty();
    }

    @Test
    void getSubject_jwtAuthentication_returnsSubClaim() {
        authenticate(jwtWithRoles("user"));

        String subject = inspector.getSubject();

        assertThat(subject).isEqualTo(SUBJECT);
    }

    @Test
    void getEmail_jwtWithEmailClaim_returnsEmail() {
        authenticate(Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).claim("email", "frodo@shire.example").build());

        String email = inspector.getEmail();

        assertThat(email).isEqualTo("frodo@shire.example");
    }

    @Test
    void getEmail_jwtWithoutEmailClaim_returnsNull() {
        authenticate(jwtWithRoles("user"));

        String email = inspector.getEmail();

        assertThat(email).isNull();
    }

    @Test
    void getSubject_noAuthentication_throwsUnauthorized() {
        assertThatThrownBy(inspector::getSubject)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getSubject_principalNotJwt_throwsUnauthorized() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("frodo", "n/a", List.of()));

        assertThatThrownBy(inspector::getSubject)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void getAuthTime_tokenWithAuthTime_returnsInstant() {
        authenticate(Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).claim("auth_time", 1793117403L).build());

        Optional<Instant> authTime = inspector.getAuthTime();

        assertThat(authTime).contains(Instant.ofEpochSecond(1793117403L));
    }

    @Test
    void getAuthTime_tokenWithoutAuthTime_returnsEmpty() {
        authenticate(jwtWithRoles("user"));

        Optional<Instant> authTime = inspector.getAuthTime();

        assertThat(authTime).isEmpty();
    }
}

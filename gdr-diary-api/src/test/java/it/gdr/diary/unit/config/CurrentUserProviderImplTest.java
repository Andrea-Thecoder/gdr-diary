package it.gdr.diary.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.config.CurrentUserProviderImpl;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserProviderImplTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";

    private final CurrentUserProviderImpl provider = new CurrentUserProviderImpl();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token").header("alg", "none").claim("sub", subject).build();
    }

    @Test
    void currentUser_jwtAuthentication_returnsSubject() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithSubject(SUBJECT), List.of()));

        Object user = provider.currentUser();

        assertThat(user).isEqualTo(SUBJECT);
    }

    @Test
    void getCurrentUser_noAuthentication_returnsPlaceholder() {
        String user = provider.getCurrentUser();

        assertThat(user).isEqualTo("N/A");
    }

    @Test
    void getCurrentUser_notAuthenticated_returnsPlaceholder() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("frodo", "secret"));

        String user = provider.getCurrentUser();

        assertThat(user).isEqualTo("N/A");
    }

    @Test
    void getCurrentUser_principalNotJwt_returnsPlaceholder() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("frodo", "n/a", List.of()));

        String user = provider.getCurrentUser();

        assertThat(user).isEqualTo("N/A");
    }

    @Test
    void getCurrentUser_blankSubject_returnsPlaceholder() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtWithSubject(" "), List.of()));

        String user = provider.getCurrentUser();

        assertThat(user).isEqualTo("N/A");
    }
}

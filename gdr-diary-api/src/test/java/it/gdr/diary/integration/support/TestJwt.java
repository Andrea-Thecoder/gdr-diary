package it.gdr.diary.integration.support;

import it.gdr.diary.security.KeycloakRoleConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class TestJwt {

    public static final String USER_SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    public static final String USER_EMAIL = "frodo@shire.example";
    public static final String OTHER_SUBJECT = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";
    public static final String OTHER_EMAIL = "sam@shire.example";

    private TestJwt() {
    }

    public static JwtAuthenticationToken authentication(String subject, String email, String... roles) {
        return authentication(subject, email, Instant.now(), roles);
    }

    public static JwtAuthenticationToken authentication(String subject, String email, Instant authTime, String... roles) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .audience(List.of("gdr-diary-backend"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
        if (authTime != null) {
            builder.claim("auth_time", authTime.getEpochSecond());
        }
        Jwt jwt = builder.build();
        return new JwtAuthenticationToken(jwt, new KeycloakRoleConverter().convert(jwt), subject);
    }

    public static RequestPostProcessor as(String subject, String email, String... roles) {
        return SecurityMockMvcRequestPostProcessors.authentication(authentication(subject, email, roles));
    }

    public static RequestPostProcessor authenticatedAt(Instant authTime) {
        return SecurityMockMvcRequestPostProcessors.authentication(authentication(USER_SUBJECT, USER_EMAIL, authTime, "user"));
    }

    public static RequestPostProcessor otherUser() {
        return as(OTHER_SUBJECT, OTHER_EMAIL, "user");
    }
}

package it.gdr.diary.unit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.gdr.diary.config.SecurityConfig;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigCorsTest {

    private static final String URI = "/controller/v1/gdr-diary-controller/characters";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private SecurityConfig configWith(List<String> origins) {
        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAllowedOrigins(origins);
        securityConfig.setAllowedMethods(List.of("GET", "POST"));
        securityConfig.setAllowedHeaders(List.of("Authorization"));
        return securityConfig;
    }

    private CorsConfiguration configFor(SecurityConfig securityConfig) {
        return securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", URI));
    }

    @Test
    void corsConfigurationSource_configuredOrigin_allowsThatOrigin() {
        CorsConfiguration config = configFor(configWith(List.of(ALLOWED_ORIGIN)));

        String allowed = config.checkOrigin(ALLOWED_ORIGIN);

        assertThat(allowed).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void corsConfigurationSource_otherOrigin_rejectsOrigin() {
        CorsConfiguration config = configFor(configWith(List.of(ALLOWED_ORIGIN)));

        String allowed = config.checkOrigin("http://evil.example");

        assertThat(allowed).isNull();
    }

    @Test
    void corsConfigurationSource_configuredOrigin_disallowsCredentials() {
        CorsConfiguration config = configFor(configWith(List.of(ALLOWED_ORIGIN)));

        assertThat(config.getAllowCredentials()).isFalse();
    }

    @Test
    void corsConfigurationSource_noOrigins_disablesCors() {
        CorsConfiguration config = configFor(configWith(List.of()));

        assertThat(config).isNull();
    }

    @Test
    void corsConfigurationSource_wildcardOrigin_throwsIllegalStateException() {
        SecurityConfig securityConfig = configWith(List.of("*"));

        assertThatThrownBy(securityConfig::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corsConfigurationSource_wildcardOrigin_explainsTheProblemInEnglish() {
        SecurityConfig securityConfig = configWith(List.of("*"));

        assertThatThrownBy(securityConfig::corsConfigurationSource)
                .hasMessage("app.cors.allowed-origins: '*' is not allowed, list each origin explicitly");
    }
}

package it.gdr.diary.config;

import it.gdr.diary.services.Sleeper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.keycloak-admin")
@Validated
public class KeycloakAdminConfig {

    @NotBlank
    private String serverUrl;
    @NotBlank
    private String realm;
    @NotBlank
    private String clientId;
    @NotBlank
    private String clientSecret;
    @Min(0)
    private int deleteRetries = 3;
    @NotNull
    @DurationMin(millis = 1)
    private Duration deleteRetryInitialDelay = Duration.ofMillis(500);

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    @Bean
    public Sleeper sleeper() {
        return delay -> Thread.sleep(delay);
    }
}

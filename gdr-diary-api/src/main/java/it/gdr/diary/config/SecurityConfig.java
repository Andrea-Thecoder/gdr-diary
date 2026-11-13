package it.gdr.diary.config;

import it.gdr.diary.security.AppRole;
import it.gdr.diary.security.KeycloakRoleConverter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Configuration
@EnableWebSecurity
@ConfigurationProperties(prefix = "app.cors")
public class SecurityConfig {

    private List<String> allowedOrigins = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "Accept");
    private List<String> exposedHeaders = List.of(HttpHeaders.WWW_AUTHENTICATE);
    private Duration maxAge = Duration.ofHours(1);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)  {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole(AppRole.ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/feedback").permitAll()
                        .requestMatchers("/feedback", "/feedback/**").hasRole(AppRole.ADMIN.name())
                        .requestMatchers("/me/payments", "/me/payments/**").hasAnyRole(AppRole.names())
                        .requestMatchers("/me", "/me/**").authenticated()
                        .anyRequest().hasAnyRole(AppRole.names()))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        if (allowedOrigins.isEmpty()) {
            return source;
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException("app.cors.allowed-origins: '*' is not allowed, list each origin explicitly");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(allowedMethods);
        config.setAllowedHeaders(allowedHeaders);
        config.setExposedHeaders(exposedHeaders);
        config.setMaxAge(maxAge);
        config.setAllowCredentials(false);

        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

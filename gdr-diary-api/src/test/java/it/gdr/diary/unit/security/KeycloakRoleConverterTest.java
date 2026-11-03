package it.gdr.diary.unit.security;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.security.KeycloakRoleConverter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    private static Jwt jwtWithRoles(String... roles) {
        return Jwt.withTokenValue("token").header("alg", "none").subject("sub")
                .claim("realm_access", Map.of("roles", List.of(roles))).build();
    }

    @Test
    void convert_knownRealmRoles_returnsPrefixedAuthorities() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRoles("user", "admin"));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void convert_lowerCaseRole_returnsUpperCaseAuthority() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRoles("user"));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void convert_onlyUnknownRoles_returnsNoAuthorities() {
        Collection<GrantedAuthority> authorities = converter.convert(jwtWithRoles("offline_access", "game_master"));

        assertThat(authorities).isEmpty();
    }
}

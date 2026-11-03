package it.gdr.diary.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class JwtInspector {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    public static Set<AppRole> extractRoles(Jwt jwt) {
        Set<AppRole> result = EnumSet.noneOf(AppRole.class);
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess != null && realmAccess.get(ROLES) instanceof List<?> roles) {
            for (Object role : roles) {
                if (role instanceof String name) {
                    AppRole.fromClaim(name).ifPresent(result::add);
                }
            }
        }
        return result;
    }

    public String getSubject() {
        return getJwt().getSubject();
    }

    public String getEmail() {
        return getJwt().getClaimAsString("email");
    }

    public Optional<Instant> getAuthTime() {
        return Optional.ofNullable(getJwt().getClaimAsInstant("auth_time"));
    }

    private Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}

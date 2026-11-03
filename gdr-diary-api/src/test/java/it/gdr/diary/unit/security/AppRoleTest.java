package it.gdr.diary.unit.security;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.security.AppRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppRoleTest {

    @ParameterizedTest
    @CsvSource({"user,USER", "USER,USER", "User,USER", "admin,ADMIN", "aDmIn,ADMIN"})
    void fromClaim_knownRoleInAnyCase_returnsRole(String claim, AppRole expected) {
        Optional<AppRole> role = AppRole.fromClaim(claim);

        assertThat(role).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"master", "offline_access", " user", "ROLE_USER"})
    void fromClaim_unknownRole_returnsEmpty(String claim) {
        Optional<AppRole> role = AppRole.fromClaim(claim);

        assertThat(role).isEmpty();
    }

    @Test
    void names_always_returnsAllRoleNames() {
        String[] names = AppRole.names();

        assertThat(names).containsExactly("USER", "ADMIN");
    }
}

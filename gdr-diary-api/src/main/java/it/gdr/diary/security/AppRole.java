package it.gdr.diary.security;

import java.util.Arrays;
import java.util.Optional;

public enum AppRole {

    USER,
    ADMIN;

    public static Optional<AppRole> fromClaim(String value) {
        return Arrays.stream(values()).filter(role -> role.name().equalsIgnoreCase(value)).findFirst();
    }

    public static String[] names() {
        return Arrays.stream(values()).map(Enum::name).toArray(String[]::new);
    }
}

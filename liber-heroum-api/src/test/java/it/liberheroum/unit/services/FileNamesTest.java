package it.liberheroum.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import it.liberheroum.services.FileNames;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class FileNamesTest {

    @ParameterizedTest
    @CsvSource({
            "Alira dei Salici, alira-dei-salici",
            "Élodie l'Ombra, elodie-l-ombra",
            "  --Thorin!!  Scudodiquercia--  , thorin-scudodiquercia",
            "Capitolo 1: L'agguato, capitolo-1-l-agguato",
            "龍, fallback",
            "'', fallback"
    })
    void slug_name_keepsOnlyLowercaseLettersDigitsAndDashes(String name, String slug) {
        assertThat(FileNames.slug(name, 40, "fallback")).isEqualTo(slug);
    }

    @ParameterizedTest
    @NullSource
    void slug_missingName_returnsFallback(String name) {
        assertThat(FileNames.slug(name, 40, "fallback")).isEqualTo("fallback");
    }

    @ParameterizedTest
    @CsvSource({"abcdefghij, 5, abcde", "abcd efghij, 5, abcd"})
    void slug_longName_isCutWithoutTrailingDash(String name, int maxLength, String slug) {
        assertThat(FileNames.slug(name, maxLength, "fallback")).isEqualTo(slug);
    }
}

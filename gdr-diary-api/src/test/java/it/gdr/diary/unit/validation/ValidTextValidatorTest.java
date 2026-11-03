package it.gdr.diary.unit.validation;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.validation.ValidTextValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidTextValidatorTest {

    private final ValidTextValidator validator = new ValidTextValidator();

    @Test
    void isValid_null_returnsTrue() {
        boolean valid = validator.isValid(null, null);

        assertThat(valid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Aragorn", "riga\nnuova", "èà", "\uD83D\uDE00"})
    void isValid_storableText_returnsTrue(String text) {
        boolean valid = validator.isValid(text, null);

        assertThat(valid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "a\u0000b", "Aragorn\u0000"})
    void isValid_textWithNul_returnsFalse(String text) {
        boolean valid = validator.isValid(text, null);

        assertThat(valid).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800", "a\uDC00", "\uD800x", "x\uDBFF"})
    void isValid_loneSurrogate_returnsFalse(String text) {
        boolean valid = validator.isValid(text, null);

        assertThat(valid).isFalse();
    }
}

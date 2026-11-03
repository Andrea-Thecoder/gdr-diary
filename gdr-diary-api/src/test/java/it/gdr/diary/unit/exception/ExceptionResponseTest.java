package it.gdr.diary.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.exception.ExceptionResponse;
import org.junit.jupiter.api.Test;

class ExceptionResponseTest {

    @Test
    void constructor_withMessage_createsSingleViolationWithoutField() {
        ExceptionResponse response = new ExceptionResponse(400, "BAD_REQUEST", "Empty file");

        assertThat(response.getViolations()).singleElement()
                .extracting(ExceptionResponse.Violation::getField, ExceptionResponse.Violation::getMessage)
                .containsExactly(null, "Empty file");
    }

    @Test
    void addViolation_existingViolations_appendsInOrder() {
        ExceptionResponse response = new ExceptionResponse(400, "BAD_REQUEST", "first");

        response.addViolation("second");

        assertThat(response.getViolations()).extracting(ExceptionResponse.Violation::getMessage).containsExactly("first", "second");
    }

    @Test
    void addViolation_noViolations_createsList() {
        ExceptionResponse response = new ExceptionResponse();

        response.addViolation("only");

        assertThat(response.getViolations()).extracting(ExceptionResponse.Violation::getMessage).containsExactly("only");
    }
}

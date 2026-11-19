package it.liberheroum.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import it.liberheroum.exception.ExceptionMapper;
import it.liberheroum.exception.ExceptionResponse;
import it.liberheroum.exception.RecentAuthenticationRequiredException;
import it.liberheroum.exception.ServiceException;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;

class ExceptionMapperTest {

    private final ExceptionMapper mapper = new ExceptionMapper();

    private static String firstViolation(ResponseEntity<ExceptionResponse> response) {
        return response.getBody().getViolations().getFirst().getMessage();
    }

    private static List<Object> summary(ResponseEntity<ExceptionResponse> response) {
        return List.of(response.getStatusCode().value(), response.getBody().getStatus(), response.getBody().getTitle(), firstViolation(response));
    }

    @Test
    void mapServiceException_businessError_returnsBadRequestWithMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapServiceException(new ServiceException("The diary is full (100 pages)"));

        assertThat(summary(response)).containsExactly(400, 400, "BAD_REQUEST", "The diary is full (100 pages)");
    }

    @Test
    void mapNotFoundException_notFound_returnsNotFoundWithBareTitleAndReason() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        assertThat(summary(response)).containsExactly(404, 404, "NOT_FOUND", "Diary not found");
    }

    @Test
    void mapNotFoundException_conflict_returnsConflictWithTitleAndReason() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(
                new ResponseStatusException(HttpStatus.CONFLICT, "The diary changed while it was being deleted, try again"));

        assertThat(summary(response)).containsExactly(409, 409, "CONFLICT", "The diary changed while it was being deleted, try again");
    }

    @Test
    void mapNotFoundException_withoutReason_returnsReasonPhraseAsViolation() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(firstViolation(response)).isEqualTo("Not Found");
    }

    @Test
    void mapNotFoundException_nonStandardStatus_returnsNumericTitle() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatusCode.valueOf(499)));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, body -> body.getViolations().getFirst().getMessage())
                .containsExactly("499", "499");
    }

    @Test
    void mapAuthException_authenticationFailure_returnsUnauthorizedHidingOriginalMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapAuthException(new BadCredentialsException("signature mismatch for kid 42"));

        assertThat(List.of(response.getStatusCode().value(), firstViolation(response))).containsExactly(401, "Authentication required");
    }

    @Test
    void mapForbiddenException_accessDenied_returnsForbidden() {
        ResponseEntity<ExceptionResponse> response = mapper.mapForbiddenException(new AccessDeniedException("no role"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void mapInvalidFormatException_invalidValue_returnsBadRequest() {
        ResponseEntity<ExceptionResponse> response = mapper.mapInvalidFormatException(mock(InvalidFormatException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapJsonParseException_malformedJson_returnsGenericMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapJsonParseException(mock(StreamReadException.class));

        assertThat(firstViolation(response)).isEqualTo("Malformed JSON request");
    }

    @Test
    void mapHttpMessageNotReadableException_malformedJson_returnsBadRequestWithGenericMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", mock(StreamReadException.class), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ExceptionResponse> response = mapper.mapHttpMessageNotReadableException(ex);

        assertThat(List.of(response.getStatusCode().value(), firstViolation(response))).containsExactly(400, "Malformed JSON request");
    }

    @Test
    void mapHttpMessageNotReadableException_invalidFormatCause_returnsInvalidFieldMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad enum", mock(InvalidFormatException.class), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ExceptionResponse> response = mapper.mapHttpMessageNotReadableException(ex);

        assertThat(firstViolation(response)).isEqualTo("Invalid value");
    }

    @Test
    void mapInvalidFormatException_unknownEnumValue_namesTheFieldWithoutClassNames() {
        InvalidFormatException ex = InvalidFormatException.from(null, "not one of the values accepted", "X", Object.class);
        ex.prependPath(new Object(), "kind");

        ResponseEntity<ExceptionResponse> response = mapper.mapInvalidFormatException(ex);

        assertThat(response.getBody().getViolations())
                .extracting(ExceptionResponse.Violation::getField, ExceptionResponse.Violation::getMessage)
                .containsExactly(tuple("kind", "Invalid value"));
    }

    @Test
    void mapInvalidFormatException_valueInsideList_namesNestedPath() {
        InvalidFormatException ex = InvalidFormatException.from(null, "bad", "X", Object.class);
        ex.prependPath(new Object(), 2);
        ex.prependPath(new Object(), "tags");

        ResponseEntity<ExceptionResponse> response = mapper.mapInvalidFormatException(ex);

        assertThat(response.getBody().getViolations()).extracting(ExceptionResponse.Violation::getField)
                .containsExactly("tags.[2]");
    }

    @Test
    void mapMethodArgumentTypeMismatchException_invalidUuid_returnsBadRequestNamingParameter() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null, null);

        ResponseEntity<ExceptionResponse> response = mapper.mapMethodArgumentTypeMismatchException(ex);

        assertThat(List.of(response.getStatusCode().value(), firstViolation(response))).containsExactly(400, "Invalid value for parameter: id");
    }

    private static BindException bindException(FieldError... errors) {
        BindException ex = new BindException(new Object(), "request");
        for (FieldError error : errors) {
            ex.addError(error);
        }
        return ex;
    }

    @Test
    void mapBindException_invalidField_returnsBadRequestInBody() {
        BindException ex = bindException(new FieldError("request", "name", "must not be blank"));

        ResponseEntity<ExceptionResponse> response = mapper.mapBindException(ex);

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, ExceptionResponse::getStatus)
                .containsExactly("BAD_REQUEST", 400);
    }

    @Test
    void mapBindException_invalidFields_returnsOneViolationPerFieldSortedByField() {
        BindException ex = bindException(
                new FieldError("request", "title", "size must be between 0 and 255"),
                new FieldError("request", "coverColor", "must match \"^#[0-9a-fA-F]{6}$\""));

        ResponseEntity<ExceptionResponse> response = mapper.mapBindException(ex);

        assertThat(response.getBody().getViolations())
                .extracting(ExceptionResponse.Violation::getField, ExceptionResponse.Violation::getMessage)
                .containsExactly(
                        tuple("coverColor", "must match \"^#[0-9a-fA-F]{6}$\""),
                        tuple("title", "size must be between 0 and 255"));
    }

    @Test
    void mapBindException_valueNotConvertible_hidesConversionDetails() {
        BindException ex = bindException(
                new FieldError("request", "page", "abc", true, new String[]{"typeMismatch"}, null, "Failed to convert value of type 'java.lang.String'"));

        ResponseEntity<ExceptionResponse> response = mapper.mapBindException(ex);

        assertThat(firstViolation(response)).isEqualTo("Invalid value");
    }

    @Test
    void mapBindException_objectError_returnsViolationWithoutField() {
        BindException ex = bindException();
        ex.addError(new ObjectError("request", "dates are not consistent"));

        ResponseEntity<ExceptionResponse> response = mapper.mapBindException(ex);

        assertThat(response.getBody().getViolations())
                .extracting(ExceptionResponse.Violation::getField, ExceptionResponse.Violation::getMessage)
                .containsExactly(tuple(null, "dates are not consistent"));
    }

    @Test
    void mapMissingServletRequestPartException_missingFile_returnsBadRequestNamingThePart() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMissingServletRequestPartException(new MissingServletRequestPartException("file"));

        assertThat(response.getBody()).satisfies(
                body -> assertThat(body).extracting(ExceptionResponse::getTitle, ExceptionResponse::getStatus).containsExactly("BAD_REQUEST", 400),
                body -> assertThat(body.getViolations())
                        .extracting(ExceptionResponse.Violation::getField, ExceptionResponse.Violation::getMessage)
                        .containsExactly(tuple("file", "Required part is missing")));
    }

    @Test
    void mapMultipartException_malformedBody_returnsBadRequestWithGenericMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMultipartException(new MultipartException("no boundary"));

        assertThat(summary(response)).containsExactly(400, 400, "BAD_REQUEST", "Malformed multipart request");
    }

    @Test
    void mapMaxUploadSizeExceededException_tooLarge_returnsContentTooLargeWithMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMaxUploadSizeExceededException(new MaxUploadSizeExceededException(5_000_000));

        assertThat(List.of(response.getStatusCode().value(), response.getBody().getTitle(), firstViolation(response)))
                .containsExactly(413, "CONTENT_TOO_LARGE", "File too large");
    }

    @Test
    void mapOptimisticLockException_concurrentChange_returnsConflictWithMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapOptimisticLockException(new OptimisticLockException("version changed"));

        assertThat(List.of(response.getStatusCode().value(), response.getBody().getTitle(), firstViolation(response)))
                .containsExactly(409, "CONFLICT", "The resource was modified elsewhere, reload it");
    }

    @Test
    void mapRuntimeException_unexpectedError_returnsInternalServerErrorHidingDetails() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRuntimeException(new IllegalStateException("password=secret"));

        assertThat(List.of(response.getStatusCode().value(), firstViolation(response))).containsExactly(500, "An unexpected error occurred");
    }

    @Test
    void mapRecentAuthenticationRequired_oldLogin_returnsUnauthorizedWithStepUpChallenge() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRecentAuthenticationRequired(new RecentAuthenticationRequiredException(300));

        assertThat(List.of(response.getStatusCode().value(), firstViolation(response), response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)))
                .containsExactly(401, "Recent authentication required",
                        "Bearer error=\"insufficient_user_authentication\", "
                                + "error_description=\"A more recent authentication is required\", max_age=\"300\"");
    }
}

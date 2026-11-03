package it.gdr.diary.unit.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.exception.RecentAuthenticationRequiredException;
import it.gdr.diary.exception.ServiceException;
import jakarta.persistence.OptimisticLockException;
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

    @Test
    void mapServiceException_businessError_returnsBadRequest() {
        ResponseEntity<ExceptionResponse> response = mapper.mapServiceException(new ServiceException("The diary is full (100 pages)"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapServiceException_businessError_returnsMessageAsViolation() {
        ResponseEntity<ExceptionResponse> response = mapper.mapServiceException(new ServiceException("The diary is full (100 pages)"));

        assertThat(firstViolation(response)).isEqualTo("The diary is full (100 pages)");
    }

    @Test
    void mapServiceException_businessError_returnsTitleAndStatusInBody() {
        ResponseEntity<ExceptionResponse> response = mapper.mapServiceException(new ServiceException("x"));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, ExceptionResponse::getStatus)
                .containsExactly("BAD_REQUEST", 400);
    }

    @Test
    void mapNotFoundException_notFound_returnsStatusOfException() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mapNotFoundException_conflict_returnsStatusOfException() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatus.CONFLICT, "Modified"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapNotFoundException_withReason_returnsReasonAsViolation() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        assertThat(firstViolation(response)).isEqualTo("Diary not found");
    }

    @Test
    void mapAuthException_authenticationFailure_returnsUnauthorized() {
        ResponseEntity<ExceptionResponse> response = mapper.mapAuthException(new BadCredentialsException("bad token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mapAuthException_authenticationFailure_hidesOriginalMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapAuthException(new BadCredentialsException("signature mismatch for kid 42"));

        assertThat(firstViolation(response)).isEqualTo("Authentication required");
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
    void mapHttpMessageNotReadableException_malformedJson_returnsBadRequest() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", mock(StreamReadException.class), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ExceptionResponse> response = mapper.mapHttpMessageNotReadableException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapHttpMessageNotReadableException_malformedJson_returnsGenericMessage() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", mock(StreamReadException.class), new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ExceptionResponse> response = mapper.mapHttpMessageNotReadableException(ex);

        assertThat(firstViolation(response)).isEqualTo("Malformed JSON request");
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
    void mapMethodArgumentTypeMismatchException_invalidUuid_returnsBadRequest() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null, null);

        ResponseEntity<ExceptionResponse> response = mapper.mapMethodArgumentTypeMismatchException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapMethodArgumentTypeMismatchException_invalidUuid_namesParameter() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id", null, null);

        ResponseEntity<ExceptionResponse> response = mapper.mapMethodArgumentTypeMismatchException(ex);

        assertThat(firstViolation(response)).isEqualTo("Invalid value for parameter: id");
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
    void mapMissingServletRequestPartException_missingFile_returnsBadRequestInBody() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMissingServletRequestPartException(new MissingServletRequestPartException("file"));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, ExceptionResponse::getStatus)
                .containsExactly("BAD_REQUEST", 400);
    }

    @Test
    void mapMissingServletRequestPartException_missingFile_namesPartInViolation() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMissingServletRequestPartException(new MissingServletRequestPartException("file"));

        assertThat(response.getBody().getViolations())
                .extracting(ExceptionResponse.Violation::getField, ExceptionResponse.Violation::getMessage)
                .containsExactly(tuple("file", "Required part is missing"));
    }

    @Test
    void mapMultipartException_malformedBody_returnsBadRequestInBody() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMultipartException(new MultipartException("no boundary"));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, ExceptionResponse::getStatus)
                .containsExactly("BAD_REQUEST", 400);
    }

    @Test
    void mapMultipartException_malformedBody_returnsGenericMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMultipartException(new MultipartException("no boundary"));

        assertThat(firstViolation(response)).isEqualTo("Malformed multipart request");
    }

    @Test
    void mapMaxUploadSizeExceededException_tooLarge_returnsContentTooLarge() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMaxUploadSizeExceededException(new MaxUploadSizeExceededException(5_000_000));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void mapOptimisticLockException_concurrentChange_returnsConflict() {
        ResponseEntity<ExceptionResponse> response = mapper.mapOptimisticLockException(new OptimisticLockException("version changed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapRuntimeException_unexpectedError_returnsInternalServerError() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRuntimeException(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void mapRuntimeException_unexpectedError_hidesInternalDetails() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRuntimeException(new IllegalStateException("password=secret"));

        assertThat(firstViolation(response)).isEqualTo("An unexpected error occurred");
    }

    @Test
    void mapRecentAuthenticationRequired_oldLogin_returnsUnauthorized() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRecentAuthenticationRequired(new RecentAuthenticationRequiredException(300));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mapRecentAuthenticationRequired_oldLogin_returnsStepUpChallengeWithMaxAge() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRecentAuthenticationRequired(new RecentAuthenticationRequiredException(300));

        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(
                "Bearer error=\"insufficient_user_authentication\", "
                        + "error_description=\"A more recent authentication is required\", max_age=\"300\"");
    }

    @Test
    void mapRecentAuthenticationRequired_oldLogin_returnsReasonAsViolation() {
        ResponseEntity<ExceptionResponse> response = mapper.mapRecentAuthenticationRequired(new RecentAuthenticationRequiredException(300));

        assertThat(firstViolation(response)).isEqualTo("Recent authentication required");
    }

    @Test
    void mapNotFoundException_notFound_returnsBareStatusNameAsTitle() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        assertThat(response.getBody().getTitle()).isEqualTo("NOT_FOUND");
    }

    @Test
    void mapNotFoundException_conflict_returnsConflictTitleAndReason() {
        ResponseEntity<ExceptionResponse> response = mapper.mapNotFoundException(
                new ResponseStatusException(HttpStatus.CONFLICT, "The diary changed while it was being deleted, try again"));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, body -> body.getViolations().getFirst().getMessage())
                .containsExactly("CONFLICT", "The diary changed while it was being deleted, try again");
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
    void mapMaxUploadSizeExceededException_tooLarge_returnsTitleAndMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapMaxUploadSizeExceededException(new MaxUploadSizeExceededException(5_000_000));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, body -> body.getViolations().getFirst().getMessage())
                .containsExactly("CONTENT_TOO_LARGE", "File too large");
    }

    @Test
    void mapOptimisticLockException_concurrentChange_returnsTitleAndMessage() {
        ResponseEntity<ExceptionResponse> response = mapper.mapOptimisticLockException(new OptimisticLockException("version changed"));

        assertThat(response.getBody()).extracting(ExceptionResponse::getTitle, body -> body.getViolations().getFirst().getMessage())
                .containsExactly("CONFLICT", "The resource was modified elsewhere, reload it");
    }
}

package it.gdr.diary.exception;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ExceptionMapper {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ExceptionResponse> mapServiceException(ServiceException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ExceptionResponse> mapNotFoundException(ResponseStatusException ex) {
        log.warn("Status exception: {}", ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String title = status != null ? status.name() : String.valueOf(ex.getStatusCode().value());
        String fallback = status != null ? status.getReasonPhrase() : title;
        String message = ex.getReason() != null ? ex.getReason() : fallback;
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(ex.getStatusCode().value(), title, message));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ExceptionResponse> mapAuthException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ExceptionResponse> mapForbiddenException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(RecentAuthenticationRequiredException.class)
    public ResponseEntity<ExceptionResponse> mapRecentAuthenticationRequired(RecentAuthenticationRequiredException ex) {
        log.warn("Recent authentication required, max age {}s", ex.getMaxAgeSeconds());
        String challenge = "Bearer error=\"insufficient_user_authentication\", "
                + "error_description=\"A more recent authentication is required\", "
                + "max_age=\"" + ex.getMaxAgeSeconds() + "\"";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, challenge)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ExceptionResponse> mapInvalidFormatException(InvalidFormatException ex) {
        log.warn("Invalid format: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse("BAD_REQUEST", HttpStatus.BAD_REQUEST.value(),
                        List.of(new ExceptionResponse.Violation(fieldOf(ex.getPath()), "Invalid value"))));
    }

    private static String fieldOf(List<JacksonException.Reference> path) {
        String field = path.stream()
                .map(reference -> reference.getPropertyName() != null
                        ? reference.getPropertyName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."));
        return field.isEmpty() ? null : field;
    }

    @ExceptionHandler(StreamReadException.class)
    public ResponseEntity<ExceptionResponse> mapJsonParseException(StreamReadException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "Malformed JSON request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ExceptionResponse> mapHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException invalidFormat) {
            return mapInvalidFormatException(invalidFormat);
        }
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "Malformed JSON request"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> mapMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid parameter: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "Invalid value for parameter: " + ex.getName()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ExceptionResponse> mapBindException(BindException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        List<ExceptionResponse.Violation> violations = new ArrayList<>();
        ex.getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new ExceptionResponse.Violation(error.getField(),
                        error.isBindingFailure() ? "Invalid value" : error.getDefaultMessage()))
                .forEach(violations::add);
        ex.getGlobalErrors().stream()
                .map(error -> new ExceptionResponse.Violation(error.getDefaultMessage()))
                .forEach(violations::add);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse("BAD_REQUEST", HttpStatus.BAD_REQUEST.value(), violations));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ExceptionResponse> mapMissingServletRequestPartException(MissingServletRequestPartException ex) {
        log.warn("Missing request part: {}", ex.getRequestPartName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse("BAD_REQUEST", HttpStatus.BAD_REQUEST.value(),
                        List.of(new ExceptionResponse.Violation(ex.getRequestPartName(), "Required part is missing"))));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ExceptionResponse> mapMultipartException(MultipartException ex) {
        log.warn("Malformed multipart request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "Malformed multipart request"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ExceptionResponse> mapMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.valueOf(413))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.valueOf(413).value(), "CONTENT_TOO_LARGE", "File too large"));
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ExceptionResponse> mapOptimisticLockException(OptimisticLockException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.CONFLICT.value(), "CONFLICT", "The resource was modified elsewhere, reload it"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ExceptionResponse> mapRuntimeException(RuntimeException ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ExceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "An unexpected error occurred"));
    }
}

package it.gdr.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.SimpleResultDTO;
import it.gdr.diary.dto.feedback.BaseFeedbackDTO;
import it.gdr.diary.dto.feedback.DetailFeedbackDTO;
import it.gdr.diary.dto.feedback.InsertFeedbackDTO;
import it.gdr.diary.dto.search.FeedbackSearchRequest;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Feedback", description = "Public feedback form (no login) and management of the received feedback (admin only)")
@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    @Operation(
            summary = "Send a feedback",
            description = "Public endpoint, no token needed. Multipart request with the form fields and up to "
                    + "app.feedback.max-files images (default 3) in the repeated part 'files', for a total of at most "
                    + "app.feedback.max-total-file-size (default 5 MB). Images must be PNG, JPEG or WebP: the real content "
                    + "of each file is checked, not only the declared type, and the declared content type and the extension "
                    + "must match the content. Empty file parts are ignored. The privacy consent is mandatory. "
                    + "The field 'website' is a trap for bots: if it is filled, the request answers 201 but nothing is saved. "
                    + "Each client address can send a limited number of requests per period "
                    + "(app.feedback.rate-limit-max-requests per app.feedback.rate-limit-window).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Feedback received (also when it was discarded by the trap field)"),
            @ApiResponse(responseCode = "400", description = "Invalid fields (unknown type, blank or too long message, missing or invalid email, consent missing or false), technical information that is not a JSON object or too large, too many files, files too large in total, empty or unsupported file, declared content type or extension not matching the content, or malformed multipart request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "413", description = "Request larger than the maximum request size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many submissions from the same address, see the Retry-After header",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(path = "/feedback",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SimpleResultDTO<Void> createFeedback(
            @Valid @ModelAttribute InsertFeedbackDTO request,
            @Parameter(description = "Optional images (PNG, JPEG or WebP), the part can be repeated")
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        service.createFeedback(request, files);
        return SimpleResultDTO.<Void>builder().message("Feedback received").build();
    }

    @Operation(
            summary = "List the feedback",
            description = "Admin only. Returns a page of the received feedback, newest first by default, filtered and sorted "
                    + "according to the search parameters (type, status, text contained in the email, and the period of reception: "
                    + "createdFrom and createdTo are dates without time, both included, from 00:00:00 of the first day to 23:59:59 of the last day "
                    + "in the time zone app.feedback.timezone, each one can be used alone or together).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of feedback (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (for example page below 1, size above 100, unknown type or status, date that is not in the format yyyy-MM-dd, or createdFrom after createdTo)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping(path = "/feedback", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BaseFeedbackDTO> findAllFeedbacks(@Valid @ParameterObject FeedbackSearchRequest request) {
        return service.findAllFeedbacks(request);
    }

    @Operation(
            summary = "Get a feedback",
            description = "Admin only. Returns a feedback with its text, technical information and the attached images, "
                    + "each with a temporary URL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The requested feedback"),
            @ApiResponse(responseCode = "400", description = "Feedback identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping(path = "/feedback/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailFeedbackDTO getFeedbackById(
            @Parameter(description = "Feedback identifier", example = "7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10")
            @PathVariable UUID id) {
        return service.getFeedbackById(id);
    }

    @Operation(
            summary = "Mark a feedback as read",
            description = "Admin only. Sets the status of a feedback to READ. Repeating the call changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback marked as read"),
            @ApiResponse(responseCode = "400", description = "Feedback identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The feedback was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping(path = "/feedback/{id}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailFeedbackDTO markFeedbackAsRead(
            @Parameter(description = "Feedback identifier", example = "7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10")
            @PathVariable UUID id) {
        return service.markFeedbackAsRead(id);
    }

    @Operation(
            summary = "Mark a feedback as unread",
            description = "Admin only. Sets the status of a feedback back to NEW (to be read). Repeating the call changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback marked as unread"),
            @ApiResponse(responseCode = "400", description = "Feedback identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The feedback was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping(path = "/feedback/{id}/unread", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailFeedbackDTO markFeedbackAsUnread(
            @Parameter(description = "Feedback identifier", example = "7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10")
            @PathVariable UUID id) {
        return service.markFeedbackAsUnread(id);
    }

    @Operation(
            summary = "Mark a feedback as answered",
            description = "Admin only. Sets the status of a feedback to ANSWERED. Repeating the call changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback marked as answered"),
            @ApiResponse(responseCode = "400", description = "Feedback identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The feedback was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping(path = "/feedback/{id}/answered", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailFeedbackDTO markFeedbackAsAnswered(
            @Parameter(description = "Feedback identifier", example = "7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10")
            @PathVariable UUID id) {
        return service.markFeedbackAsAnswered(id);
    }

    @Operation(
            summary = "Archive a feedback",
            description = "Admin only. Sets the status of a feedback to ARCHIVED. Repeating the call changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback archived"),
            @ApiResponse(responseCode = "400", description = "Feedback identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The feedback was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping(path = "/feedback/{id}/archive", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailFeedbackDTO archiveFeedback(
            @Parameter(description = "Feedback identifier", example = "7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10")
            @PathVariable UUID id) {
        return service.archiveFeedback(id);
    }

    @Operation(
            summary = "Delete a feedback",
            description = "Admin only. Deletes a feedback together with its attached images, for example when the sender "
                    + "asks to remove their data. The operation cannot be undone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Feedback deleted"),
            @ApiResponse(responseCode = "400", description = "Feedback identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user does not have the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Feedback not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping(path = "/feedback/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteFeedback(
            @Parameter(description = "Feedback identifier", example = "7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10")
            @PathVariable UUID id) {
        service.deleteFeedback(id);
        return SimpleResultDTO.<Void>builder().message("Feedback deleted").build();
    }
}

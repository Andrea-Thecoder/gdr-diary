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
import it.gdr.diary.dto.diary.BaseDiaryDTO;
import it.gdr.diary.dto.diary.BookmarkDiaryDTO;
import it.gdr.diary.dto.diary.DetailDiaryDTO;
import it.gdr.diary.dto.diary.InsertDiaryDTO;
import it.gdr.diary.dto.diary.UpdateBookmarkDiaryDTO;
import it.gdr.diary.dto.diary.UpdateDiaryDTO;
import it.gdr.diary.dto.search.DiarySearchRequest;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Diaries", description = "Manage the diaries of the characters of the logged-in user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService service;

    @Operation(
            summary = "List the diaries of a character",
            description = "Returns a page of the diaries of a character owned by the logged-in user, filtered and sorted "
                    + "according to the search parameters (default order: title). The tag filter keeps the diaries that have "
                    + "at least one page with a tag containing the given text.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of diaries of the character (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (for example page below 1 or size above 100), or character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/characters/{characterId}/diaries", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BaseDiaryDTO> findAllDiariesByCharacter(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId,
            @Valid @ParameterObject DiarySearchRequest request) {
        return service.findAllDiariesByCharacter(characterId, request);
    }

    @Operation(
            summary = "Create a diary",
            description = "Creates a new diary for a character owned by the logged-in user. Only the title is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Diary created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example blank title or malformed color), or character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, owned by another user, or deleted while the diary was being created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(path = "/characters/{characterId}/diaries",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DetailDiaryDTO createDiary(
            @Parameter(description = "Identifier of the character the diary belongs to",
                    example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId,
            @Valid @RequestBody InsertDiaryDTO request) {
        return service.createDiary(characterId, request);
    }

    @Operation(
            summary = "Get a diary",
            description = "Returns a single diary of a character owned by the logged-in user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The requested diary"),
            @ApiResponse(responseCode = "400", description = "Diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/diaries/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailDiaryDTO getDiaryById(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id) {
        return service.getDiaryById(id);
    }

    @Operation(
            summary = "Update a diary",
            description = "Replaces all the editable fields of a diary. Fields not sent are set to empty. "
                    + "The diary cannot be moved to another character.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diary updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example blank title or malformed color), or diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The diary was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/diaries/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailDiaryDTO updateDiary(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiaryDTO request) {
        return service.updateDiary(id, request);
    }

    @Operation(
            summary = "Delete a diary",
            description = "Deletes a diary together with all its pages and its cover image. The operation cannot be undone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Diary deleted"),
            @ApiResponse(responseCode = "400", description = "Diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The diary changed while it was being deleted (for example it was modified at the same time): nothing is deleted, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/diaries/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteDiary(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id) {
        service.deleteDiary(id);
        return SimpleResultDTO.<Void>builder().message("Diary deleted").build();
    }

    @Operation(
            summary = "Set the cover of a diary",
            description = "Uploads an image (png, jpeg or webp) as cover of a diary of a character owned by the logged-in user. "
                    + "The real content of the file is checked, not only the declared type: the file type must be an allowed image, "
                    + "the declared content type must be present and, like the extension, must match the content and the size must not exceed the configured limit. "
                    + "The previous cover, if any, is replaced and its file is deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cover set"),
            @ApiResponse(responseCode = "400", description = "Missing file part, malformed multipart request, empty file, unsupported file type, missing or mismatching content type, extension not matching the content, file too large, or diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The diary was modified at the same time by another request: the cover is not changed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "413", description = "File larger than the maximum request size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/diaries/{id}/cover",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailDiaryDTO updateDiaryCover(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id,
            @Parameter(description = "The image to use as cover (png, jpeg or webp)")
            @RequestPart("file") MultipartFile file) {
        return service.updateDiaryCover(id, file);
    }

    @Operation(
            summary = "Remove the cover of a diary",
            description = "Removes the cover of a diary of a character owned by the logged-in user and deletes its file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cover removed"),
            @ApiResponse(responseCode = "400", description = "Diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The diary was modified at the same time by another request: the cover is not removed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/diaries/{id}/cover", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteDiaryCover(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id) {
        service.deleteDiaryCover(id);
        return SimpleResultDTO.<Void>builder().message("Cover removed").build();
    }

    @Operation(
            summary = "Get the bookmark of a diary",
            description = "Returns the page the reader left the bookmark on, in a diary of a character owned by the logged-in user. "
                    + "Both fields of the response are null when the diary has no bookmark.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The bookmark of the diary (empty if there is none)"),
            @ApiResponse(responseCode = "400", description = "Diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/diaries/{id}/bookmark", produces = MediaType.APPLICATION_JSON_VALUE)
    public BookmarkDiaryDTO getDiaryBookmark(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id) {
        return service.getDiaryBookmark(id);
    }

    @Operation(
            summary = "Set the bookmark of a diary",
            description = "Puts the bookmark of a diary of a character owned by the logged-in user on one of its pages. "
                    + "A diary has at most one bookmark: the previous one, if any, is replaced. "
                    + "When the bookmarked page is deleted the bookmark disappears.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookmark set"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example missing or malformed page identifier), or diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found or its character is owned by another user, or the page does not belong to the diary (or was deleted meanwhile)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The diary was modified at the same time by another request: the bookmark is not changed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/diaries/{id}/bookmark",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public BookmarkDiaryDTO updateDiaryBookmark(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBookmarkDiaryDTO request) {
        return service.updateDiaryBookmark(id, request);
    }

    @Operation(
            summary = "Remove the bookmark of a diary",
            description = "Removes the bookmark of a diary of a character owned by the logged-in user. "
                    + "The operation succeeds also when the diary has no bookmark.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bookmark removed"),
            @ApiResponse(responseCode = "400", description = "Diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The diary was modified at the same time by another request: the bookmark is not removed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/diaries/{id}/bookmark", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteDiaryBookmark(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID id) {
        service.deleteDiaryBookmark(id);
        return SimpleResultDTO.<Void>builder().message("Bookmark removed").build();
    }
}

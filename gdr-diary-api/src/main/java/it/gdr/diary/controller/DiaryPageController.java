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
import it.gdr.diary.dto.diary_page.BaseDiaryPageDTO;
import it.gdr.diary.dto.diary_page.InsertDiaryPageDTO;
import it.gdr.diary.dto.diary_page.UpdateDiaryPageDTO;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.DiaryPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Diary pages", description = "Manage the pages of the diaries of the logged-in user (maximum 100 pages per diary)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class DiaryPageController {

    private final DiaryPageService service;

    @Operation(
            summary = "List the pages of a diary",
            description = "Returns two pages at a time of a diary of a character owned by the logged-in user, with their content, "
                    + "sorted by page number. Use page=1 for pages 1 and 2, page=2 for pages 3 and 4, and so on. "
                    + "The last spread can contain a single page. A page beyond the end returns an empty list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of diary pages (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid page parameter (below 1 or not a number), or diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/diaries/{diaryId}/pages", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BaseDiaryPageDTO> findAllDiaryPagesByDiary(
            @Parameter(description = "Diary identifier", example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID diaryId,
            @Parameter(description = "Number of the spread to read (1-based), two pages at a time", example = "1",
                    schema = @Schema(minimum = "1", defaultValue = "1"))
            @RequestParam(defaultValue = "1") int page) {
        return service.findAllDiaryPagesByDiary(diaryId, page);
    }
    

    @Operation(
            summary = "Create a page",
            description = "Adds a page at the end of a diary of a character owned by the logged-in user. "
                    + "The page number is assigned by the server (the next one after the last page) and a diary can have at most 100 pages. "
                    + "The content must be a document of the diary editor: a doc node with paragraphs (optionally aligned left, center, right or justify), blockquotes, bullet and ordered lists and horizontal rules, where the text and the hard breaks can carry the marks bold, italic, underline, title (level 1 or 2) and textColor (a color of the palette: red, orange, green, teal, blue, purple, magenta or brown). Any other node, mark or property is rejected. "
                    + "The content must not exceed the configured maximum size.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Page created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example empty content), content that is not a valid diary document, diary full (100 pages), page added at the same time by another request, content too large, or diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, its character is owned by another user, or the diary was deleted while the page was being created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(path = "/diaries/{diaryId}/pages",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseDiaryPageDTO createDiaryPage(
            @Parameter(description = "Identifier of the diary the page belongs to",
                    example = "5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4")
            @PathVariable UUID diaryId,
            @Valid @RequestBody InsertDiaryPageDTO request) {
        return service.createDiaryPage(diaryId, request);
    }

    @Operation(
            summary = "Update a page",
            description = "Replaces the content of a page. The page number cannot be changed directly and the page cannot be moved to another diary. "
                    + "The content must be a document of the diary editor: a doc node with paragraphs (optionally aligned left, center, right or justify), blockquotes, bullet and ordered lists and horizontal rules, where the text and the hard breaks can carry the marks bold, italic, underline, title (level 1 or 2) and textColor (a color of the palette: red, orange, green, teal, blue, purple, magenta or brown). Any other node, mark or property is rejected. "
                    + "The content must not exceed the configured maximum size. "
                    + "The request must carry the version of the page the client last read: if the page was modified in the meantime nothing is saved and the response is 409. "
                    + "The response carries the new version, to be used for the next update.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page updated, with the new version of the page"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example empty content or missing version), content that is not a valid diary document, content too large, or page identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Page not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The page was modified in the meantime (another tab or device, or a deletion that renumbered it): nothing is saved and the page must be read again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/pages/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public BaseDiaryPageDTO updateDiaryPage(
            @Parameter(description = "Page identifier", example = "7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiaryPageDTO request) {
        return service.updateDiaryPage(id, request);
    }

    @Operation(
            summary = "Delete a page",
            description = "Deletes a page of a diary of a character owned by the logged-in user. "
                    + "The pages that follow are renumbered, so the numbers stay consecutive. The identifiers of the pages do not change.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page deleted"),
            @ApiResponse(responseCode = "400", description = "Page identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Page not found, its character is owned by another user, or the page was deleted at the same time",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "A page of the diary was modified at the same time by another request: nothing is deleted, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/pages/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteDiaryPage(
            @Parameter(description = "Page identifier", example = "7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45")
            @PathVariable UUID id) {
        service.deleteDiaryPage(id);
        return SimpleResultDTO.<Void>builder().message("Page deleted").build();
    }
}

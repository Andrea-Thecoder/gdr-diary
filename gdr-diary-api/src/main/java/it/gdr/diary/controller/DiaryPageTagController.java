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
import it.gdr.diary.dto.diary_page_tag.BaseDiaryTagDTO;
import it.gdr.diary.dto.diary_page_tag.DetailDiaryPageTagDTO;
import it.gdr.diary.dto.diary_page_tag.UpdateDiaryPageTagDTO;
import it.gdr.diary.dto.search.DiaryTagSearchRequest;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.DiaryPageTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Diary page tags", description = "Manage the tags of the pages of the diaries of the logged-in user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class DiaryPageTagController {

    private final DiaryPageTagService service;

    @Operation(
            summary = "List the tags of a diary",
            description = "Returns a page of the tags used in the pages of a diary of a character owned by the logged-in user, "
                    + "each with the numbers of the pages that have it (a diary has at most 10 different tags). Tags that differ only by case are grouped. "
                    + "The tags are always sorted by the number of the first page that has them (the first tag of the diary comes first).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of tags of the diary (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (for example page below 1, size above 100 or name longer than 30 characters), or diary identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Diary not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/diaries/{diaryId}/tags", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BaseDiaryTagDTO> findAllDiaryTagsByDiary(
            @Parameter(description = "Diary identifier", example = "5c1e8a42-93d7-4f0b-8a16-0d6b7e2f9a31")
            @PathVariable UUID diaryId,
            @Valid @ParameterObject DiaryTagSearchRequest request) {
        return service.findAllDiaryTagsByDiary(diaryId, request);
    }

    @Operation(
            summary = "Set the tag of a page",
            description = "Replaces the tag of a page of a diary of a character owned by the logged-in user. "
                    + "A page has at most one tag, 1 to 30 characters without control characters; a null tag removes it. "
                    + "A diary can have at most 10 different tags (ignoring case): if the diary already has the tag with another case, "
                    + "that spelling is kept. The tag is not part of the page content: saving it does not change the version of the page. "
                    + "The response gives the tag the page has now (null if none).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tag replaced, with the tag the page has now"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (blank or too long tag, control characters, characters that cannot be stored), "
                    + "a new tag when the diary already has 10 different tags, or page identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Page not found, its character is owned by another user, or the page was deleted at the same time",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/pages/{id}/tag",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailDiaryPageTagDTO updateDiaryPageTag(
            @Parameter(description = "Page identifier", example = "7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiaryPageTagDTO request) {
        return service.updateDiaryPageTag(id, request);
    }
}

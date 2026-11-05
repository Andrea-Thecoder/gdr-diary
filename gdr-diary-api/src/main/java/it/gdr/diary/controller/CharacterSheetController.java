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
import it.gdr.diary.dto.character_sheet.BaseCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.DetailCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.InsertCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.UpdateCharacterSheetDTO;
import it.gdr.diary.dto.search.CharacterSheetSearchRequest;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.CharacterSheetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Character sheets", description = "Manage the PDF sheets of the characters of the logged-in user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class CharacterSheetController {

    private final CharacterSheetService service;

    @Operation(
            summary = "List the sheets of a character",
            description = "Returns a page of the PDF sheets of a character owned by the logged-in user, filtered and sorted "
                    + "according to the search parameters (default order: upload date). Each sheet has two temporary URLs: "
                    + "one to open the PDF in the browser and one that downloads it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of sheets of the character (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (for example page below 1 or size above 100), or character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/characters/{characterId}/sheets", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BaseCharacterSheetDTO> findAllCharacterSheetsByCharacter(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId,
            @Valid @ParameterObject CharacterSheetSearchRequest request) {
        return service.findAllCharacterSheetsByCharacter(characterId, request);
    }

    @Operation(
            summary = "Upload a sheet for a character",
            description = "Uploads a PDF as a new sheet of a character owned by the logged-in user, in a single step: "
                    + "the multipart request has the file part and the title (required) and game system (optional) fields. "
                    + "The real content of the file is checked, not only the declared type: the file must start with the PDF signature, "
                    + "the declared content type must be present and, like the extension, must match the content and the size must not exceed the configured limit. "
                    + "A character can have a limited number of sheets (app.character-sheet.max-sheets-per-character, default 10).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sheet created"),
            @ApiResponse(responseCode = "400", description = "Missing file part, malformed multipart request, blank or too long title, empty file, file that is not a PDF, missing or mismatching content type, extension not matching the content, file too large, too many sheets for the character, or character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, owned by another user, or deleted while the sheet was being uploaded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "413", description = "File larger than the maximum request size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(path = "/characters/{characterId}/sheets",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DetailCharacterSheetDTO createCharacterSheet(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId,
            @Valid @ModelAttribute InsertCharacterSheetDTO request,
            @Parameter(description = "The PDF file of the sheet")
            @RequestPart("file") MultipartFile file) {
        return service.createCharacterSheet(characterId, request, file);
    }

    @Operation(
            summary = "Get a sheet",
            description = "Returns a single sheet of a character owned by the logged-in user, with fresh temporary URLs "
                    + "(the ones returned by the list expire after a few minutes).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The requested sheet"),
            @ApiResponse(responseCode = "400", description = "Sheet identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sheet not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/sheets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailCharacterSheetDTO getCharacterSheetById(
            @Parameter(description = "Sheet identifier", example = "4d0f6c1a-2b7e-4c1d-8e55-9a3b7c1d2e10")
            @PathVariable UUID id) {
        return service.getCharacterSheetById(id);
    }

    @Operation(
            summary = "Update a sheet",
            description = "Replaces title and game system of a sheet of a character owned by the logged-in user. "
                    + "Fields not sent are set to empty (the title cannot be empty). The PDF file is not changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sheet updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example blank title), or sheet identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sheet not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The sheet was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/sheets/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailCharacterSheetDTO updateCharacterSheet(
            @Parameter(description = "Sheet identifier", example = "4d0f6c1a-2b7e-4c1d-8e55-9a3b7c1d2e10")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCharacterSheetDTO request) {
        return service.updateCharacterSheet(id, request);
    }

    @Operation(
            summary = "Replace the PDF of a sheet",
            description = "Uploads a PDF that replaces the file of a sheet of a character owned by the logged-in user. "
                    + "Title and game system are kept. The same checks as the upload of a new sheet apply. "
                    + "The previous file is replaced and deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF replaced"),
            @ApiResponse(responseCode = "400", description = "Missing file part, malformed multipart request, empty file, file that is not a PDF, missing or mismatching content type, extension not matching the content, file too large, or sheet identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sheet not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The sheet was modified at the same time by another request: the PDF is not changed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "413", description = "File larger than the maximum request size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/sheets/{id}/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailCharacterSheetDTO updateCharacterSheetFile(
            @Parameter(description = "Sheet identifier", example = "4d0f6c1a-2b7e-4c1d-8e55-9a3b7c1d2e10")
            @PathVariable UUID id,
            @Parameter(description = "The PDF file that replaces the current one")
            @RequestPart("file") MultipartFile file) {
        return service.updateCharacterSheetFile(id, file);
    }

    @Operation(
            summary = "Delete a sheet",
            description = "Deletes a sheet of a character owned by the logged-in user, together with its PDF file. "
                    + "The operation cannot be undone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sheet deleted"),
            @ApiResponse(responseCode = "400", description = "Sheet identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sheet not found, or its character is owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/sheets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteCharacterSheet(
            @Parameter(description = "Sheet identifier", example = "4d0f6c1a-2b7e-4c1d-8e55-9a3b7c1d2e10")
            @PathVariable UUID id) {
        service.deleteCharacterSheet(id);
        return SimpleResultDTO.<Void>builder().message("Sheet deleted").build();
    }
}

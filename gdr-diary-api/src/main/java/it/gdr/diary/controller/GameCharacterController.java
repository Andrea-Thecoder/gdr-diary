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
import it.gdr.diary.dto.game_character.BaseGameCharacterDTO;
import it.gdr.diary.dto.game_character.DetailGameCharacterDTO;
import it.gdr.diary.dto.game_character.InsertGameCharacterDTO;
import it.gdr.diary.dto.game_character.UpdateGameCharacterDTO;
import it.gdr.diary.dto.search.GameCharacterSearchRequest;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.GameCharacterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Characters", description = "Manage the role-playing characters of the logged-in user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/characters")
@RequiredArgsConstructor
public class GameCharacterController {

    private final GameCharacterService service;

    @Operation(
            summary = "List characters",
            description = "Returns a page of the characters owned by the logged-in user, filtered and sorted "
                    + "according to the search parameters (default order: name). "
                    + "Returns an empty page if the user has not been created yet with POST /me.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of characters of the logged-in user (empty list if none)"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters (for example page below 1 or size above 100)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedResultDTO<BaseGameCharacterDTO> findAllGameCharacters(
            @Valid @ParameterObject GameCharacterSearchRequest request) {
        return service.findAllGameCharacters(request);
    }

    @Operation(
            summary = "Get a character",
            description = "Returns a single character owned by the logged-in user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The requested character"),
            @ApiResponse(responseCode = "400", description = "Character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailGameCharacterDTO getGameCharacterById(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID id) {
        return service.getGameCharacterById(id);
    }

    @Operation(
            summary = "Create a character",
            description = "Creates a new character owned by the logged-in user. Only the name is required.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Character created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example blank name or malformed color)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user has not been created yet (call POST /me first), or was deleted while the character was being created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DetailGameCharacterDTO createGameCharacter(
            @Valid @RequestBody InsertGameCharacterDTO request) {
        return service.createGameCharacter(request);
    }

    @Operation(
            summary = "Update a character",
            description = "Replaces all the editable fields of a character owned by the logged-in user. "
                    + "Fields not sent are set to empty.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Character updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example blank name or malformed color), or character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The character was modified at the same time by another request: nothing is saved, read it again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailGameCharacterDTO updateGameCharacter(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGameCharacterDTO request) {
        return service.updateGameCharacter(id, request);
    }

    @Operation(
            summary = "Delete a character",
            description = "Deletes a character owned by the logged-in user, together with all its diaries, their pages "
                    + "and the images of the character and of its diaries. The operation cannot be undone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Character deleted"),
            @ApiResponse(responseCode = "400", description = "Character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The character changed while it was being deleted (for example a diary or a page was added, or it was modified at the same time): nothing is deleted, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteGameCharacter(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID id) {
        service.deleteGameCharacter(id);
        return SimpleResultDTO.<Void>builder().message("Character deleted").build();
    }

    @Operation(
            summary = "Set the avatar of a character",
            description = "Uploads an image (png, jpeg or webp) as avatar of a character owned by the logged-in user. "
                    + "The real content of the file is checked, not only the declared type: the file type must be an allowed image, "
                    + "the declared content type must be present and, like the extension, must match the content and the size must not exceed the configured limit. "
                    + "The previous avatar, if any, is replaced and its file is deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar set"),
            @ApiResponse(responseCode = "400", description = "Missing file part, malformed multipart request, empty file, unsupported file type, missing or mismatching content type, extension not matching the content, file too large, or character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The character was modified at the same time by another request: the avatar is not changed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "413", description = "File larger than the maximum request size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailGameCharacterDTO updateGameCharacterAvatar(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID id,
            @Parameter(description = "The image to use as avatar (png, jpeg or webp)")
            @RequestPart("file") MultipartFile file) {
        return service.updateGameCharacterAvatar(id, file);
    }

    @Operation(
            summary = "Remove the avatar of a character",
            description = "Removes the avatar of a character owned by the logged-in user and deletes its file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar removed"),
            @ApiResponse(responseCode = "400", description = "Character identifier that is not a UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The character was modified at the same time by another request: the avatar is not removed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/{id}/avatar", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteGameCharacterAvatar(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID id) {
        service.deleteGameCharacterAvatar(id);
        return SimpleResultDTO.<Void>builder().message("Avatar removed").build();
    }
}

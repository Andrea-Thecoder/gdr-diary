package it.gdr.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.gdr.diary.dto.character_background.DetailCharacterBackgroundDTO;
import it.gdr.diary.dto.character_background.UpdateCharacterBackgroundDTO;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.BackgroundPdf;
import it.gdr.diary.services.CharacterBackgroundPdfService;
import it.gdr.diary.services.CharacterBackgroundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Tag(name = "Character backgrounds", description = "Read and write the background story of the characters of the logged-in user (one story per character)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class CharacterBackgroundController {

    private final CharacterBackgroundService service;
    private final CharacterBackgroundPdfService pdfService;

    @Operation(
            summary = "Read the background of a character",
            description = "Returns the background story of a character owned by the logged-in user, as a rich text document. "
                    + "A character that has no background yet answers 200 with an empty document and version 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The background, or an empty document with version 0 if the character has none"),
            @ApiResponse(responseCode = "400", description = "Character identifier that is not a UUID, or a game master (game masters have no background)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, or owned by another user",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/characters/{characterId}/background", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailCharacterBackgroundDTO getCharacterBackground(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId) {
        return service.getCharacterBackground(characterId);
    }

    @Operation(
            summary = "Write the background of a character",
            description = "Replaces the background story of a character owned by the logged-in user; the first write creates it. "
                    + "The content must be a document of the diary editor: a doc node with paragraphs (optionally aligned left, center, right or justify), blockquotes, bullet and ordered lists and horizontal rules, where the text and the hard breaks can carry the marks bold, italic, underline, title (level 1 or 2) and textColor (a color of the palette: red, orange, green, teal, blue, purple, magenta or brown). Any other node, mark or property is rejected. "
                    + "The content must not exceed the configured maximum size, and its text must not exceed the configured maximum number of characters. "
                    + "The request must carry the version the client last read (0 if the character has no background yet): if the background was modified in the meantime nothing is saved and the response is 409. "
                    + "The response carries the new version, to be used for the next write.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Background saved, with its new version"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (for example empty content or missing version), content that is not a valid diary document, content too large, text too long, character identifier that is not a UUID, or a game master (game masters have no background)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, owned by another user, or deleted while the background was being saved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The background was modified in the meantime (another tab or device): nothing is saved and it must be read again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/characters/{characterId}/background",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailCharacterBackgroundDTO updateCharacterBackground(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId,
            @Valid @RequestBody UpdateCharacterBackgroundDTO request) {
        return service.updateCharacterBackground(characterId, request);
    }

    @Operation(
            summary = "Download the background of a character as a PDF",
            description = "Returns the background story of a character owned by the logged-in user as a PDF file (A4, with the formatting of the editor: titles, bold, italic, underline, colors, alignment, lists, quotes and rules), "
                    + "with the name of the character and the campaign at the top and the page numbers at the bottom. "
                    + "The response has Content-Disposition attachment with a file name built from the name of the character (background-<name>.pdf). "
                    + "Characters that the font cannot draw (for example emoji) are replaced by a question mark.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The PDF of the background",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "Character identifier that is not a UUID, or a game master (game masters have no background)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "The user has neither the user nor the admin role", content = @Content),
            @ApiResponse(responseCode = "404", description = "Character not found, owned by another user, or without a background yet",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/characters/{characterId}/background/pdf")
    public ResponseEntity<byte[]> getCharacterBackgroundPdf(
            @Parameter(description = "Character identifier", example = "0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10")
            @PathVariable UUID characterId) {
        BackgroundPdf pdf = pdfService.getCharacterBackgroundPdf(characterId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.content());
    }
}

package it.gdr.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.UserExport;
import it.gdr.diary.services.UserExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@Tag(name = "User export", description = "Copy of all the data of the logged-in user (GDPR access and portability)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class UserExportController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final UserExportService service;

    @Operation(
            summary = "Download a copy of all the data of the logged-in user",
            description = "Returns a ZIP archive with dati.json (account: email, premium, settings; characters with their background story, "
                    + "character sheets and diaries with every page; payments of the account), a LEGGIMI.txt that explains the content "
                    + "and the files of the user: profile image, images of characters and diaries, PDFs of the character sheets. "
                    + "The text of the story and of the pages is the JSON document of the editor. Bookmarks and page tags are not included, "
                    + "neither are the feedbacks (they are not linked to the account). "
                    + "Everything is read in a single read-only transaction, so the copy is consistent. "
                    + "It requires a recent login with the password (the auth_time claim of the token, app.user.export-max-auth-age, five minutes by default): "
                    + "otherwise it answers 401 with WWW-Authenticate error insufficient_user_authentication. "
                    + "The response has Content-Disposition attachment with the name diario-gdr-<date>.zip.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The ZIP archive with the data and the files of the user",
                    content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token, or the last login with the password is older than app.user.export-max-auth-age (the answer carries WWW-Authenticate with error insufficient_user_authentication and the client must ask for the password again)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "404", description = "The user does not exist yet (POST /me was never called)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(path = "/me/export")
    public ResponseEntity<StreamingResponseBody> getUserExport() {
        UserExport export = service.getUserExport();
        return ResponseEntity.ok()
                .contentType(ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(out -> service.writeUserExport(export, out));
    }
}

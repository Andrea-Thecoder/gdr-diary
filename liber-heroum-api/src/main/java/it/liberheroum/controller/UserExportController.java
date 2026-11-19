package it.liberheroum.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.liberheroum.exception.ExceptionResponse;
import it.liberheroum.services.UserExport;
import it.liberheroum.services.UserExportService;
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
            description = "Returns a ZIP archive meant to be read by a person: LEGGIMI.txt (account email and what the archive contains), "
                    + "the profile image, and for every character a folder with its image, its background story as storia.pdf, the "
                    + "PDFs of its character sheets and one PDF per diary (every page of the diary on a new sheet, with its cover "
                    + "image next to it); pagamenti.csv with the payments of the account (semicolon separated, for spreadsheets); "
                    + "per-altri-programmi/dati.json with the same texts and data in a structured format for other programs (data "
                    + "portability). Settings, bookmarks and page tags are not included, neither are the feedbacks (they are not "
                    + "linked to the account). Everything is read in a single read-only transaction, so the copy is consistent. It "
                    + "requires a recent login with the password (the auth_time claim of the token, app.user.export-max-auth-age, "
                    + "five minutes by default): otherwise it answers 401 with WWW-Authenticate error "
                    + "insufficient_user_authentication. The response has Content-Disposition attachment with the name "
                    + "liber-heroum-<date>.zip.")
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

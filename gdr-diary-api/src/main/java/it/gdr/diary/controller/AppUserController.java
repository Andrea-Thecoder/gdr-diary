package it.gdr.diary.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.gdr.diary.dto.SimpleResultDTO;
import it.gdr.diary.dto.app_user.DetailAppUserDTO;
import it.gdr.diary.dto.app_user.UpdateSettingsAppUserDTO;
import it.gdr.diary.exception.ExceptionResponse;
import it.gdr.diary.services.AppUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Users", description = "The logged-in user")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class AppUserController {

    private final AppUserService service;

    @Operation(
            summary = "Sign in the logged-in user",
            description = "To be called once after every login. Creates the user in the backend the first time, "
                    + "otherwise keeps the email aligned with the access token, and returns the identifier of the user. "
                    + "It is idempotent: calling it again returns the same identifier. "
                    + "Call it again after the email has been changed on the authentication server and the access token has been refreshed: "
                    + "the stored email is aligned with the one in the token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The identifier of the logged-in user"),
            @ApiResponse(responseCode = "400", description = "The token has no email address",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "409", description = "The user was modified at the same time by another request, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<UUID> createAppUser() {
        return SimpleResultDTO.<UUID>builder().payload(service.createAppUser()).message("User ready").build();
    }

    @Operation(
            summary = "Get the logged-in user",
            description = "Returns the email, the premium flag and the settings of the logged-in user, identified by the access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The logged-in user"),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user has not been created yet (call POST /me first)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public DetailAppUserDTO getAppUserBySubject() {
        return service.getAppUserBySubject();
    }

    @Operation(
            summary = "Update the settings of the logged-in user",
            description = "Merges the settings sent into the stored ones: a key replaces the stored value, a key with a null value "
                    + "is removed, keys that are not sent are left untouched. Nothing but the settings is changed. "
                    + "Returns the settings after the update (an empty object if none are stored).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings updated, with the settings of the logged-in user after the update as payload"),
            @ApiResponse(responseCode = "400", description = "Body missing or malformed, settings missing or empty, settings with characters that cannot be stored, or settings too large",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user has not been created yet (call POST /me first)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The settings were modified at the same time by another request: nothing is saved, read them again and retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PatchMapping(path = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Map<String, Object>> updateAppUserSettings(@RequestBody @Valid UpdateSettingsAppUserDTO request) {
        return SimpleResultDTO.<Map<String, Object>>builder()
                .payload(service.updateAppUserSettings(request))
                .message("Settings updated")
                .build();
    }

    @Operation(
            summary = "Set the profile image of the logged-in user",
            description = "Uploads an image (png, jpeg or webp) as profile image of the logged-in user. "
                    + "The real content of the file is checked, not only the declared type: the file type must be an allowed image, "
                    + "the declared content type must be present and, like the extension, must match the content and the size must not exceed the configured limit. "
                    + "The previous profile image, if any, is replaced and its file is deleted. "
                    + "Returns a temporary URL of the new image, valid for a few minutes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile image set, with its temporary URL as payload"),
            @ApiResponse(responseCode = "400", description = "Missing file part, malformed multipart request, empty file, unsupported file type, missing or mismatching content type, extension not matching the content, or file too large",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user has not been created yet (call POST /me first)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The user was modified at the same time by another request: the profile image is not changed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "413", description = "File larger than the maximum request size",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "500", description = "Storage not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @PutMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<String> updateAppUserAvatar(
            @Parameter(description = "The image to use as profile image (png, jpeg or webp)")
            @RequestPart("file") MultipartFile file) {
        return SimpleResultDTO.<String>builder().payload(service.updateAppUserAvatar(file)).message("Avatar set").build();
    }

    @Operation(
            summary = "Remove the profile image of the logged-in user",
            description = "Removes the profile image of the logged-in user and deletes its file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile image removed"),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user has not been created yet (call POST /me first)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The user was modified at the same time by another request: the profile image is not removed, try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(path = "/avatar", produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteAppUserAvatar() {
        service.deleteAppUserAvatar();
        return SimpleResultDTO.<Void>builder().message("Avatar removed").build();
    }

    @Operation(
            summary = "Delete the account and all the data of the logged-in user",
            description = "Permanently deletes the account of the logged-in user: first the user on the authentication server (Keycloak, "
                    + "retried three times with growing waits if it fails; an already deleted user counts as done), then everything the user has in the backend: all the characters with their diaries and pages, "
                    + "the profile image, the images of characters and diaries (files included) and the user itself. It cannot be undone. "
                    + "The payments of the user are not deleted: they are kept, detached from the account, because the law requires to retain them. "
                    + "Everything is deleted in a single transaction: if something fails nothing is deleted. "
                    + "If the authentication server cannot delete the user after the retries it answers 502 and deletes nothing in the backend. "
                    + "It requires a recent login with the password (the auth_time claim of the token, five minutes by default): otherwise it answers 401 and deletes nothing. "
                    + "It is idempotent: if the user has no data any more it still answers 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account and data deleted"),
            @ApiResponse(responseCode = "401", description = "Missing, expired or invalid token, or the last login with the password is older than app.user.delete-max-auth-age (the answer carries WWW-Authenticate with error insufficient_user_authentication and the client must ask for the password again)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "409", description = "The account changed while it was being deleted (for example a character was created at the same time): nothing is deleted in the backend (the user on the authentication server is already gone, so calling again goes straight to the data), try again",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class))),
            @ApiResponse(responseCode = "502", description = "The user could not be deleted on the authentication server after the retries: nothing is deleted in the backend, try again later",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ExceptionResponse.class)))
    })
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public SimpleResultDTO<Void> deleteAppUser() {
        service.deleteAppUser();
        return SimpleResultDTO.<Void>builder().message("Account data deleted").build();
    }
}

package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.controller.AppUserController;
import it.gdr.diary.dto.app_user.DetailAppUserDTO;
import it.gdr.diary.dto.app_user.UpdateSettingsAppUserDTO;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.RecentAuthenticationRequiredException;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.AppUserService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AppUserControllerTest {

    private static final UUID USER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");

    @Mock
    private AppUserService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new AppUserController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    @Test
    void createAppUser_authenticatedUser_returnsUserIdAsPayload() {
        when(service.createAppUser()).thenReturn(USER_ID);

        assertThat(mvc.post().uri("/me")).bodyJson().extractingPath("$.payload").isEqualTo(USER_ID.toString());
    }

    @Test
    void createAppUser_authenticatedUser_returnsReadyMessage() {
        when(service.createAppUser()).thenReturn(USER_ID);

        assertThat(mvc.post().uri("/me")).bodyJson().extractingPath("$.message").isEqualTo("User ready");
    }

    @Test
    void getAppUserBySubject_existingUser_returnsDetail() {
        when(service.getAppUserBySubject()).thenReturn(new DetailAppUserDTO("frodo@shire.example", true, Map.of(), null));

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.email").isEqualTo("frodo@shire.example");
    }

    @Test
    void getAppUserBySubject_noSettings_returnsEmptyObject() {
        when(service.getAppUserBySubject()).thenReturn(new DetailAppUserDTO("frodo@shire.example", false, Map.of(), null));

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.settings").asMap().isEmpty();
    }

    @Test
    void getAppUserBySubject_userMissing_returnsNotFound() {
        when(service.getAppUserBySubject()).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        assertThat(mvc.get().uri("/me")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateAppUserSettings_validBody_returnsMergedSettingsAsPayload() {
        when(service.updateAppUserSettings(any())).thenReturn(Map.of("font", "Lora"));

        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"font\":\"Lora\"}}"))
                .bodyJson().extractingPath("$.payload.font").isEqualTo("Lora");
    }

    @Test
    void updateAppUserSettings_validBody_returnsUpdatedMessage() {
        when(service.updateAppUserSettings(any())).thenReturn(Map.of("font", "Lora"));

        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"font\":\"Lora\"}}"))
                .bodyJson().extractingPath("$.message").isEqualTo("Settings updated");
    }

    @Test
    void updateAppUserSettings_validBody_passesSettingsToService() {
        when(service.updateAppUserSettings(any())).thenReturn(Map.of("fontSize", 16));

        mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"fontSize\":16}}").exchange();

        verify(service).updateAppUserSettings(new UpdateSettingsAppUserDTO(Map.of("fontSize", 16)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"settings\":{}}", "{\"settings\":null}"})
    void updateAppUserSettings_emptySettings_returnsBadRequest(String body) {
        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateAppUserSettings_emptySettings_doesNotCallService() {
        mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{}}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void updateAppUserSettings_tooLarge_returnsBadRequest() {
        when(service.updateAppUserSettings(any())).thenThrow(new ServiceException("Settings too large"));

        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"font\":\"Lora\"}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateAppUserAvatar_imagePart_returnsUrlAsPayload() {
        when(service.updateAppUserAvatar(any())).thenReturn("https://storage.example/avatar");

        assertThat(mvc.put().uri("/me/avatar").multipart().file(image()))
                .bodyJson().extractingPath("$.payload").isEqualTo("https://storage.example/avatar");
    }

    @Test
    void updateAppUserAvatar_imagePart_returnsAvatarSetMessage() {
        when(service.updateAppUserAvatar(any())).thenReturn("https://storage.example/avatar");

        assertThat(mvc.put().uri("/me/avatar").multipart().file(image()))
                .bodyJson().extractingPath("$.message").isEqualTo("Avatar set");
    }

    @Test
    void updateAppUserAvatar_invalidFile_returnsBadRequest() {
        when(service.updateAppUserAvatar(any())).thenThrow(new ServiceException("Unsupported file type"));

        assertThat(mvc.put().uri("/me/avatar").multipart().file(image())).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteAppUserAvatar_existingUser_returnsRemovedMessage() {
        assertThat(mvc.delete().uri("/me/avatar")).bodyJson().extractingPath("$.message").isEqualTo("Avatar removed");
    }

    @Test
    void deleteAppUserAvatar_existingUser_omitsPayload() {
        assertThat(mvc.delete().uri("/me/avatar")).bodyJson().doesNotHavePath("$.payload");
    }

    @Test
    void deleteAppUserAvatar_userMissing_returnsNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")).when(service).deleteAppUserAvatar();

        assertThat(mvc.delete().uri("/me/avatar")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteAppUser_authenticatedUser_returnsDeletedMessage() {
        assertThat(mvc.delete().uri("/me")).bodyJson().extractingPath("$.message").isEqualTo("Account data deleted");
    }

    @Test
    void deleteAppUser_authenticatedUser_deletesThroughService() {
        mvc.delete().uri("/me").exchange();

        verify(service).deleteAppUser();
    }

    @Test
    void deleteAppUser_loginTooOld_returnsUnauthorized() {
        doThrow(new RecentAuthenticationRequiredException(300)).when(service).deleteAppUser();

        assertThat(mvc.delete().uri("/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteAppUser_loginTooOld_returnsStepUpChallenge() {
        doThrow(new RecentAuthenticationRequiredException(300)).when(service).deleteAppUser();

        assertThat(mvc.delete().uri("/me")).headers().hasValue(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"insufficient_user_authentication\", "
                        + "error_description=\"A more recent authentication is required\", max_age=\"300\"");
    }

    @Test
    void deleteAppUser_keycloakUnavailable_returnsBadGateway() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The account could not be deleted on the authentication server, try again later"))
                .when(service).deleteAppUser();

        assertThat(mvc.delete().uri("/me")).hasStatus(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void deleteAppUser_keycloakUnavailable_returnsMessageInBody() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The account could not be deleted on the authentication server, try again later"))
                .when(service).deleteAppUser();

        assertThat(mvc.delete().uri("/me")).bodyJson().extractingPath("$.violations[0].message")
                .isEqualTo("The account could not be deleted on the authentication server, try again later");
    }

    @Test
    void updateAppUserSettings_emptySettings_returnsViolationOnSettingsField() {
        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{}}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("settings");
    }
}

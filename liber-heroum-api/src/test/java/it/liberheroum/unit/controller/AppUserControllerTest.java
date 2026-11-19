package it.liberheroum.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.liberheroum.controller.AppUserController;
import it.liberheroum.dto.app_user.DetailAppUserDTO;
import it.liberheroum.dto.app_user.UpdateSettingsAppUserDTO;
import it.liberheroum.exception.ExceptionMapper;
import it.liberheroum.services.AppUserService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
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
    void createAppUser_authenticatedUser_returnsUserIdAndReadyMessage() {
        when(service.createAppUser()).thenReturn(USER_ID);

        MvcTestResult result = mvc.post().uri("/me").exchange();

        assertThat(result).bodyJson().extractingPath("$").asMap()
                .containsEntry("payload", USER_ID.toString()).containsEntry("message", "User ready");
    }

    @Test
    void getAppUserBySubject_existingUserWithoutSettings_returnsDetailWithEmptySettingsObject() {
        when(service.getAppUserBySubject()).thenReturn(new DetailAppUserDTO("frodo@shire.example", true, Map.of(), null));

        MvcTestResult result = mvc.get().uri("/me").exchange();

        assertThat(result).bodyJson().extractingPath("$").asMap()
                .containsEntry("email", "frodo@shire.example").containsEntry("premium", true).containsEntry("settings", Map.of());
    }

    @Test
    void updateAppUserSettings_validBody_passesSettingsToServiceAndReturnsPayloadAndMessage() {
        when(service.updateAppUserSettings(any())).thenReturn(Map.of("fontSize", 16));

        MvcTestResult result = mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\":{\"fontSize\":16}}").exchange();

        verify(service).updateAppUserSettings(new UpdateSettingsAppUserDTO(Map.of("fontSize", 16)));
        assertThat(result).bodyJson().extractingPath("$").asMap()
                .containsEntry("payload", Map.of("fontSize", 16)).containsEntry("message", "Settings updated");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"settings\":{}}", "{\"settings\":null}"})
    void updateAppUserSettings_emptySettings_returnsBadRequestOnSettingsFieldWithoutCallingService(String body) {
        MvcTestResult result = mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo("settings");
        verifyNoInteractions(service);
    }

    @Test
    void updateAppUserAvatar_imagePart_returnsUrlAndAvatarSetMessage() {
        when(service.updateAppUserAvatar(any())).thenReturn("https://storage.example/avatar");

        MvcTestResult result = mvc.put().uri("/me/avatar").multipart().file(image()).exchange();

        assertThat(result).bodyJson().extractingPath("$").asMap()
                .containsEntry("payload", "https://storage.example/avatar").containsEntry("message", "Avatar set");
    }

    @Test
    void deleteAppUserAvatar_existingUser_returnsRemovedMessageWithoutPayload() {
        MvcTestResult result = mvc.delete().uri("/me/avatar").exchange();

        assertThat(result).bodyJson().extractingPath("$").asMap()
                .containsEntry("message", "Avatar removed").doesNotContainKey("payload");
    }

    @Test
    void deleteAppUser_authenticatedUser_deletesThroughServiceAndReturnsMessage() {
        MvcTestResult result = mvc.delete().uri("/me").exchange();

        verify(service).deleteAppUser();
        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Account data deleted");
    }

    @Test
    void deleteAppUser_keycloakUnavailable_returnsBadGatewayWithMessage() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The account could not be deleted on the authentication server, try again later"))
                .when(service).deleteAppUser();

        MvcTestResult result = mvc.delete().uri("/me").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY).bodyJson().extractingPath("$.violations[0].message")
                .isEqualTo("The account could not be deleted on the authentication server, try again later");
    }
}

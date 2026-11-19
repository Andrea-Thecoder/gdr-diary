package it.liberheroum.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.liberheroum.controller.CharacterBackgroundController;
import it.liberheroum.dto.character_background.DetailCharacterBackgroundDTO;
import it.liberheroum.dto.character_background.UpdateCharacterBackgroundDTO;
import it.liberheroum.exception.ExceptionMapper;
import it.liberheroum.services.BackgroundPdf;
import it.liberheroum.services.CharacterBackgroundPdfService;
import it.liberheroum.services.CharacterBackgroundService;
import java.util.List;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CharacterBackgroundControllerTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));
    private static final String CONTENT_JSON = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";

    @Mock
    private CharacterBackgroundService service;
    @Mock
    private CharacterBackgroundPdfService pdfService;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new CharacterBackgroundController(service, pdfService))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private DetailCharacterBackgroundDTO background(long version) {
        return new DetailCharacterBackgroundDTO(CONTENT, version, 12);
    }

    @Test
    void getCharacterBackground_existing_returnsContentVersionAndTextLength() {
        when(service.getCharacterBackground(CHARACTER_ID)).thenReturn(background(3L));

        MvcTestResult result = mvc.get().uri("/characters/{characterId}/background", CHARACTER_ID).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.textLength").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.content.type").isEqualTo("doc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/characters/abc/background", "/characters/abc/background/pdf"})
    void backgroundRoute_identifierNotAUuid_returnsBadRequestWithoutCallingService(String path) {
        MvcTestResult result = mvc.get().uri(path).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service, pdfService);
    }

    @Test
    void updateCharacterBackground_validRequest_returnsNewVersionAndPassesContentAndVersionToService() {
        when(service.updateCharacterBackground(eq(CHARACTER_ID), any())).thenReturn(background(5L));

        MvcTestResult result = mvc.put().uri("/characters/{characterId}/background", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + ",\"version\":4}").exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.version").isEqualTo(5);
        verify(service).updateCharacterBackground(CHARACTER_ID, new UpdateCharacterBackgroundDTO(CONTENT, 4L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"content\":{},\"version\":0}", "{\"content\":null,\"version\":0}",
            "{\"content\":{\"type\":\"doc\",\"content\":[]}}", "{\"content\":{\"type\":\"doc\",\"content\":[]},\"version\":null}"})
    void updateCharacterBackground_missingContentOrVersion_returnsBadRequestWithoutCallingService(String body) {
        MvcTestResult result = mvc.put().uri("/characters/{characterId}/background", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void getCharacterBackgroundPdf_existing_returnsAttachmentPdfWithTheBytesAndFileName() {
        when(pdfService.getCharacterBackgroundPdf(CHARACTER_ID)).thenReturn(new BackgroundPdf("background-alira.pdf", new byte[]{37, 80, 68, 70}));

        MvcTestResult result = mvc.get().uri("/characters/{characterId}/background/pdf", CHARACTER_ID).exchange();

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF)
                .headers().hasValue(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"background-alira.pdf\"; filename*=UTF-8''background-alira.pdf");
        assertThat(result).body().isEqualTo(new byte[]{37, 80, 68, 70});
    }
}

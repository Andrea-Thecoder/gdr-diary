package it.liberheroum.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.liberheroum.controller.DiaryPageController;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.diary_page.BaseDiaryPageDTO;
import it.liberheroum.dto.diary_page.InsertDiaryPageDTO;
import it.liberheroum.dto.diary_page.UpdateDiaryPageDTO;
import it.liberheroum.exception.ExceptionMapper;
import it.liberheroum.services.DiaryPageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DiaryPageControllerTest {

    private static final UUID DIARY_ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");
    private static final UUID PAGE_ID = UUID.fromString("9a7b6c5d-4e3f-4a1b-8c2d-0e1f2a3b4c5d");
    private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));
    private static final String CONTENT_JSON = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";

    @Mock
    private DiaryPageService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new DiaryPageController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private BaseDiaryPageDTO page(int number, long version) {
        return new BaseDiaryPageDTO(PAGE_ID, (short) number, CONTENT, version, null);
    }

    @Test
    void findAllDiaryPagesByDiary_noPageParameter_returnsFirstSpreadWithContentAndVersion() {
        when(service.findAllDiaryPagesByDiary(DIARY_ID, 1)).thenReturn(PagedResultDTO.of(List.of(page(1, 7L)), 1, 1, 2));

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", DIARY_ID)).bodyJson()
                .extractingPath("$.list[0]['pageNumber','version','content']")
                .isEqualTo(Map.of("pageNumber", 1, "version", 7, "content", CONTENT));
    }

    @Test
    void findAllDiaryPagesByDiary_pageParameter_requestsThatSpread() {
        when(service.findAllDiaryPagesByDiary(DIARY_ID, 3)).thenReturn(PagedResultDTO.of(List.of(), 0, 3, 2));

        mvc.get().uri("/diaries/{diaryId}/pages?page=3", DIARY_ID).exchange();

        verify(service).findAllDiaryPagesByDiary(DIARY_ID, 3);
    }

    @Test
    void createDiaryPage_validContent_returnsCreatedPageAndPassesContentToService() {
        when(service.createDiaryPage(eq(DIARY_ID), any())).thenReturn(page(5, 1L));

        MvcTestResult result = mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.pageNumber").isEqualTo(5);
        verify(service).createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    void createDiaryPage_missingContent_returnsBadRequestOnContentFieldWithoutCallingService(String body) {
        MvcTestResult result = mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo("content");
        verifyNoInteractions(service);
    }

    static Stream<String> invalidCreateBodies() {
        return Stream.of("{}", "{\"content\":{}}", "{\"content\":null}");
    }

    @Test
    void updateDiaryPage_validBody_returnsNewVersionAndPassesContentAndVersionToService() {
        when(service.updateDiaryPage(eq(PAGE_ID), any())).thenReturn(page(1, 5L));

        MvcTestResult result = mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + ",\"version\":4}").exchange();

        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(5);
        verify(service).updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateBodies")
    void updateDiaryPage_invalidBody_returnsBadRequestOnFieldWithoutCallingService(String body, String field) {
        MvcTestResult result = mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidUpdateBodies() {
        return Stream.of(
                Arguments.of("{\"content\":" + CONTENT_JSON + "}", "version"),
                Arguments.of("{\"content\":{},\"version\":4}", "content"),
                Arguments.of("{\"version\":4}", "content"));
    }

    @Test
    void deleteDiaryPage_ownedPage_deletesThroughServiceAndReturnsConfirmationMessage() {
        MvcTestResult result = mvc.delete().uri("/pages/{id}", PAGE_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Page deleted");
        verify(service).deleteDiaryPage(PAGE_ID);
    }
}

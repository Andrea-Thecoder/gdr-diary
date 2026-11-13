package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.controller.DiaryPageTagController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page_tag.BaseDiaryTagDTO;
import it.gdr.diary.dto.diary_page_tag.DetailDiaryPageTagDTO;
import it.gdr.diary.dto.diary_page_tag.UpdateDiaryPageTagDTO;
import it.gdr.diary.dto.search.DiaryTagSearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.DiaryPageTagService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DiaryPageTagControllerTest {

    private static final UUID DIARY_ID = UUID.fromString("5c1e8a42-93d7-4f0b-8a16-0d6b7e2f9a31");
    private static final UUID PAGE_ID = UUID.fromString("7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45");

    @Mock
    private DiaryPageTagService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new DiaryPageTagController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private String tagBody(String tag) {
        return "{\"tag\":\"" + tag + "\"}";
    }

    @Test
    void updateDiaryPageTag_validBody_returnsTagOfPage() {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any())).thenReturn(new DetailDiaryPageTagDTO("combat"));

        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("combat")))
                .bodyJson().extractingPath("$.tag").isEqualTo("combat");
    }

    @Test
    void updateDiaryPageTag_validBody_passesTagToService() {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any())).thenReturn(new DetailDiaryPageTagDTO("combat"));

        mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("combat")).exchange();

        verify(service).updateDiaryPageTag(PAGE_ID, new UpdateDiaryPageTagDTO("combat"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"tag\":null}", "{}"})
    void updateDiaryPageTag_nullTag_removesTagAndReturnsOk(String body) {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any())).thenReturn(new DetailDiaryPageTagDTO(null));

        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatusOk();
        verify(service).updateDiaryPageTag(PAGE_ID, new UpdateDiaryPageTagDTO(null));
    }

    @Test
    void updateDiaryPageTag_removedTag_returnsNullTag() {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any())).thenReturn(new DetailDiaryPageTagDTO(null));

        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tag\":null}"))
                .bodyJson().extractingPath("$.tag").isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"tag\":\"\"}", "{\"tag\":\"   \"}", "{\"tag\":[\"a\"]}",
            "{\"tag\":\"a\\nb\"}", "{\"tag\":\"a\\u0000b\"}"})
    void updateDiaryPageTag_invalidBody_returnsBadRequest(String body) {
        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTag_tagOfThirtyOneCharacters_returnsBadRequest() {
        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("a".repeat(31))))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTag_invalidBody_doesNotCallService() {
        mvc.put().uri("/pages/{id}/tag", PAGE_ID).contentType(MediaType.APPLICATION_JSON).content("{\"tag\":\"\"}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void updateDiaryPageTag_tagOfThirtyCharacters_returnsOk() {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any())).thenReturn(new DetailDiaryPageTagDTO("a".repeat(30)));

        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("a".repeat(30))))
                .hasStatusOk();
    }

    @Test
    void updateDiaryPageTag_oldPluralPath_isNotMapped() {
        assertThat(mvc.put().uri("/pages/{id}/tags", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("combat")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTag_pageNotFound_returnsNotFound() {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));

        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("combat")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTag_serviceRejectsTag_returnsReasonAsViolation() {
        when(service.updateDiaryPageTag(eq(PAGE_ID), any()))
                .thenThrow(new ServiceException("A diary can have at most 10 different tags"));

        assertThat(mvc.put().uri("/pages/{id}/tag", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("combat")))
                .bodyJson().extractingPath("$.violations[0].message")
                .isEqualTo("A diary can have at most 10 different tags");
    }

    @Test
    void updateDiaryPageTag_pageIdNotUuid_returnsBadRequest() {
        assertThat(mvc.put().uri("/pages/{id}/tag", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON).content(tagBody("combat")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private PagedResultDTO<BaseDiaryTagDTO> tagsPage() {
        return PagedResultDTO.of(List.of(new BaseDiaryTagDTO("combat", List.of((short) 1, (short) 4))), 1, 1, 20);
    }

    private DiaryTagSearchRequest capturedSearchRequest() {
        ArgumentCaptor<DiaryTagSearchRequest> captor = ArgumentCaptor.forClass(DiaryTagSearchRequest.class);
        verify(service).findAllDiaryTagsByDiary(eq(DIARY_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void findAllDiaryTagsByDiary_noParameters_returnsTagsWithPageNumbers() {
        when(service.findAllDiaryTagsByDiary(eq(DIARY_ID), any())).thenReturn(tagsPage());

        assertThat(mvc.get().uri("/diaries/{diaryId}/tags", DIARY_ID))
                .hasStatusOk()
                .bodyJson().extractingPath("$.list[0].pageNumbers").isEqualTo(List.of(1, 4));
    }

    @Test
    void findAllDiaryTagsByDiary_noParameters_usesDefaultPaging() {
        when(service.findAllDiaryTagsByDiary(eq(DIARY_ID), any())).thenReturn(tagsPage());

        mvc.get().uri("/diaries/{diaryId}/tags", DIARY_ID).exchange();

        DiaryTagSearchRequest request = capturedSearchRequest();
        assertThat(request.getPage()).isEqualTo(1);
        assertThat(request.getSize()).isEqualTo(20);
        assertThat(request.getName()).isNull();
    }

    @Test
    void findAllDiaryTagsByDiary_parametersGiven_passesThemToService() {
        when(service.findAllDiaryTagsByDiary(eq(DIARY_ID), any())).thenReturn(tagsPage());

        mvc.get().uri("/diaries/{diaryId}/tags?name=comb&page=2&size=50", DIARY_ID).exchange();

        DiaryTagSearchRequest request = capturedSearchRequest();
        assertThat(request.getName()).isEqualTo("comb");
        assertThat(request.getPage()).isEqualTo(2);
        assertThat(request.getSize()).isEqualTo(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "size=0", "size=101", "name=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void findAllDiaryTagsByDiary_invalidParameters_returnsBadRequest(String query) {
        assertThat(mvc.get().uri("/diaries/{diaryId}/tags?" + query, DIARY_ID)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryTagsByDiary_invalidParameters_doesNotCallService() {
        mvc.get().uri("/diaries/{diaryId}/tags?page=0", DIARY_ID).exchange();

        verify(service, never()).findAllDiaryTagsByDiary(any(), any());
    }

    @Test
    void findAllDiaryTagsByDiary_diaryNotFound_returnsNotFound() {
        when(service.findAllDiaryTagsByDiary(eq(DIARY_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        assertThat(mvc.get().uri("/diaries/{diaryId}/tags", DIARY_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllDiaryTagsByDiary_diaryIdNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/diaries/{diaryId}/tags", "not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}

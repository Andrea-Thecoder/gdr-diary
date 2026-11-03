package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.controller.DiaryPageController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page.BaseDiaryPageDTO;
import it.gdr.diary.dto.diary_page.InsertDiaryPageDTO;
import it.gdr.diary.dto.diary_page.UpdateDiaryPageDTO;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.DiaryPageService;
import jakarta.persistence.OptimisticLockException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

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
        return new BaseDiaryPageDTO(PAGE_ID, (short) number, CONTENT, version);
    }

    @Test
    void findAllDiaryPagesByDiary_noPageParameter_requestsFirstSpread() {
        when(service.findAllDiaryPagesByDiary(DIARY_ID, 1)).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 2));

        mvc.get().uri("/diaries/{diaryId}/pages", DIARY_ID).exchange();

        verify(service).findAllDiaryPagesByDiary(DIARY_ID, 1);
    }

    @Test
    void findAllDiaryPagesByDiary_pageParameter_requestsThatSpread() {
        when(service.findAllDiaryPagesByDiary(DIARY_ID, 3)).thenReturn(PagedResultDTO.of(List.of(), 0, 3, 2));

        mvc.get().uri("/diaries/{diaryId}/pages?page=3", DIARY_ID).exchange();

        verify(service).findAllDiaryPagesByDiary(DIARY_ID, 3);
    }

    @Test
    void findAllDiaryPagesByDiary_pagesFound_returnsContentAndVersion() {
        when(service.findAllDiaryPagesByDiary(DIARY_ID, 1)).thenReturn(PagedResultDTO.of(List.of(page(1, 7L)), 1, 1, 2));

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", DIARY_ID)).bodyJson()
                .extractingPath("$.list[0].version").isEqualTo(7);
    }

    @Test
    void findAllDiaryPagesByDiary_pageBelowOne_returnsBadRequest() {
        when(service.findAllDiaryPagesByDiary(DIARY_ID, 0)).thenThrow(new ServiceException("The page must be 1 or greater"));

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=0", DIARY_ID)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_validContent_returnsCreated() {
        when(service.createDiaryPage(eq(DIARY_ID), any())).thenReturn(page(1, 1L));

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createDiaryPage_validContent_passesContentToService() {
        when(service.createDiaryPage(eq(DIARY_ID), any())).thenReturn(page(1, 1L));

        mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}").exchange();

        verify(service).createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));
    }

    @Test
    void createDiaryPage_validContent_returnsAssignedPageNumber() {
        when(service.createDiaryPage(eq(DIARY_ID), any())).thenReturn(page(5, 1L));

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}"))
                .bodyJson().extractingPath("$.pageNumber").isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"content\":{}}", "{\"content\":null}"})
    void createDiaryPage_missingContent_returnsBadRequest(String body) {
        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_missingContent_doesNotCallService() {
        mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void createDiaryPage_diaryFull_returnsReasonAsViolation() {
        when(service.createDiaryPage(eq(DIARY_ID), any())).thenThrow(new ServiceException("The diary is full (100 pages)"));

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("The diary is full (100 pages)");
    }

    @Test
    void updateDiaryPage_validBody_returnsNewVersion() {
        when(service.updateDiaryPage(eq(PAGE_ID), any())).thenReturn(page(1, 5L));

        assertThat(mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + ",\"version\":4}"))
                .bodyJson().extractingPath("$.version").isEqualTo(5);
    }

    @Test
    void updateDiaryPage_validBody_passesContentAndVersionToService() {
        when(service.updateDiaryPage(eq(PAGE_ID), any())).thenReturn(page(1, 5L));

        mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + ",\"version\":4}").exchange();

        verify(service).updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));
    }

    @Test
    void updateDiaryPage_missingVersion_returnsBadRequest() {
        assertThat(mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPage_staleVersion_returnsConflict() {
        when(service.updateDiaryPage(eq(PAGE_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "The page was modified elsewhere, reload it"));

        assertThat(mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + ",\"version\":4}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateDiaryPage_concurrentSave_returnsConflict() {
        when(service.updateDiaryPage(eq(PAGE_ID), any())).thenThrow(new OptimisticLockException("version changed"));

        assertThat(mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + ",\"version\":4}"))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteDiaryPage_ownedPage_returnsConfirmationMessage() {
        assertThat(mvc.delete().uri("/pages/{id}", PAGE_ID)).bodyJson()
                .extractingPath("$.message").isEqualTo("Page deleted");
    }

    @Test
    void deleteDiaryPage_ownedPage_deletesThroughService() {
        mvc.delete().uri("/pages/{id}", PAGE_ID).exchange();

        verify(service).deleteDiaryPage(PAGE_ID);
    }

    @Test
    void deleteDiaryPage_notFound_returnsNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found")).when(service).deleteDiaryPage(PAGE_ID);

        assertThat(mvc.delete().uri("/pages/{id}", PAGE_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllDiaryPagesByDiary_unexpectedError_returnsInternalServerError() {
        when(service.findAllDiaryPagesByDiary(eq(DIARY_ID), anyInt())).thenThrow(new IllegalStateException("boom"));

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", DIARY_ID)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void updateDiaryPage_emptyContent_returnsBadRequest() {
        assertThat(mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":{},\"version\":4}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPage_emptyContent_doesNotCallService() {
        mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":{},\"version\":4}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void updateDiaryPage_missingVersion_returnsViolationOnVersionField() {
        assertThat(mvc.put().uri("/pages/{id}", PAGE_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + CONTENT_JSON + "}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("version");
    }
}

package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.ExpressionList;
import it.gdr.diary.controller.DiaryController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary.BaseDiaryDTO;
import it.gdr.diary.dto.diary.DetailDiaryDTO;
import it.gdr.diary.dto.diary.InsertDiaryDTO;
import it.gdr.diary.dto.diary.UpdateDiaryDTO;
import it.gdr.diary.dto.search.DiarySearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.DiaryService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DiaryControllerTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final UUID DIARY_ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");
    private static final String VALID_BODY = """
            {"title":"Chapter one","description":"The journey begins","coverColor":"#1A2B3C","font":"Lora","fontSize":16}
            """;

    @Mock
    private DiaryService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new DiaryController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private DetailDiaryDTO detail(String title) {
        return new DetailDiaryDTO(DIARY_ID, CHARACTER_ID, title, null, "#1A2B3C", "Lora", (short) 16, null, null);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "cover.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    @Test
    void findAllDiariesByCharacter_diariesFound_returnsPagedList() {
        BaseDiaryDTO diary = new BaseDiaryDTO(DIARY_ID, CHARACTER_ID, "Chapter one", null, null);
        when(service.findAllDiariesByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(diary), 1, 1, 20));

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", CHARACTER_ID)).bodyJson()
                .extractingPath("$.list[0].title").isEqualTo("Chapter one");
    }

    @Test
    void findAllDiariesByCharacter_queryParameters_bindsSearchRequest() {
        when(service.findAllDiariesByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));
        ArgumentCaptor<DiarySearchRequest> request = ArgumentCaptor.forClass(DiarySearchRequest.class);

        mvc.get().uri("/characters/{characterId}/diaries?title=chap&sort=title&descending=true", CHARACTER_ID).exchange();

        verify(service).findAllDiariesByCharacter(eq(CHARACTER_ID), request.capture());
        assertThat(request.getValue())
                .extracting(DiarySearchRequest::getTitle, DiarySearchRequest::getSort, DiarySearchRequest::isDescending)
                .containsExactly("chap", "title", true);
    }

    @Test
    void findAllDiariesByCharacter_titleTooLong_returnsBadRequest() {
        assertThat(mvc.get().uri("/characters/{characterId}/diaries?title=" + "a".repeat(256), CHARACTER_ID))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiariesByCharacter_characterNotFound_returnsNotFound() {
        when(service.findAllDiariesByCharacter(eq(CHARACTER_ID), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", CHARACTER_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDiary_validBody_returnsCreated() {
        when(service.createDiary(eq(CHARACTER_ID), any())).thenReturn(detail("Chapter one"));

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createDiary_validBody_passesCharacterAndBodyToService() {
        when(service.createDiary(eq(CHARACTER_ID), any())).thenReturn(detail("Chapter one"));

        mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

        verify(service).createDiary(CHARACTER_ID, new InsertDiaryDTO("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"title\":\"  \"}",
            "{\"title\":\"Chapter\",\"coverColor\":\"blue\"}",
            "{\"title\":\"Chapter\",\"fontSize\":0}",
            "{\"title\":\"Chapter\",\"fontSize\":-4}"})
    void createDiary_invalidBody_returnsBadRequest(String body) {
        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiary_descriptionTooLong_returnsBadRequest() {
        String body = "{\"title\":\"Chapter\",\"description\":\"" + "a".repeat(2001) + "\"}";

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiary_invalidBody_doesNotCallService() {
        mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        verifyNoInteractions(service);
    }

    @Test
    void getDiaryById_ownedDiary_returnsDetail() {
        when(service.getDiaryById(DIARY_ID)).thenReturn(detail("Chapter one"));

        assertThat(mvc.get().uri("/diaries/{id}", DIARY_ID)).bodyJson()
                .extractingPath("$.characterId").isEqualTo(CHARACTER_ID.toString());
    }

    @Test
    void getDiaryById_notFound_returnsNotFound() {
        when(service.getDiaryById(DIARY_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found"));

        assertThat(mvc.get().uri("/diaries/{id}", DIARY_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiary_validBody_returnsDetail() {
        when(service.updateDiary(eq(DIARY_ID), any())).thenReturn(detail("Chapter two"));

        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .bodyJson().extractingPath("$.title").isEqualTo("Chapter two");
    }

    @Test
    void updateDiary_validBody_passesIdAndBodyToService() {
        when(service.updateDiary(eq(DIARY_ID), any())).thenReturn(detail("Chapter one"));

        mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

        verify(service).updateDiary(DIARY_ID, new UpdateDiaryDTO("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16));
    }

    @Test
    void updateDiary_blankTitle_returnsBadRequest() {
        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteDiary_ownedDiary_returnsConfirmationMessage() {
        assertThat(mvc.delete().uri("/diaries/{id}", DIARY_ID)).bodyJson()
                .extractingPath("$.message").isEqualTo("Diary deleted");
    }

    @Test
    void deleteDiary_ownedDiary_deletesThroughService() {
        mvc.delete().uri("/diaries/{id}", DIARY_ID).exchange();

        verify(service).deleteDiary(DIARY_ID);
    }

    @Test
    void deleteDiary_notFound_returnsNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found")).when(service).deleteDiary(DIARY_ID);

        assertThat(mvc.delete().uri("/diaries/{id}", DIARY_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryCover_imagePart_returnsDetail() {
        when(service.updateDiaryCover(eq(DIARY_ID), any())).thenReturn(detail("Chapter one"));

        assertThat(mvc.put().uri("/diaries/{id}/cover", DIARY_ID).multipart().file(image()))
                .bodyJson().extractingPath("$.id").isEqualTo(DIARY_ID.toString());
    }

    @Test
    void updateDiaryCover_invalidFile_returnsBadRequest() {
        when(service.updateDiaryCover(eq(DIARY_ID), any())).thenThrow(new ServiceException("File too large"));

        assertThat(mvc.put().uri("/diaries/{id}/cover", DIARY_ID).multipart().file(image()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteDiaryCover_ownedDiary_returnsConfirmationMessage() {
        assertThat(mvc.delete().uri("/diaries/{id}/cover", DIARY_ID)).bodyJson()
                .extractingPath("$.message").isEqualTo("Cover removed");
    }

    @Test
    void deleteDiaryCover_ownedDiary_deletesThroughService() {
        mvc.delete().uri("/diaries/{id}/cover", DIARY_ID).exchange();

        verify(service).deleteDiaryCover(DIARY_ID);
    }

    @Test
    void createDiary_titleOf255Chars_returnsCreated() {
        when(service.createDiary(eq(CHARACTER_ID), any())).thenReturn(detail("a".repeat(255)));
        String body = "{\"title\":\"" + "a".repeat(255) + "\"}";

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createDiary_titleOf256Chars_returnsBadRequest() {
        String body = "{\"title\":\"" + "a".repeat(256) + "\"}";

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiary_titleOf255Chars_returnsOk() {
        when(service.updateDiary(eq(DIARY_ID), any())).thenReturn(detail("a".repeat(255)));
        String body = "{\"title\":\"" + "a".repeat(255) + "\"}";

        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void updateDiary_titleOf256Chars_returnsBadRequest() {
        String body = "{\"title\":\"" + "a".repeat(256) + "\"}";

        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\"Chapter\",\"coverColor\":\"blue\"}",
            "{\"title\":\"Chapter\",\"coverColor\":\"#12345\"}",
            "{\"title\":\"Chapter\",\"fontSize\":0}",
            "{\"title\":\"Chapter\",\"fontSize\":-4}"})
    void updateDiary_invalidBody_returnsBadRequest(String body) {
        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiary_descriptionTooLong_returnsBadRequest() {
        String body = "{\"title\":\"Chapter\",\"description\":\"" + "a".repeat(2001) + "\"}";

        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiary_invalidBody_doesNotCallService() {
        mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Chapter\",\"fontSize\":0}").exchange();

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "size=0", "size=101"})
    void findAllDiariesByCharacter_paginationOutOfRange_returnsBadRequest(String query) {
        assertThat(mvc.get().uri("/characters/{characterId}/diaries?" + query, CHARACTER_ID))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiariesByCharacter_paginationOutOfRange_doesNotCallService() {
        mvc.get().uri("/characters/{characterId}/diaries?size=101", CHARACTER_ID).exchange();

        verifyNoInteractions(service);
    }

    @Test
    void createDiary_blankTitle_returnsViolationOnTitleField() {
        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("title");
    }

    @Test
    void createDiary_invalidCoverColor_returnsViolationOnCoverColorField() {
        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Chapter\",\"coverColor\":\"blue\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("coverColor");
    }

    @Test
    void createDiary_negativeFontSize_returnsViolationOnFontSizeField() {
        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Chapter\",\"fontSize\":-4}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("fontSize");
    }

    @Test
    void updateDiary_blankTitle_returnsViolationOnTitleField() {
        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("title");
    }

    @Test
    void updateDiary_invalidCoverColor_returnsViolationOnCoverColorField() {
        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Chapter\",\"coverColor\":\"#12345\"}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("coverColor");
    }

    @Test
    void updateDiary_negativeFontSize_returnsViolationOnFontSizeField() {
        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Chapter\",\"fontSize\":-1}"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("fontSize");
    }

    @Test
    void findAllDiariesByCharacter_sizeOutOfRange_returnsViolationOnSizeField() {
        assertThat(mvc.get().uri("/characters/{characterId}/diaries?size=101", CHARACTER_ID))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("size");
    }

    @Test
    void findAllDiariesByCharacter_pageNotANumber_returnsReadableViolationMessage() {
        assertThat(mvc.get().uri("/characters/{characterId}/diaries?page=abc", CHARACTER_ID))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Invalid value");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllDiariesByCharacter_injectedSortFields_sortsByDefaultField() {
        when(service.findAllDiariesByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));
        ArgumentCaptor<DiarySearchRequest> request = ArgumentCaptor.forClass(DiarySearchRequest.class);
        ExpressionList<Object> query = mock(ExpressionList.class, RETURNS_SELF);

        mvc.get().uri("/characters/{characterId}/diaries?SORT_FIELDS[character.owner.email]=character.owner.email&sortFields[character.owner.email]=character.owner.email&sort=character.owner.email", CHARACTER_ID).exchange();

        verify(service).findAllDiariesByCharacter(eq(CHARACTER_ID), request.capture());
        request.getValue().applySortAndPagination(query, "title");
        verify(query).orderBy("title asc, id asc");
    }
}

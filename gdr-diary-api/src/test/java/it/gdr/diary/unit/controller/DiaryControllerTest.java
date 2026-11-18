package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.ExpressionList;
import it.gdr.diary.controller.DiaryController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary.BaseDiaryDTO;
import it.gdr.diary.dto.diary.BookmarkDiaryDTO;
import it.gdr.diary.dto.diary.DetailDiaryDTO;
import it.gdr.diary.dto.diary.InsertDiaryDTO;
import it.gdr.diary.dto.diary.UpdateBookmarkDiaryDTO;
import it.gdr.diary.dto.diary.UpdateDiaryDTO;
import it.gdr.diary.dto.search.DiarySearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.services.DiaryService;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DiaryControllerTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final UUID DIARY_ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");
    private static final UUID PAGE_ID = UUID.fromString("7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45");
    private static final String BOOKMARK_BODY = "{\"pageId\":\"" + PAGE_ID + "\"}";
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
    void findAllDiariesByCharacter_filtersAndSort_returnsPagedListAndBindsSearchRequest() {
        BaseDiaryDTO diary = new BaseDiaryDTO(DIARY_ID, CHARACTER_ID, "Chapter one", null, null);
        when(service.findAllDiariesByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(diary), 1, 1, 20));
        ArgumentCaptor<DiarySearchRequest> request = ArgumentCaptor.forClass(DiarySearchRequest.class);

        MvcTestResult result = mvc.get()
                .uri("/characters/{characterId}/diaries?title=chap&tag=combat&sort=title&descending=true", CHARACTER_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.list[0].title").isEqualTo("Chapter one");
        verify(service).findAllDiariesByCharacter(eq(CHARACTER_ID), request.capture());
        assertThat(request.getValue())
                .extracting(DiarySearchRequest::getTitle, DiarySearchRequest::getTag, DiarySearchRequest::getSort, DiarySearchRequest::isDescending)
                .containsExactly("chap", "combat", "title", true);
    }

    @ParameterizedTest
    @MethodSource("invalidSearchQueries")
    void findAllDiariesByCharacter_invalidQuery_returnsBadRequestOnFieldWithoutCallingService(String query, String field) {
        MvcTestResult result = mvc.get().uri("/characters/{characterId}/diaries?" + query, CHARACTER_ID).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidSearchQueries() {
        return Stream.of(
                Arguments.of("title=" + "a".repeat(256), "title"),
                Arguments.of("tag=" + "a".repeat(31), "tag"),
                Arguments.of("page=0", "page"),
                Arguments.of("size=0", "size"),
                Arguments.of("size=101", "size"));
    }

    @Test
    void findAllDiariesByCharacter_tagOfThirtyCharacters_returnsOk() {
        when(service.findAllDiariesByCharacter(eq(CHARACTER_ID), any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?tag=" + "a".repeat(30), CHARACTER_ID)).hasStatusOk();
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

    @Test
    void createDiary_validBody_returnsCreatedAndPassesCharacterAndBodyToService() {
        when(service.createDiary(eq(CHARACTER_ID), any())).thenReturn(detail("Chapter one"));

        MvcTestResult result = mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        verify(service).createDiary(CHARACTER_ID, new InsertDiaryDTO("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16));
    }

    @Test
    void createDiary_titleOf255Chars_returnsCreated() {
        when(service.createDiary(eq(CHARACTER_ID), any())).thenReturn(detail("a".repeat(255)));
        String body = "{\"title\":\"" + "a".repeat(255) + "\"}";

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @MethodSource("invalidCreateBodies")
    void createDiary_invalidBody_returnsBadRequestOnFieldWithoutCallingService(String body, String field) {
        MvcTestResult result = mvc.post().uri("/characters/{characterId}/diaries", CHARACTER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidCreateBodies() {
        return Stream.concat(
                Stream.of(Arguments.of("{}", "title"), Arguments.of("{\"title\":\"  \"}", "title")),
                invalidFieldBodies());
    }

    static Stream<Arguments> invalidFieldBodies() {
        return Stream.of(
                Arguments.of("{\"title\":\"" + "a".repeat(256) + "\"}", "title"),
                Arguments.of("{\"title\":\"Chapter\",\"coverColor\":\"blue\"}", "coverColor"),
                Arguments.of("{\"title\":\"Chapter\",\"coverColor\":\"#12345\"}", "coverColor"),
                Arguments.of("{\"title\":\"Chapter\",\"fontSize\":0}", "fontSize"),
                Arguments.of("{\"title\":\"Chapter\",\"fontSize\":-4}", "fontSize"),
                Arguments.of("{\"title\":\"Chapter\",\"description\":\"" + "a".repeat(2001) + "\"}", "description"));
    }

    @Test
    void getDiaryById_ownedDiary_returnsDetail() {
        when(service.getDiaryById(DIARY_ID)).thenReturn(detail("Chapter one"));

        assertThat(mvc.get().uri("/diaries/{id}", DIARY_ID)).bodyJson()
                .extractingPath("$['characterId','title']")
                .isEqualTo(Map.of("characterId", CHARACTER_ID.toString(), "title", "Chapter one"));
    }

    @Test
    void updateDiary_validBody_returnsDetailAndPassesIdAndBodyToService() {
        when(service.updateDiary(eq(DIARY_ID), any())).thenReturn(detail("Chapter two"));

        MvcTestResult result = mvc.put().uri("/diaries/{id}", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Chapter two");
        verify(service).updateDiary(DIARY_ID, new UpdateDiaryDTO("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16));
    }

    @Test
    void updateDiary_titleOf255Chars_returnsOk() {
        when(service.updateDiary(eq(DIARY_ID), any())).thenReturn(detail("a".repeat(255)));
        String body = "{\"title\":\"" + "a".repeat(255) + "\"}";

        assertThat(mvc.put().uri("/diaries/{id}", DIARY_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.OK);
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateBodies")
    void updateDiary_invalidBody_returnsBadRequestOnFieldWithoutCallingService(String body, String field) {
        MvcTestResult result = mvc.put().uri("/diaries/{id}", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidUpdateBodies() {
        return Stream.concat(Stream.of(Arguments.of("{\"title\":\"\"}", "title")), invalidFieldBodies());
    }

    @Test
    void deleteDiary_ownedDiary_deletesThroughServiceAndReturnsConfirmationMessage() {
        MvcTestResult result = mvc.delete().uri("/diaries/{id}", DIARY_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Diary deleted");
        verify(service).deleteDiary(DIARY_ID);
    }

    @Test
    void updateDiaryCover_imagePart_returnsDetail() {
        when(service.updateDiaryCover(eq(DIARY_ID), any())).thenReturn(detail("Chapter one"));

        assertThat(mvc.put().uri("/diaries/{id}/cover", DIARY_ID).multipart().file(image()))
                .bodyJson().extractingPath("$.id").isEqualTo(DIARY_ID.toString());
    }

    @Test
    void deleteDiaryCover_ownedDiary_deletesThroughServiceAndReturnsConfirmationMessage() {
        MvcTestResult result = mvc.delete().uri("/diaries/{id}/cover", DIARY_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Cover removed");
        verify(service).deleteDiaryCover(DIARY_ID);
    }

    @Test
    void getDiaryBookmark_diaryWithBookmark_returnsPageIdAndNumber() {
        when(service.getDiaryBookmark(DIARY_ID)).thenReturn(new BookmarkDiaryDTO(PAGE_ID, (short) 12));

        assertThat(mvc.get().uri("/diaries/{id}/bookmark", DIARY_ID)).bodyJson()
                .extractingPath("$['pageId','pageNumber']")
                .isEqualTo(Map.of("pageId", PAGE_ID.toString(), "pageNumber", 12));
    }

    @Test
    void updateDiaryBookmark_validBody_returnsBookmarkAndPassesPageIdToService() {
        when(service.updateDiaryBookmark(eq(DIARY_ID), any())).thenReturn(new BookmarkDiaryDTO(PAGE_ID, (short) 3));

        MvcTestResult result = mvc.put().uri("/diaries/{id}/bookmark", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content(BOOKMARK_BODY).exchange();

        assertThat(result).bodyJson().extractingPath("$.pageNumber").isEqualTo(3);
        verify(service).updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"pageId\":null}", "{\"pageId\":\"abc\"}", "{\"pageId\":12}", ""})
    void updateDiaryBookmark_invalidBody_returnsBadRequestWithoutCallingService(String body) {
        MvcTestResult result = mvc.put().uri("/diaries/{id}/bookmark", DIARY_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void deleteDiaryBookmark_ownedDiary_deletesThroughServiceAndReturnsConfirmationMessage() {
        MvcTestResult result = mvc.delete().uri("/diaries/{id}/bookmark", DIARY_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Bookmark removed");
        verify(service).deleteDiaryBookmark(DIARY_ID);
    }
}

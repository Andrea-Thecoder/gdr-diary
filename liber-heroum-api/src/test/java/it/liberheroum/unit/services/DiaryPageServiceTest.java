package it.liberheroum.unit.services;

import static it.liberheroum.unit.support.QueryStub.pagedList;
import static it.liberheroum.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.liberheroum.config.DiaryPageConfig;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.diary_page.BaseDiaryPageDTO;
import it.liberheroum.dto.diary_page.InsertDiaryPageDTO;
import it.liberheroum.dto.diary_page.UpdateDiaryPageDTO;
import it.liberheroum.exception.ServiceException;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import it.liberheroum.security.JwtInspector;
import it.liberheroum.services.DiaryPageContentValidator;
import it.liberheroum.services.OwnedResources;
import it.liberheroum.services.DiaryPageService;
import it.liberheroum.services.DiaryPageTagService;
import it.liberheroum.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DiaryPageServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID DIARY_ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");
    private static final UUID PAGE_ID = UUID.fromString("9a7b6c5d-4e3f-4a1b-8c2d-0e1f2a3b4c5d");
    private static final Map<String, Object> CONTENT = Map.of("type", "doc", "content", List.of(Map.of("type", "paragraph")));

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private DiaryPageContentValidator contentValidator;
    @Mock
    private Transaction transaction;
    @Mock
    private DiaryPageTagService tagService;

    private final DiaryPageConfig config = new DiaryPageConfig();

    private DiaryPageService service;

    @BeforeEach
    void setUp() {
        service = new DiaryPageService(database, JsonMapper.builder().build(), config, contentValidator, tagService, new OwnedResources(database, jwtInspector));
    }

    private Diary diary() {
        Diary diary = new Diary();
        diary.setId(DIARY_ID);
        return diary;
    }

    private DiaryPage page(int number, long version) {
        DiaryPage page = new DiaryPage();
        page.setId(UUID.randomUUID());
        page.setDiary(diary());
        page.setPageNumber((short) number);
        page.setContent(CONTENT);
        page.setVersion(version);
        return page;
    }

    private QueryStub<Diary> givenOwnedDiary(Diary diary) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(diary);
        return diaries;
    }

    private QueryStub<DiaryPage> givenOwnedPage(DiaryPage page) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(pages.where().findOne()).thenReturn(page);
        return pages;
    }

    private QueryStub<DiaryPage> givenPageCount(int count) {
        when(database.beginTransaction()).thenReturn(transaction);
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(pages.where().findCount()).thenReturn(count);
        return pages;
    }

    private QueryStub<Diary> givenLockedDiary() {
        when(database.beginTransaction()).thenReturn(transaction);
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(diary());
        return diaries;
    }

    private QueryStub<DiaryPage> givenPageList(List<DiaryPage> found) {
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        PagedList<DiaryPage> paged = pagedList(found, 0, 2, found.size());
        when(pages.where().findPagedList()).thenReturn(paged);
        return pages;
    }

    @Test
    void findAllDiaryPagesByDiary_pagesWithTags_returnsPagesWithVersionAndTagOfEachPage() {
        givenOwnedDiary(diary());
        DiaryPage first = page(1, 3L);
        DiaryPage second = page(2, 7L);
        givenPageList(List.of(first, second));
        when(tagService.findTagByPages(List.of(first.getId(), second.getId()))).thenReturn(Map.of(first.getId(), "combat"));

        PagedResultDTO<BaseDiaryPageDTO> result = service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        assertThat(result.getList()).extracting(BaseDiaryPageDTO::pageNumber, BaseDiaryPageDTO::version, BaseDiaryPageDTO::tag)
                .containsExactly(tuple((short) 1, 3L, "combat"), tuple((short) 2, 7L, null));
    }

    @Test
    void createDiaryPage_diaryWithPages_savesPageAfterLastOneAndReturnsItWithoutTag() {
        Diary diary = diary();
        givenOwnedDiary(diary);
        givenPageCount(4);
        ArgumentCaptor<DiaryPage> saved = ArgumentCaptor.forClass(DiaryPage.class);

        BaseDiaryPageDTO result = service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        verify(database).save(saved.capture());
        assertThat(saved.getValue()).extracting(DiaryPage::getDiary, DiaryPage::getContent, DiaryPage::getPageNumber)
                .containsExactly(diary, CONTENT, (short) 5);
        assertThat(result).extracting(BaseDiaryPageDTO::pageNumber, BaseDiaryPageDTO::tag).containsExactly((short) 5, null);
    }

    @Test
    void createDiaryPage_diaryFull_throwsServiceException() {
        givenOwnedDiary(diary());
        givenPageCount(100);
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("The diary is full (100 pages)");
    }

    @Test
    void createDiaryPage_invalidContent_throwsAndDoesNotSave() {
        givenOwnedDiary(diary());
        ServiceException invalid = new ServiceException("Invalid page content at content[0]: unknown node");
        doThrow(invalid).when(contentValidator).validate(CONTENT);

        Throwable thrown = catchThrowable(() -> service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT)));

        assertThat(thrown).isSameAs(invalid);
        verify(database, never()).save(any());
    }

    @Test
    void createDiaryPage_contentOverMaxSize_throwsServiceExceptionWithoutStructuralValidation() {
        config.setMaxPageContentSize(DataSize.ofBytes(10));
        givenOwnedDiary(diary());
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Page content too large");
        verifyNoInteractions(contentValidator);
    }

    @ParameterizedTest
    @MethodSource("concurrentInsertFailures")
    void createDiaryPage_concurrentInsert_throwsServiceException(PersistenceException failure) {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(failure).when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Another page was added at the same time, try again");
    }

    static Stream<PersistenceException> concurrentInsertFailures() {
        return Stream.of(
                new DuplicateKeyException("duplicate", null),
                new RollbackException("commit failed", new SQLException("duplicate key", "23505")),
                new RollbackException("commit failed", new SQLException("check violation", "23514")),
                new RollbackException("commit failed", new SQLException("deadlock detected", "40P01")));
    }

    @Test
    void createDiaryPage_uniqueViolationOnCommit_throwsServiceException() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(new RollbackException("commit failed", new SQLException("duplicate key", "23505"))).when(transaction).commit();
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Another page was added at the same time, try again");
    }

    @Test
    void createDiaryPage_diaryDeletedBeforeCommit_throwsNotFound() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(new RollbackException("commit failed", new SQLException("foreign key violation", "23503")))
                .when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDiaryPage_foreignKeyViolationOnCommit_throwsNotFound() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(new RollbackException("commit failed", new SQLException("foreign key violation", "23503"))).when(transaction).commit();
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDiaryPage_otherDatabaseError_rethrowsIt() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request)).isSameAs(failure);
    }

    @Test
    void updateDiaryPage_sameVersionOnTaggedPage_replacesContentAndReturnsPageNumberAndTag() {
        DiaryPage page = page(3, 4L);
        givenOwnedPage(page);
        when(tagService.findTagByPages(List.of(page.getId()))).thenReturn(Map.of(page.getId(), "clues"));
        Map<String, Object> newContent = Map.of("type", "doc", "content", List.of(Map.of("type", "horizontalRule")));

        BaseDiaryPageDTO result = service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(newContent, 4L));

        verify(database).save(page);
        assertThat(page.getContent()).isEqualTo(newContent);
        assertThat(result).extracting(BaseDiaryPageDTO::pageNumber, BaseDiaryPageDTO::tag).containsExactly((short) 3, "clues");
    }

    @Test
    void deleteDiaryPage_renumberingFails_doesNotCommit() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(2, 1L));
        List<DiaryPage> following = List.of(page(3, 1L));
        when(pages.where().findList()).thenReturn(following);
        givenLockedDiary();
        when(database.saveAll(following)).thenThrow(new IllegalStateException("db down"));

        catchThrowable(() -> service.deleteDiaryPage(PAGE_ID));

        verify(transaction, never()).commit();
    }
}

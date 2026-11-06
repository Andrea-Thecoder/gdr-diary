package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.gdr.diary.config.DiaryPageConfig;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page.BaseDiaryPageDTO;
import it.gdr.diary.dto.diary_page.InsertDiaryPageDTO;
import it.gdr.diary.dto.diary_page.UpdateDiaryPageDTO;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.DiaryPageContentValidator;
import it.gdr.diary.services.DiaryPageService;
import it.gdr.diary.services.DiaryPageTagService;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
        service = new DiaryPageService(database, jwtInspector, JsonMapper.builder().build(), config, contentValidator, tagService);
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
    void findAllDiaryPagesByDiary_pageBelowOne_throwsServiceException() {
        assertThatThrownBy(() -> service.findAllDiaryPagesByDiary(DIARY_ID, 0))
                .isInstanceOf(ServiceException.class)
                .hasMessage("The page must be 1 or greater");
    }

    @Test
    void findAllDiaryPagesByDiary_pageBelowOne_doesNotQueryDatabase() {
        catchThrowable(() -> service.findAllDiaryPagesByDiary(DIARY_ID, 0));

        verifyNoInteractions(database);
    }

    @Test
    void findAllDiaryPagesByDiary_notOwnedDiary_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.findAllDiaryPagesByDiary(DIARY_ID, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void findAllDiaryPagesByDiary_anyDiary_checksOwnershipThroughCharacter() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary());
        givenPageList(List.of());

        service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        verify(diaries.where()).idEq(DIARY_ID);
        verify(diaries.where()).eq("character.owner.keycloakId", SUBJECT);
    }

    @Test
    void findAllDiaryPagesByDiary_firstSpread_readsPagesOneAndTwo() {
        givenOwnedDiary(diary());
        QueryStub<DiaryPage> pages = givenPageList(List.of());

        service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        verify(pages.where()).setFirstRow(0);
        verify(pages.where()).setMaxRows(2);
    }

    @Test
    void findAllDiaryPagesByDiary_secondSpread_readsPagesThreeAndFour() {
        givenOwnedDiary(diary());
        QueryStub<DiaryPage> pages = givenPageList(List.of());

        service.findAllDiaryPagesByDiary(DIARY_ID, 2);

        verify(pages.where()).setFirstRow(2);
        verify(pages.where()).setMaxRows(2);
    }

    @Test
    void findAllDiaryPagesByDiary_anySpread_ordersByPageNumber() {
        givenOwnedDiary(diary());
        QueryStub<DiaryPage> pages = givenPageList(List.of());

        service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        verify(pages.where()).orderBy("pageNumber");
    }

    @Test
    void findAllDiaryPagesByDiary_pagesFound_returnsPagesWithVersion() {
        givenOwnedDiary(diary());
        DiaryPage first = page(1, 3L);
        DiaryPage second = page(2, 7L);
        givenPageList(List.of(first, second));

        PagedResultDTO<BaseDiaryPageDTO> result = service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        assertThat(result.getList()).extracting(BaseDiaryPageDTO::pageNumber, BaseDiaryPageDTO::version)
                .containsExactly(tuple((short) 1, 3L), tuple((short) 2, 7L));
    }

    @Test
    void createDiaryPage_diaryWithPages_appendsAfterLastPage() {
        givenOwnedDiary(diary());
        givenPageCount(4);
        ArgumentCaptor<DiaryPage> saved = ArgumentCaptor.forClass(DiaryPage.class);

        service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        verify(database).save(saved.capture());
        assertThat(saved.getValue().getPageNumber()).isEqualTo((short) 5);
    }

    @Test
    void createDiaryPage_emptyDiary_createsPageOne() {
        givenOwnedDiary(diary());
        givenPageCount(0);

        BaseDiaryPageDTO result = service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        assertThat(result.pageNumber()).isEqualTo((short) 1);
    }

    @Test
    void createDiaryPage_validRequest_storesContentAndDiary() {
        Diary diary = diary();
        givenOwnedDiary(diary);
        givenPageCount(0);
        ArgumentCaptor<DiaryPage> saved = ArgumentCaptor.forClass(DiaryPage.class);

        service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        verify(database).save(saved.capture());
        assertThat(saved.getValue()).extracting(DiaryPage::getDiary, DiaryPage::getContent).containsExactly(diary, CONTENT);
    }

    @Test
    void createDiaryPage_diaryWithNinetyNinePages_createsPageOneHundred() {
        givenOwnedDiary(diary());
        givenPageCount(99);

        BaseDiaryPageDTO result = service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        assertThat(result.pageNumber()).isEqualTo((short) 100);
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
    void createDiaryPage_diaryFull_doesNotSave() {
        givenOwnedDiary(diary());
        givenPageCount(100);

        catchThrowable(() -> service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT)));

        verify(database, never()).save(any());
    }

    @Test
    void createDiaryPage_validRequest_validatesContent() {
        givenOwnedDiary(diary());
        givenPageCount(0);

        service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        verify(contentValidator).validate(CONTENT);
    }

    @Test
    void createDiaryPage_invalidContent_doesNotSave() {
        givenOwnedDiary(diary());
        doThrow(new ServiceException("Invalid page content at content[0]: unknown node")).when(contentValidator).validate(CONTENT);

        catchThrowable(() -> service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT)));

        verify(database, never()).save(any());
    }

    @Test
    void createDiaryPage_contentOverMaxSize_throwsServiceException() {
        config.setMaxPageContentSize(DataSize.ofBytes(10));
        givenOwnedDiary(diary());
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Page content too large");
    }

    @Test
    void createDiaryPage_contentOverMaxSize_skipsStructuralValidation() {
        config.setMaxPageContentSize(DataSize.ofBytes(10));
        givenOwnedDiary(diary());

        catchThrowable(() -> service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT)));

        verifyNoInteractions(contentValidator);
    }

    @Test
    void createDiaryPage_concurrentInsert_throwsServiceException() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(new DuplicateKeyException("duplicate", null)).when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Another page was added at the same time, try again");
    }

    @Test
    void createDiaryPage_uniqueViolationAtCommit_throwsServiceException() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(new RollbackException("commit failed", new SQLException("duplicate key", "23505")))
                .when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Another page was added at the same time, try again");
    }

    @Test
    void createDiaryPage_pageLimitCheckViolatedAtCommit_throwsServiceException() {
        givenOwnedDiary(diary());
        givenPageCount(99);
        doThrow(new RollbackException("commit failed", new SQLException("check violation", "23514")))
                .when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void createDiaryPage_deadlockAtCommit_throwsServiceException() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        doThrow(new RollbackException("commit failed", new SQLException("deadlock detected", "40P01")))
                .when(database).save(any(DiaryPage.class));
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
    void createDiaryPage_otherDatabaseError_rethrowsIt() {
        givenOwnedDiary(diary());
        givenPageCount(3);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).save(any(DiaryPage.class));
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request)).isSameAs(failure);
    }

    @Test
    void createDiaryPage_notOwnedDiary_throwsNotFound() {
        givenOwnedDiary(null);
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryPage_sameVersion_replacesContent() {
        DiaryPage page = page(1, 4L);
        givenOwnedPage(page);
        Map<String, Object> newContent = Map.of("type", "doc", "content", List.of(Map.of("type", "horizontalRule")));

        service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(newContent, 4L));

        verify(database).save(page);
        assertThat(page.getContent()).isEqualTo(newContent);
    }

    @Test
    void updateDiaryPage_sameVersion_keepsPageNumber() {
        givenOwnedPage(page(3, 4L));

        BaseDiaryPageDTO result = service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));

        assertThat(result.pageNumber()).isEqualTo((short) 3);
    }

    @Test
    void updateDiaryPage_staleVersion_throwsConflict() {
        givenOwnedPage(page(1, 5L));
        UpdateDiaryPageDTO request = new UpdateDiaryPageDTO(CONTENT, 4L);

        assertThatThrownBy(() -> service.updateDiaryPage(PAGE_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateDiaryPage_staleVersion_doesNotSave() {
        givenOwnedPage(page(1, 5L));

        catchThrowable(() -> service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L)));

        verify(database, never()).save(any());
    }

    @Test
    void updateDiaryPage_invalidContent_doesNotSave() {
        givenOwnedPage(page(1, 4L));
        doThrow(new ServiceException("Invalid page content")).when(contentValidator).validate(CONTENT);

        catchThrowable(() -> service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L)));

        verify(database, never()).save(any());
    }

    @Test
    void updateDiaryPage_contentOverMaxSize_throwsServiceException() {
        config.setMaxPageContentSize(DataSize.ofBytes(10));
        givenOwnedPage(page(1, 4L));
        UpdateDiaryPageDTO request = new UpdateDiaryPageDTO(CONTENT, 4L);

        assertThatThrownBy(() -> service.updateDiaryPage(PAGE_ID, request))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Page content too large");
    }

    @Test
    void updateDiaryPage_anyId_checksOwnershipThroughDiaryAndCharacter() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(1, 4L));

        service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));

        verify(pages.where()).idEq(PAGE_ID);
        verify(pages.where()).eq("diary.character.owner.keycloakId", SUBJECT);
    }

    @Test
    void updateDiaryPage_notOwnedOrMissing_throwsNotFound() {
        givenOwnedPage(null);
        UpdateDiaryPageDTO request = new UpdateDiaryPageDTO(CONTENT, 4L);

        assertThatThrownBy(() -> service.updateDiaryPage(PAGE_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteDiaryPage_followingPages_shiftsThemDownByOne() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(2, 1L));
        DiaryPage third = page(3, 1L);
        DiaryPage fourth = page(4, 1L);
        when(pages.where().findList()).thenReturn(List.of(third, fourth));
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        assertThat(List.of(third, fourth)).extracting(DiaryPage::getPageNumber).containsExactly((short) 2, (short) 3);
    }

    @Test
    void deleteDiaryPage_followingPages_savesRenumberedPagesBeforeCommit() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(2, 1L));
        List<DiaryPage> following = List.of(page(3, 1L));
        when(pages.where().findList()).thenReturn(following);
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        InOrder order = inOrder(database, transaction);
        order.verify(database).saveAll(following);
        order.verify(transaction).commit();
    }

    @Test
    void deleteDiaryPage_ownedPage_deletesPageBeforeRenumbering() {
        DiaryPage page = page(2, 1L);
        givenOwnedPage(page);
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        InOrder order = inOrder(database);
        order.verify(database).delete(page);
        order.verify(database).saveAll(List.of());
    }

    @Test
    void deleteDiaryPage_ownedPage_deletesTagsOfPageBeforePage() {
        DiaryPage page = page(2, 1L);
        givenOwnedPage(page);
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        InOrder order = inOrder(tagService, database);
        order.verify(tagService).deleteDiaryPageTagsByPage(page.getId());
        order.verify(database).delete(page);
    }

    @Test
    void deleteDiaryPage_ownedPage_deletesTagsAfterLockingDiary() {
        givenOwnedPage(page(2, 1L));
        QueryStub<Diary> diaries = givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        InOrder order = inOrder(diaries.query(), tagService);
        order.verify(diaries.query()).forUpdate();
        order.verify(tagService).deleteDiaryPageTagsByPage(any());
    }

    @Test
    void deleteDiaryPage_notOwnedOrMissing_deletesNoTags() {
        givenOwnedPage(null);

        catchThrowable(() -> service.deleteDiaryPage(PAGE_ID));

        verifyNoInteractions(tagService);
    }

    @Test
    void deleteDiaryPage_ownedPage_renumbersOnlyFollowingPagesOfSameDiary() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(2, 1L));
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        verify(pages.where()).eq("diary.id", DIARY_ID);
        verify(pages.where()).gt("pageNumber", (short) 2);
    }

    @Test
    void deleteDiaryPage_saveFails_doesNotCommit() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(2, 1L));
        List<DiaryPage> following = List.of(page(3, 1L));
        when(pages.where().findList()).thenReturn(following);
        givenLockedDiary();
        when(database.saveAll(following)).thenThrow(new IllegalStateException("db down"));

        catchThrowable(() -> service.deleteDiaryPage(PAGE_ID));

        verify(transaction, never()).commit();
    }

    @Test
    void deleteDiaryPage_notOwnedOrMissing_throwsNotFound() {
        givenOwnedPage(null);

        assertThatThrownBy(() -> service.deleteDiaryPage(PAGE_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private int sizeOfContent() {
        return JsonMapper.builder().build().writeValueAsBytes(CONTENT).length;
    }

    @Test
    void createDiaryPage_contentExactlyAtMaxSize_savesPage() {
        config.setMaxPageContentSize(DataSize.ofBytes(sizeOfContent()));
        givenOwnedDiary(diary());
        givenPageCount(0);

        service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        verify(database).save(any(DiaryPage.class));
    }

    @Test
    void updateDiaryPage_contentExactlyAtMaxSize_savesPage() {
        config.setMaxPageContentSize(DataSize.ofBytes(sizeOfContent()));
        DiaryPage page = page(1, 4L);
        givenOwnedPage(page);

        service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));

        verify(database).save(page);
    }

    @Test
    void deleteDiaryPage_lastPage_commitsTransaction() {
        givenOwnedPage(page(3, 1L));
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        verify(transaction).commit();
    }

    @Test
    void deleteDiaryPage_lastPage_renumbersNothing() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(3, 1L));
        when(pages.where().findList()).thenReturn(List.of());
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        verify(database).saveAll(List.of());
    }

    @Test
    void findAllDiaryPagesByDiary_hugePageNumber_usesNonNegativeOffset() {
        givenOwnedDiary(diary());
        QueryStub<DiaryPage> pages = givenPageList(List.of());
        ArgumentCaptor<Integer> firstRow = ArgumentCaptor.forClass(Integer.class);

        service.findAllDiaryPagesByDiary(DIARY_ID, Integer.MAX_VALUE);

        verify(pages.where()).setFirstRow(firstRow.capture());
        assertThat(firstRow.getValue()).isNotNegative();
    }

    @Test
    void createDiaryPage_anyRequest_locksDiaryInsideTransactionBeforeCountingPages() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary());
        QueryStub<DiaryPage> pages = givenPageCount(4);

        service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        InOrder order = inOrder(database, diaries.query(), pages.where());
        order.verify(database).beginTransaction();
        order.verify(diaries.query()).forUpdate();
        order.verify(pages.where()).findCount();
    }

    @Test
    void createDiaryPage_validRequest_commitsAfterSave() {
        givenOwnedDiary(diary());
        givenPageCount(4);

        service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        InOrder order = inOrder(database, transaction);
        order.verify(database).save(any(DiaryPage.class));
        order.verify(transaction).commit();
    }

    @Test
    void createDiaryPage_diaryDeletedBeforeLock_throwsDiaryNotFound() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary());
        when(diaries.where().findOne()).thenReturn(diary(), (Diary) null);
        when(database.beginTransaction()).thenReturn(transaction);
        InsertDiaryPageDTO request = new InsertDiaryPageDTO(CONTENT);

        assertThatThrownBy(() -> service.createDiaryPage(DIARY_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Diary not found"));
    }

    @Test
    void createDiaryPage_diaryDeletedBeforeLock_doesNotSave() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary());
        when(diaries.where().findOne()).thenReturn(diary(), (Diary) null);
        when(database.beginTransaction()).thenReturn(transaction);

        catchThrowable(() -> service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT)));

        verify(database, never()).save(any());
    }

    @Test
    void createDiaryPage_diaryFull_doesNotCommit() {
        givenOwnedDiary(diary());
        givenPageCount(100);

        catchThrowable(() -> service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT)));

        verify(transaction, never()).commit();
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
    void deleteDiaryPage_ownedPage_locksDiaryBeforeDeletingPage() {
        DiaryPage page = page(2, 1L);
        givenOwnedPage(page);
        QueryStub<Diary> diaries = givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        InOrder order = inOrder(database, diaries.query());
        order.verify(database).beginTransaction();
        order.verify(diaries.query()).forUpdate();
        order.verify(database).delete(page);
    }

    @Test
    void deleteDiaryPage_ownedPage_locksDiaryOfPage() {
        givenOwnedPage(page(2, 1L));
        QueryStub<Diary> diaries = givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        verify(diaries.where()).idEq(DIARY_ID);
    }

    @Test
    void deleteDiaryPage_diaryDeletedBeforeLock_throwsPageNotFound() {
        givenOwnedPage(page(2, 1L));
        QueryStub<Diary> diaries = givenLockedDiary();
        when(diaries.where().findOne()).thenReturn(null);

        assertThatThrownBy(() -> service.deleteDiaryPage(PAGE_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Page not found"));
    }

    @Test
    void deleteDiaryPage_pageDeletedWhileWaitingForLock_throwsNotFound() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(2, 1L));
        when(pages.where().findOne()).thenReturn(page(2, 1L), (DiaryPage) null);
        givenLockedDiary();

        assertThatThrownBy(() -> service.deleteDiaryPage(PAGE_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteDiaryPage_pageRenumberedWhileWaitingForLock_renumbersFromCurrentNumber() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page(3, 1L));
        when(pages.where().findOne()).thenReturn(page(3, 1L), page(2, 2L));
        givenLockedDiary();

        service.deleteDiaryPage(PAGE_ID);

        verify(pages.where()).gt("pageNumber", (short) 2);
    }

    @Test
    void findAllDiaryPagesByDiary_pagesFound_asksTagsOfThosePages() {
        givenOwnedDiary(diary());
        DiaryPage first = page(1, 3L);
        DiaryPage second = page(2, 7L);
        givenPageList(List.of(first, second));

        service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        verify(tagService).findTagNamesByPages(List.of(first.getId(), second.getId()));
    }

    @Test
    void findAllDiaryPagesByDiary_pagesWithTags_returnsTagsOfEachPage() {
        givenOwnedDiary(diary());
        DiaryPage first = page(1, 3L);
        DiaryPage second = page(2, 7L);
        givenPageList(List.of(first, second));
        when(tagService.findTagNamesByPages(any())).thenReturn(Map.of(first.getId(), List.of("clues", "combat")));

        PagedResultDTO<BaseDiaryPageDTO> result = service.findAllDiaryPagesByDiary(DIARY_ID, 1);

        assertThat(result.getList()).extracting(BaseDiaryPageDTO::tags)
                .containsExactly(List.of("clues", "combat"), List.of());
    }

    @Test
    void createDiaryPage_validRequest_returnsPageWithoutTags() {
        givenOwnedDiary(diary());
        givenPageCount(0);

        BaseDiaryPageDTO result = service.createDiaryPage(DIARY_ID, new InsertDiaryPageDTO(CONTENT));

        assertThat(result.tags()).isEmpty();
    }

    @Test
    void updateDiaryPage_pageWithTags_returnsItsTags() {
        DiaryPage page = page(1, 4L);
        givenOwnedPage(page);
        when(tagService.findTagNamesByPages(List.of(page.getId()))).thenReturn(Map.of(page.getId(), List.of("clues")));

        BaseDiaryPageDTO result = service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));

        assertThat(result.tags()).containsExactly("clues");
    }

    @Test
    void updateDiaryPage_pageWithoutTags_returnsEmptyTags() {
        givenOwnedPage(page(1, 4L));

        BaseDiaryPageDTO result = service.updateDiaryPage(PAGE_ID, new UpdateDiaryPageDTO(CONTENT, 4L));

        assertThat(result.tags()).isEmpty();
    }
}

package it.gdr.diary.unit.services;

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
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page_tag.BaseDiaryTagDTO;
import it.gdr.diary.dto.diary_page_tag.DetailDiaryPageTagsDTO;
import it.gdr.diary.dto.diary_page_tag.UpdateDiaryPageTagsDTO;
import it.gdr.diary.dto.search.DiaryTagSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.DiaryPageTag;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.DiaryPageTagService;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DiaryPageTagServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID DIARY_ID = UUID.fromString("5c1e8a42-93d7-4f0b-8a16-0d6b7e2f9a31");
    private static final UUID PAGE_ID = UUID.fromString("7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private Transaction transaction;
    @Captor
    private ArgumentCaptor<Collection<DiaryPageTag>> saved;
    @Captor
    private ArgumentCaptor<Collection<DiaryPageTag>> deleted;

    private DiaryPageTagService service;

    @BeforeEach
    void setUp() {
        service = new DiaryPageTagService(database, jwtInspector);
    }

    private DiaryPage page() {
        DiaryPage page = new DiaryPage();
        page.setId(PAGE_ID);
        return page;
    }

    private DiaryPageTag tag(String name) {
        DiaryPageTag tag = new DiaryPageTag();
        tag.setId(UUID.randomUUID());
        tag.setName(name);
        return tag;
    }

    private QueryStub<DiaryPage> givenOwnedPage(DiaryPage page) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(pages.where().findOne()).thenReturn(page);
        return pages;
    }

    private QueryStub<DiaryPageTag> givenStoredTags(DiaryPageTag... stored) {
        when(database.beginTransaction()).thenReturn(transaction);
        QueryStub<DiaryPageTag> tags = stubFind(database, DiaryPageTag.class);
        when(tags.where().findList()).thenReturn(List.of(stored));
        return tags;
    }

    private UpdateDiaryPageTagsDTO request(String... tags) {
        return new UpdateDiaryPageTagsDTO(List.of(tags));
    }

    private List<String> savedNames() {
        verify(database).saveAll(saved.capture());
        return saved.getValue().stream().map(DiaryPageTag::getName).toList();
    }

    private Collection<DiaryPageTag> deletedTags() {
        verify(database).deleteAll(deleted.capture());
        return deleted.getValue();
    }

    @Test
    void updateDiaryPageTags_pageNotOwnedOrMissing_throwsNotFound() {
        givenOwnedPage(null);

        assertThatThrownBy(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Page not found"));
    }

    @Test
    void updateDiaryPageTags_pageNotOwnedOrMissing_doesNotOpenTransaction() {
        givenOwnedPage(null);

        catchThrowable(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")));

        verify(database, never()).beginTransaction();
    }

    @Test
    void updateDiaryPageTags_pageNotOwnedOrMissing_savesNothing() {
        givenOwnedPage(null);

        catchThrowable(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")));

        verify(database, never()).saveAll(any(Collection.class));
    }

    @Test
    void updateDiaryPageTags_ownedPage_checksOwnerOfPage() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page());
        givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("combat"));

        verify(pages.where()).eq("diary.character.owner.keycloakId", SUBJECT);
    }

    @Test
    void updateDiaryPageTags_noStoredTags_savesOneTagPerName() {
        givenOwnedPage(page());
        givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("Combat", "clues"));

        assertThat(savedNames()).containsExactly("Combat", "clues");
    }

    @Test
    void updateDiaryPageTags_noStoredTags_linksTagsToPage() {
        DiaryPage page = page();
        givenOwnedPage(page);
        givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("combat", "clues"));

        verify(database).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(DiaryPageTag::getPage).containsOnly(page);
    }

    @Test
    void updateDiaryPageTags_namesWithSurroundingSpaces_savesTrimmedNames() {
        givenOwnedPage(page());
        givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("  combat  "));

        assertThat(savedNames()).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTags_namesDifferingByCase_keepsFirstOnly() {
        givenOwnedPage(page());
        givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("Combat", "combat", "COMBAT"));

        assertThat(savedNames()).containsExactly("Combat");
    }

    @Test
    void updateDiaryPageTags_namesEqualAfterTrim_keepsFirstOnly() {
        givenOwnedPage(page());
        givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("combat", " combat "));

        assertThat(savedNames()).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTags_storedTagStillRequested_doesNotDeleteIt() {
        givenOwnedPage(page());
        givenStoredTags(tag("combat"));

        service.updateDiaryPageTags(PAGE_ID, request("combat", "clues"));

        assertThat(deletedTags()).isEmpty();
    }

    @Test
    void updateDiaryPageTags_storedTagStillRequested_savesStoredRowAgain() {
        DiaryPageTag stored = tag("combat");
        givenOwnedPage(page());
        givenStoredTags(stored);

        service.updateDiaryPageTags(PAGE_ID, request("combat", "clues"));

        verify(database).saveAll(saved.capture());
        assertThat(saved.getValue()).contains(stored);
    }

    @Test
    void updateDiaryPageTags_storedTagStillRequested_savesStoredRowAndNewTag() {
        givenOwnedPage(page());
        givenStoredTags(tag("combat"));

        service.updateDiaryPageTags(PAGE_ID, request("combat", "clues"));

        assertThat(savedNames()).containsExactlyInAnyOrder("combat", "clues");
    }

    @Test
    void updateDiaryPageTags_storedTagWithOtherCase_renamesStoredRow() {
        DiaryPageTag stored = tag("combat");
        givenOwnedPage(page());
        givenStoredTags(stored);

        service.updateDiaryPageTags(PAGE_ID, request("Combat"));

        assertThat(stored.getName()).isEqualTo("Combat");
    }

    @Test
    void updateDiaryPageTags_storedTagWithOtherCase_doesNotDeleteIt() {
        givenOwnedPage(page());
        givenStoredTags(tag("combat"));

        service.updateDiaryPageTags(PAGE_ID, request("Combat"));

        assertThat(deletedTags()).isEmpty();
    }

    @Test
    void updateDiaryPageTags_storedTagNotRequested_deletesOnlyThatTag() {
        DiaryPageTag old = tag("old");
        givenOwnedPage(page());
        givenStoredTags(tag("combat"), old);

        service.updateDiaryPageTags(PAGE_ID, request("combat"));

        assertThat(deletedTags()).containsExactly(old);
    }

    @Test
    void updateDiaryPageTags_emptyList_deletesAllStoredTags() {
        DiaryPageTag first = tag("combat");
        DiaryPageTag second = tag("clues");
        givenOwnedPage(page());
        givenStoredTags(first, second);

        service.updateDiaryPageTags(PAGE_ID, request());

        assertThat(deletedTags()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void updateDiaryPageTags_emptyList_savesNoTag() {
        givenOwnedPage(page());
        givenStoredTags(tag("combat"));

        service.updateDiaryPageTags(PAGE_ID, request());

        assertThat(savedNames()).isEmpty();
    }

    @Test
    void updateDiaryPageTags_unsortedNames_returnsNamesSortedIgnoringCase() {
        givenOwnedPage(page());
        givenStoredTags();

        DetailDiaryPageTagsDTO result = service.updateDiaryPageTags(PAGE_ID, request("clues", "Alpha", "beta"));

        assertThat(result.tags()).containsExactly("Alpha", "beta", "clues");
    }

    @Test
    void updateDiaryPageTags_validRequest_deletesThenSavesThenCommits() {
        givenOwnedPage(page());
        givenStoredTags(tag("old"));

        service.updateDiaryPageTags(PAGE_ID, request("combat"));

        InOrder order = inOrder(database, transaction);
        order.verify(database).deleteAll(any(Collection.class));
        order.verify(database).saveAll(any(Collection.class));
        order.verify(transaction).commit();
    }

    @Test
    void updateDiaryPageTags_validRequest_locksPageBeforeReadingTags() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page());
        QueryStub<DiaryPageTag> tags = givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("combat"));

        InOrder order = inOrder(database, pages.query(), tags.where());
        order.verify(database).beginTransaction();
        order.verify(pages.query()).forUpdate();
        order.verify(tags.where()).findList();
    }

    @Test
    void updateDiaryPageTags_validRequest_readsOnlyTagsOfPage() {
        givenOwnedPage(page());
        QueryStub<DiaryPageTag> tags = givenStoredTags();

        service.updateDiaryPageTags(PAGE_ID, request("combat"));

        verify(tags.where()).eq("page.id", PAGE_ID);
    }

    @Test
    void updateDiaryPageTags_pageDeletedBeforeLock_throwsPageNotFound() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page());
        when(pages.where().findOne()).thenReturn(page(), (DiaryPage) null);
        when(database.beginTransaction()).thenReturn(transaction);

        assertThatThrownBy(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Page not found"));
    }

    @Test
    void updateDiaryPageTags_pageDeletedBeforeLock_savesNothing() {
        QueryStub<DiaryPage> pages = givenOwnedPage(page());
        when(pages.where().findOne()).thenReturn(page(), (DiaryPage) null);
        when(database.beginTransaction()).thenReturn(transaction);

        catchThrowable(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")));

        verify(database, never()).saveAll(any(Collection.class));
    }

    @Test
    void updateDiaryPageTags_namesEqualForDatabase_throwsServiceException() {
        givenOwnedPage(page());
        givenStoredTags();
        doThrow(new PersistenceException("duplicate", new SQLException("duplicate", "23505"))).when(database).saveAll(any(Collection.class));

        assertThatThrownBy(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("The tags must differ from each other, ignoring case");
    }

    @Test
    void updateDiaryPageTags_namesEqualForDatabase_doesNotCommit() {
        givenOwnedPage(page());
        givenStoredTags();
        doThrow(new PersistenceException("duplicate", new SQLException("duplicate", "23505"))).when(database).saveAll(any(Collection.class));

        catchThrowable(() -> service.updateDiaryPageTags(PAGE_ID, request("combat")));

        verify(transaction, never()).commit();
    }

    @Test
    void updateDiaryPageTags_otherDatabaseError_rethrowsIt() {
        givenOwnedPage(page());
        givenStoredTags();
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).saveAll(any(Collection.class));

        assertThatThrownBy(() -> service.updateDiaryPageTags(PAGE_ID, request("combat"))).isSameAs(failure);
    }

    @Test
    void deleteDiaryPageTagsByPage_anyPage_deletesOnlyTagsOfPage() {
        QueryStub<DiaryPageTag> tags = stubFind(database, DiaryPageTag.class);

        service.deleteDiaryPageTagsByPage(PAGE_ID);

        verify(tags.where()).eq("page.id", PAGE_ID);
    }

    @Test
    void deleteDiaryPageTagsByPage_anyPage_deletesTheSelectedTags() {
        QueryStub<DiaryPageTag> tags = stubFind(database, DiaryPageTag.class);

        service.deleteDiaryPageTagsByPage(PAGE_ID);

        verify(tags.where()).delete();
    }

    @Test
    void deleteDiaryPageTagsByDiaries_severalDiaries_deletesOnlyTagsOfThoseDiaries() {
        QueryStub<DiaryPageTag> tags = stubFind(database, DiaryPageTag.class);
        List<UUID> diaryIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        service.deleteDiaryPageTagsByDiaries(diaryIds);

        verify(tags.where()).in("page.diary.id", diaryIds);
    }

    @Test
    void deleteDiaryPageTagsByDiaries_severalDiaries_deletesTheSelectedTags() {
        QueryStub<DiaryPageTag> tags = stubFind(database, DiaryPageTag.class);

        service.deleteDiaryPageTagsByDiaries(List.of(UUID.randomUUID()));

        verify(tags.where()).delete();
    }

    private DiaryPageTag tagOnPage(String name, int pageNumber) {
        DiaryPage tagPage = new DiaryPage();
        tagPage.setId(UUID.randomUUID());
        tagPage.setPageNumber((short) pageNumber);
        DiaryPageTag tag = tag(name);
        tag.setPage(tagPage);
        return tag;
    }

    private QueryStub<DiaryPageTag> givenDiaryWithTags(DiaryPageTag... tags) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(new Diary());
        QueryStub<DiaryPageTag> found = stubFind(database, DiaryPageTag.class);
        when(found.where().findList()).thenReturn(List.of(tags));
        return found;
    }

    private DiaryTagSearchRequest searchRequest(String name, int page, int size) {
        DiaryTagSearchRequest request = new DiaryTagSearchRequest();
        request.setName(name);
        request.setPage(page);
        request.setSize(size);
        return request;
    }

    @Test
    void findAllDiaryTagsByDiary_diaryNotOwnedOrMissing_throwsNotFound() {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(null);
        DiaryTagSearchRequest request = searchRequest(null, 1, 20);

        assertThatThrownBy(() -> service.findAllDiaryTagsByDiary(DIARY_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Diary not found"));
    }

    @Test
    void findAllDiaryTagsByDiary_diaryNotOwnedOrMissing_doesNotReadTags() {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(null);

        catchThrowable(() -> service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest(null, 1, 20)));

        verify(database, never()).find(DiaryPageTag.class);
    }

    @Test
    void findAllDiaryTagsByDiary_ownedDiary_filtersByDiaryAndOwner() {
        QueryStub<DiaryPageTag> found = givenDiaryWithTags();

        service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest(null, 1, 20));

        verify(found.where()).eq("page.diary.id", DIARY_ID);
        verify(found.where()).eq("page.diary.character.owner.keycloakId", SUBJECT);
    }

    @Test
    void findAllDiaryTagsByDiary_nameGiven_filtersByTrimmedName() {
        QueryStub<DiaryPageTag> found = givenDiaryWithTags();

        service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest("  comb ", 1, 20));

        verify(found.where()).icontains("name", "comb");
    }

    @Test
    void findAllDiaryTagsByDiary_blankName_doesNotFilterByName() {
        QueryStub<DiaryPageTag> found = givenDiaryWithTags();

        service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest("   ", 1, 20));

        verify(found.where(), never()).icontains(any(), any());
    }

    @Test
    void findAllDiaryTagsByDiary_tagsDifferingByCase_groupsThemKeepingTheFirstName() {
        givenDiaryWithTags(tagOnPage("Combat", 1), tagOnPage("clues", 2), tagOnPage("combat", 3));

        PagedResultDTO<BaseDiaryTagDTO> result = service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest(null, 1, 20));

        assertThat(result.getList()).extracting(BaseDiaryTagDTO::name, BaseDiaryTagDTO::pageNumbers)
                .containsExactly(tuple("clues", List.of((short) 2)), tuple("Combat", List.of((short) 1, (short) 3)));
    }

    @Test
    void findAllDiaryTagsByDiary_noTags_returnsEmptyList() {
        givenDiaryWithTags();

        PagedResultDTO<BaseDiaryTagDTO> result = service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest(null, 1, 20));

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotalRows()).isZero();
    }

    @Test
    void findAllDiaryTagsByDiary_secondPage_returnsRemainingTagsWithTotals() {
        givenDiaryWithTags(tagOnPage("a", 1), tagOnPage("b", 1), tagOnPage("c", 1));

        PagedResultDTO<BaseDiaryTagDTO> result = service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest(null, 2, 2));

        assertThat(result.getList()).extracting(BaseDiaryTagDTO::name).containsExactly("c");
        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(2);
    }

    @Test
    void findAllDiaryTagsByDiary_pageBeyondTheEnd_returnsEmptyList() {
        givenDiaryWithTags(tagOnPage("a", 1));

        PagedResultDTO<BaseDiaryTagDTO> result = service.findAllDiaryTagsByDiary(DIARY_ID, searchRequest(null, 5, 20));

        assertThat(result.getList()).isEmpty();
        assertThat(result.getTotalRows()).isEqualTo(1);
    }

    @Test
    void findTagNamesByPages_noPages_doesNotQuery() {
        Map<UUID, List<String>> result = service.findTagNamesByPages(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(database);
    }

    @Test
    void findTagNamesByPages_tagsOfSeveralPages_groupsThemByPageSortedIgnoringCase() {
        DiaryPageTag combat = tagOnPage("combat", 1);
        DiaryPageTag clues = tagOnPage("Clues", 1);
        DiaryPageTag elsewhere = tagOnPage("elsewhere", 2);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPageTag> found = stubFind(database, DiaryPageTag.class);
        when(found.where().findList()).thenReturn(List.of(combat, clues, elsewhere));

        Map<UUID, List<String>> result = service.findTagNamesByPages(List.of(combat.getPage().getId(), elsewhere.getPage().getId()));

        assertThat(result).containsOnly(
                Map.entry(combat.getPage().getId(), List.of("Clues", "combat")),
                Map.entry(elsewhere.getPage().getId(), List.of("elsewhere")));
    }

    @Test
    void findTagNamesByPages_anyPages_filtersByPagesAndOwner() {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPageTag> found = stubFind(database, DiaryPageTag.class);
        when(found.where().findList()).thenReturn(List.of());

        service.findTagNamesByPages(List.of(PAGE_ID));

        verify(found.where()).in("page.id", List.of(PAGE_ID));
        verify(found.where()).eq("page.diary.character.owner.keycloakId", SUBJECT);
    }
}

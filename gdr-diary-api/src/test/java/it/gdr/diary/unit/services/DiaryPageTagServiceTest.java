package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page_tag.BaseDiaryTagDTO;
import it.gdr.diary.dto.diary_page_tag.DetailDiaryPageTagDTO;
import it.gdr.diary.dto.diary_page_tag.UpdateDiaryPageTagDTO;
import it.gdr.diary.dto.search.DiaryTagSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.DiaryPageTag;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.DiaryPageTagService;
import it.gdr.diary.services.OwnedResources;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    private DiaryPageTagService service;
    private Diary diary;
    private DiaryPage page;
    private QueryStub<DiaryPage> pageQuery;

    @BeforeEach
    void setUp() {
        service = new DiaryPageTagService(database, jwtInspector, new OwnedResources(database, jwtInspector));
        diary = new Diary();
        diary.setId(DIARY_ID);
        page = new DiaryPage();
        page.setId(PAGE_ID);
        page.setDiary(diary);
    }

    private DiaryPageTag tag(String name) {
        DiaryPageTag tag = new DiaryPageTag();
        tag.setId(UUID.randomUUID());
        tag.setName(name);
        return tag;
    }

    private UpdateDiaryPageTagDTO request(String tag) {
        return new UpdateDiaryPageTagDTO(tag);
    }

    private QueryStub<DiaryPage> givenOwnedPage(DiaryPage found) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(pages.where().findOne()).thenReturn(found);
        return pages;
    }

    private QueryStub<Diary> givenLockedDiary(Diary found) {
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(found);
        return diaries;
    }

    /** Page and diary of the user, with the tag the page has now (null if none) and the tags of the other pages of the diary. */
    private QueryStub<DiaryPageTag> givenTags(DiaryPageTag current, DiaryPageTag... others) {
        pageQuery = givenOwnedPage(page);
        givenLockedDiary(diary);
        when(database.beginTransaction()).thenReturn(transaction);
        QueryStub<DiaryPageTag> tags = stubFind(database, DiaryPageTag.class);
        lenient().when(tags.where().findOne()).thenReturn(current);
        lenient().when(tags.where().findList()).thenReturn(List.of(others));
        return tags;
    }

    private List<DiaryPageTag> otherTags(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> tag("tag" + number)).toList();
    }

    private DiaryPageTag tagOrNull(String name) {
        return name == null ? null : tag(name);
    }

    @Test
    void updateDiaryPageTag_diaryDeletedBeforeLock_throwsNotFoundAndSavesNothing() {
        givenOwnedPage(page);
        givenLockedDiary(null);
        when(database.beginTransaction()).thenReturn(transaction);

        assertThatThrownBy(() -> service.updateDiaryPageTag(PAGE_ID, request("combat")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(database, never()).save(any(DiaryPageTag.class));
        verify(transaction, never()).commit();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "own")
    void updateDiaryPageTag_diaryHasTenOtherTags_rejectsNewTagAndSavesNothing(String currentTag) {
        givenTags(tagOrNull(currentTag), otherTags(10).toArray(DiaryPageTag[]::new));

        assertThatThrownBy(() -> service.updateDiaryPageTag(PAGE_ID, request("brandnew")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("A diary can have at most 10 different tags");
        verify(database, never()).save(any(DiaryPageTag.class));
        verify(transaction, never()).commit();
    }

    @Test
    void updateDiaryPageTag_otherPagesShareTheSameTag_countItOnce() {
        givenTags(null, tag("Combat"), tag("combat"), tag("COMBAT"), tag("a"), tag("b"), tag("c"), tag("d"), tag("e"), tag("f"), tag("g"));

        DetailDiaryPageTagDTO result = service.updateDiaryPageTag(PAGE_ID, request("brandnew"));

        assertThat(result.tag()).isEqualTo("brandnew");
    }

    @Test
    void updateDiaryPageTag_uniqueViolation_throwsServiceExceptionAndDoesNotCommit() {
        givenTags(null);
        doThrow(new PersistenceException("duplicate", new SQLException("duplicate", "23505"))).when(database).save(any(DiaryPageTag.class));

        assertThatThrownBy(() -> service.updateDiaryPageTag(PAGE_ID, request("combat")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("The tag of the page was changed at the same time, try again");
        verify(transaction, never()).commit();
    }

    @Test
    void updateDiaryPageTag_otherDatabaseError_rethrowsIt() {
        givenTags(null);
        PersistenceException failure = new PersistenceException("boom", new SQLException("boom", "08006"));
        doThrow(failure).when(database).save(any(DiaryPageTag.class));

        assertThatThrownBy(() -> service.updateDiaryPageTag(PAGE_ID, request("combat"))).isSameAs(failure);
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
    void findTagByPages_noPages_returnsEmptyWithoutQuerying() {
        Map<UUID, String> result = service.findTagByPages(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(database);
    }

    @Test
    void findTagByPages_somePagesWithoutTag_returnsTagOfTaggedPagesOnly() {
        DiaryPageTag combat = tagOnPage("combat", 1);
        DiaryPageTag elsewhere = tagOnPage("Elsewhere", 2);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPageTag> found = stubFind(database, DiaryPageTag.class);
        when(found.where().findList()).thenReturn(List.of(combat, elsewhere));

        Map<UUID, String> result = service.findTagByPages(
                List.of(combat.getPage().getId(), elsewhere.getPage().getId(), UUID.randomUUID()));

        assertThat(result).containsOnly(
                Map.entry(combat.getPage().getId(), "combat"),
                Map.entry(elsewhere.getPage().getId(), "Elsewhere"));
    }

    @Test
    void findTagByPages_anyPages_filtersByPagesAndOwner() {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<DiaryPageTag> found = stubFind(database, DiaryPageTag.class);
        when(found.where().findList()).thenReturn(List.of());

        service.findTagByPages(List.of(PAGE_ID));

        verify(found.where()).in("page.id", List.of(PAGE_ID));
        verify(found.where()).eq("diary.character.owner.keycloakId", SUBJECT);
    }
}

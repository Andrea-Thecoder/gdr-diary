package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.DataIntegrityException;
import io.ebean.Database;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary.BaseDiaryDTO;
import it.gdr.diary.dto.diary.BookmarkDiaryDTO;
import it.gdr.diary.dto.diary.DetailDiaryDTO;
import it.gdr.diary.dto.diary.InsertDiaryDTO;
import it.gdr.diary.dto.diary.UpdateBookmarkDiaryDTO;
import it.gdr.diary.dto.diary.UpdateDiaryDTO;
import it.gdr.diary.dto.search.DiarySearchRequest;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.DiaryService;
import it.gdr.diary.services.DocumentService;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final UUID DIARY_ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");
    private static final UUID PAGE_ID = UUID.fromString("7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private DocumentService documentService;
    @Mock
    private Transaction transaction;

    private final MultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

    private DiaryService service;

    @BeforeEach
    void setUp() {
        service = new DiaryService(database, jwtInspector, documentService);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
    }

    private GameCharacter character() {
        GameCharacter character = new GameCharacter();
        character.setId(CHARACTER_ID);
        return character;
    }

    private Diary diary(String title) {
        Diary diary = new Diary();
        diary.setId(DIARY_ID);
        diary.setTitle(title);
        diary.setCharacter(character());
        return diary;
    }

    private DiaryPage page(int number) {
        DiaryPage page = new DiaryPage();
        page.setId(PAGE_ID);
        page.setPageNumber((short) number);
        return page;
    }

    private QueryStub<DiaryPage> givenPageOfDiary(DiaryPage page) {
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(pages.where().findOne()).thenReturn(page);
        return pages;
    }

    private Document document(String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        return document;
    }

    private QueryStub<GameCharacter> givenOwnedCharacter(GameCharacter character) {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        when(characters.where().findOne()).thenReturn(character);
        return characters;
    }

    private QueryStub<Diary> givenOwnedDiary(Diary diary) {
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        when(diaries.where().findOne()).thenReturn(diary);
        return diaries;
    }

    private QueryStub<Diary> givenDiaryList(List<Diary> found) {
        QueryStub<Diary> diaries = stubFind(database, Diary.class);
        PagedList<Diary> paged = pagedList(found, 0, 20, found.size());
        when(diaries.where().findPagedList()).thenReturn(paged);
        return diaries;
    }

    private static DataIntegrityException foreignKeyViolation() {
        return new DataIntegrityException("violates foreign key constraint",
                new SQLException("violates foreign key constraint", "23503"));
    }

    private InsertDiaryDTO insertRequest() {
        return new InsertDiaryDTO("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16);
    }

    @Test
    void findAllDiariesByCharacter_ownedCharacter_filtersByCharacter() {
        givenOwnedCharacter(character());
        QueryStub<Diary> diaries = givenDiaryList(List.of());

        service.findAllDiariesByCharacter(CHARACTER_ID, new DiarySearchRequest());

        verify(diaries.where()).eq("character.id", CHARACTER_ID);
    }

    @Test
    void findAllDiariesByCharacter_anyCharacter_checksOwnershipOfCharacter() {
        QueryStub<GameCharacter> characters = givenOwnedCharacter(character());
        givenDiaryList(List.of());

        service.findAllDiariesByCharacter(CHARACTER_ID, new DiarySearchRequest());

        verify(characters.where()).idEq(CHARACTER_ID);
        verify(characters.where()).eq("owner.keycloakId", SUBJECT);
    }

    @Test
    void findAllDiariesByCharacter_notOwnedCharacter_throwsNotFound() {
        givenOwnedCharacter(null);
        DiarySearchRequest request = new DiarySearchRequest();

        assertThatThrownBy(() -> service.findAllDiariesByCharacter(CHARACTER_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void findAllDiariesByCharacter_titleFilter_searchesTrimmedTitle() {
        givenOwnedCharacter(character());
        QueryStub<Diary> diaries = givenDiaryList(List.of());
        DiarySearchRequest request = new DiarySearchRequest();
        request.setTitle("  Chapter ");

        service.findAllDiariesByCharacter(CHARACTER_ID, request);

        verify(diaries.where()).icontains("title", "Chapter");
    }

    @Test
    void findAllDiariesByCharacter_blankTitle_doesNotFilterByTitle() {
        givenOwnedCharacter(character());
        QueryStub<Diary> diaries = givenDiaryList(List.of());
        DiarySearchRequest request = new DiarySearchRequest();
        request.setTitle("   ");

        service.findAllDiariesByCharacter(CHARACTER_ID, request);

        verify(diaries.where(), never()).icontains(eq("title"), anyString());
    }

    @Test
    void findAllDiariesByCharacter_noSort_ordersByTitle() {
        givenOwnedCharacter(character());
        QueryStub<Diary> diaries = givenDiaryList(List.of());

        service.findAllDiariesByCharacter(CHARACTER_ID, new DiarySearchRequest());

        verify(diaries.where()).orderBy("title asc, id asc");
    }

    @Test
    void findAllDiariesByCharacter_diaryWithCover_mapsCoverUrl() {
        givenOwnedCharacter(character());
        Diary diary = diary("Chapter one");
        Document cover = document("cover.png");
        diary.setCoverDocument(cover);
        givenDiaryList(List.of(diary));
        when(documentService.getDocumentUrl(cover)).thenReturn("https://storage.example/cover");

        PagedResultDTO<BaseDiaryDTO> result = service.findAllDiariesByCharacter(CHARACTER_ID, new DiarySearchRequest());

        assertThat(result.getList()).extracting(BaseDiaryDTO::coverUrl).containsExactly("https://storage.example/cover");
    }

    @Test
    void getDiaryById_ownedDiary_returnsDetail() {
        givenOwnedDiary(diary("Chapter one"));

        DetailDiaryDTO result = service.getDiaryById(DIARY_ID);

        assertThat(result).extracting(DetailDiaryDTO::id, DetailDiaryDTO::characterId, DetailDiaryDTO::title)
                .containsExactly(DIARY_ID, CHARACTER_ID, "Chapter one");
    }

    @Test
    void getDiaryById_anyId_filtersByIdAndOwnerOfCharacter() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary("Chapter one"));

        service.getDiaryById(DIARY_ID);

        verify(diaries.where()).idEq(DIARY_ID);
        verify(diaries.where()).eq("character.owner.keycloakId", SUBJECT);
    }

    @Test
    void getDiaryById_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.getDiaryById(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDiary_ownedCharacter_savesDiaryLinkedToCharacter() {
        GameCharacter character = character();
        givenOwnedCharacter(character);
        ArgumentCaptor<Diary> saved = ArgumentCaptor.forClass(Diary.class);

        service.createDiary(CHARACTER_ID, insertRequest());

        verify(database).save(saved.capture());
        assertThat(saved.getValue().getCharacter()).isSameAs(character);
    }

    @Test
    void createDiary_ownedCharacter_copiesRequestFields() {
        givenOwnedCharacter(character());

        DetailDiaryDTO result = service.createDiary(CHARACTER_ID, insertRequest());

        assertThat(result)
                .extracting(DetailDiaryDTO::title, DetailDiaryDTO::description, DetailDiaryDTO::coverColor,
                        DetailDiaryDTO::font, DetailDiaryDTO::fontSize)
                .containsExactly("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16);
    }

    @Test
    void createDiary_notOwnedCharacter_throwsNotFound() {
        givenOwnedCharacter(null);

        assertThatThrownBy(() -> service.createDiary(CHARACTER_ID, insertRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createDiary_notOwnedCharacter_doesNotSave() {
        givenOwnedCharacter(null);

        catchThrowable(() -> service.createDiary(CHARACTER_ID, insertRequest()));

        verify(database, never()).save(any());
    }

    @Test
    void updateDiary_ownedDiary_replacesAllFields() {
        Diary diary = diary("Chapter one");
        diary.setDescription("old");
        givenOwnedDiary(diary);

        DetailDiaryDTO result = service.updateDiary(DIARY_ID, new UpdateDiaryDTO("Chapter two", null, "#000000", null, null));

        assertThat(result)
                .extracting(DetailDiaryDTO::title, DetailDiaryDTO::description, DetailDiaryDTO::coverColor,
                        DetailDiaryDTO::font, DetailDiaryDTO::fontSize)
                .containsExactly("Chapter two", null, "#000000", null, null);
    }

    @Test
    void updateDiary_ownedDiary_saves() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);

        service.updateDiary(DIARY_ID, new UpdateDiaryDTO("Chapter two", null, null, null, null));

        verify(database).save(diary);
    }

    @Test
    void updateDiary_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);
        UpdateDiaryDTO request = new UpdateDiaryDTO("Chapter two", null, null, null, null);

        assertThatThrownBy(() -> service.updateDiary(DIARY_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryCover_validFile_returnsDetailWithNewCover() {
        givenOwnedDiary(diary("Chapter one"));
        Document cover = document("cover.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(cover);
        when(documentService.getDocumentUrl(cover)).thenReturn("https://storage.example/cover");

        DetailDiaryDTO result = service.updateDiaryCover(DIARY_ID, file);

        assertThat(result).extracting(DetailDiaryDTO::coverDocumentId, DetailDiaryDTO::coverUrl)
                .containsExactly(cover.getId(), "https://storage.example/cover");
    }

    @Test
    void updateDiaryCover_previousCover_deletesRecordThenCommitsThenDeletesFile() {
        Diary diary = diary("Chapter one");
        Document previous = document("old.png");
        diary.setCoverDocument(previous);
        givenOwnedDiary(diary);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("cover.png"));

        service.updateDiaryCover(DIARY_ID, file);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void updateDiaryCover_saveFails_deletesNewFile() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        Document cover = document("cover.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(cover);
        doThrow(new IllegalStateException("db down")).when(database).save(diary);

        catchThrowable(() -> service.updateDiaryCover(DIARY_ID, file));

        verify(documentService).deleteDocumentFile(cover);
    }

    @Test
    void updateDiaryCover_saveFails_keepsPreviousFile() {
        Diary diary = diary("Chapter one");
        Document previous = document("old.png");
        diary.setCoverDocument(previous);
        givenOwnedDiary(diary);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("cover.png"));
        doThrow(new IllegalStateException("db down")).when(database).save(diary);

        catchThrowable(() -> service.updateDiaryCover(DIARY_ID, file));

        verify(documentService, never()).deleteDocumentFile(previous);
    }

    @Test
    void updateDiaryCover_saveFails_rethrowsException() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("cover.png"));
        doThrow(new IllegalStateException("db down")).when(database).save(diary);

        assertThatThrownBy(() -> service.updateDiaryCover(DIARY_ID, file)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteDiaryCover_withCover_unlinksCover() {
        Diary diary = diary("Chapter one");
        diary.setCoverDocument(document("old.png"));
        givenOwnedDiary(diary);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiaryCover(DIARY_ID);

        verify(database).save(diary);
        assertThat(diary.getCoverDocument()).isNull();
    }

    @Test
    void deleteDiaryCover_withCover_deletesRecordThenCommitsThenDeletesFile() {
        Diary diary = diary("Chapter one");
        Document previous = document("old.png");
        diary.setCoverDocument(previous);
        givenOwnedDiary(diary);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiaryCover(DIARY_ID);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void deleteDiary_ownedDiary_deletesPagesThenDiaryInsideTransaction() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiary(DIARY_ID);

        InOrder order = inOrder(pages.where(), database, transaction);
        order.verify(pages.where()).delete();
        order.verify(database).delete(diary);
        order.verify(transaction).commit();
    }

    @Test
    void deleteDiary_ownedDiary_deletesOnlyPagesOfDiary() {
        givenOwnedDiary(diary("Chapter one"));
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiary(DIARY_ID);

        verify(pages.where()).eq("diary.id", DIARY_ID);
    }

    @Test
    void deleteDiary_pageAddedMeanwhile_throwsConflict() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).delete(diary);

        assertThatThrownBy(() -> service.deleteDiary(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deleteDiary_pageAddedMeanwhile_keepsCoverFile() {
        Diary diary = diary("Chapter one");
        Document cover = document("cover.png");
        diary.setCoverDocument(cover);
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).delete(diary);

        catchThrowable(() -> service.deleteDiary(DIARY_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteDiary_withCover_deletesRecordThenCommitsThenDeletesFile() {
        Diary diary = diary("Chapter one");
        Document cover = document("cover.png");
        diary.setCoverDocument(cover);
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiary(DIARY_ID);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(cover);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(cover);
    }

    @Test
    void deleteDiary_deleteFails_keepsCoverFile() {
        Diary diary = diary("Chapter one");
        Document cover = document("cover.png");
        diary.setCoverDocument(cover);
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).delete(diary);

        catchThrowable(() -> service.deleteDiary(DIARY_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteDiary_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.deleteDiary(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryCover_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.updateDiaryCover(DIARY_ID, file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryCover_notOwnedOrMissing_doesNotUploadFile() {
        givenOwnedDiary(null);

        catchThrowable(() -> service.updateDiaryCover(DIARY_ID, file));

        verify(documentService, never()).createImageDocument(any());
    }

    @Test
    void deleteDiaryCover_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.deleteDiaryCover(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteDiaryCover_notOwnedOrMissing_doesNotDeleteAnyFile() {
        givenOwnedDiary(null);

        catchThrowable(() -> service.deleteDiaryCover(DIARY_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteDiaryCover_withoutCover_commitsTransaction() {
        givenOwnedDiary(diary("Chapter one"));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiaryCover(DIARY_ID);

        verify(transaction).commit();
    }

    @Test
    void deleteDiaryCover_withoutCover_deletesNoFile() {
        givenOwnedDiary(diary("Chapter one"));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiaryCover(DIARY_ID);

        verify(documentService, never()).deleteDocumentFile(any(Document.class));
    }

    @Test
    void createDiary_characterDeletedBeforeCommit_throwsCharacterNotFound() {
        givenOwnedCharacter(character());
        doThrow(foreignKeyViolation()).when(database).save(any(Diary.class));

        assertThatThrownBy(() -> service.createDiary(CHARACTER_ID, insertRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Character not found"));
    }

    @Test
    void createDiary_otherDatabaseError_rethrowsIt() {
        givenOwnedCharacter(character());
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).save(any(Diary.class));

        assertThatThrownBy(() -> service.createDiary(CHARACTER_ID, insertRequest())).isSameAs(failure);
    }

    @Test
    void deleteDiary_ownedDiary_locksDiaryBeforeDeletingPages() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary("Chapter one"));
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteDiary(DIARY_ID);

        InOrder order = inOrder(database, diaries.query(), pages.where());
        order.verify(database).beginTransaction();
        order.verify(diaries.query()).forUpdate();
        order.verify(pages.where()).delete();
    }

    @Test
    void deleteDiary_diaryDeletedBeforeLock_throwsDiaryNotFound() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary("Chapter one"));
        when(diaries.where().findOne()).thenReturn(diary("Chapter one"), (Diary) null);
        when(database.beginTransaction()).thenReturn(transaction);

        assertThatThrownBy(() -> service.deleteDiary(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Diary not found"));
    }

    @Test
    void deleteDiary_otherDatabaseError_rethrowsIt() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).delete(diary);

        assertThatThrownBy(() -> service.deleteDiary(DIARY_ID)).isSameAs(failure);
    }

    @Test
    void getDiaryBookmark_diaryWithBookmark_returnsPageIdAndNumber() {
        Diary diary = diary("Chapter one");
        diary.setBookmarkPage(page(12));
        givenOwnedDiary(diary);

        BookmarkDiaryDTO result = service.getDiaryBookmark(DIARY_ID);

        assertThat(result).isEqualTo(new BookmarkDiaryDTO(PAGE_ID, (short) 12));
    }

    @Test
    void getDiaryBookmark_diaryWithoutBookmark_returnsEmptyBookmark() {
        givenOwnedDiary(diary("Chapter one"));

        BookmarkDiaryDTO result = service.getDiaryBookmark(DIARY_ID);

        assertThat(result).isEqualTo(new BookmarkDiaryDTO(null, null));
    }

    @Test
    void getDiaryBookmark_anyId_filtersByIdAndOwnerOfCharacter() {
        QueryStub<Diary> diaries = givenOwnedDiary(diary("Chapter one"));

        service.getDiaryBookmark(DIARY_ID);

        verify(diaries.where()).idEq(DIARY_ID);
        verify(diaries.where()).eq("character.owner.keycloakId", SUBJECT);
    }

    @Test
    void getDiaryBookmark_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.getDiaryBookmark(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryBookmark_pageOfDiary_savesDiaryWithBookmark() {
        Diary diary = diary("Chapter one");
        DiaryPage page = page(3);
        givenOwnedDiary(diary);
        givenPageOfDiary(page);

        service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID));

        verify(database).save(diary);
        assertThat(diary.getBookmarkPage()).isSameAs(page);
    }

    @Test
    void updateDiaryBookmark_pageOfDiary_returnsBookmark() {
        givenOwnedDiary(diary("Chapter one"));
        givenPageOfDiary(page(3));

        BookmarkDiaryDTO result = service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID));

        assertThat(result).isEqualTo(new BookmarkDiaryDTO(PAGE_ID, (short) 3));
    }

    @Test
    void updateDiaryBookmark_anyPage_looksThePageUpInTheSameDiary() {
        givenOwnedDiary(diary("Chapter one"));
        QueryStub<DiaryPage> pages = givenPageOfDiary(page(3));

        service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID));

        verify(pages.where()).idEq(PAGE_ID);
        verify(pages.where()).eq("diary.id", DIARY_ID);
    }

    @Test
    void updateDiaryBookmark_pageNotInDiary_throwsNotFound() {
        givenOwnedDiary(diary("Chapter one"));
        givenPageOfDiary(null);

        assertThatThrownBy(() -> service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryBookmark_pageNotInDiary_savesNothing() {
        givenOwnedDiary(diary("Chapter one"));
        givenPageOfDiary(null);

        catchThrowable(() -> service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID)));

        verify(database, never()).save(any(Diary.class));
    }

    @Test
    void updateDiaryBookmark_pageDeletedBeforeSave_throwsNotFound() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        givenPageOfDiary(page(3));
        doThrow(foreignKeyViolation()).when(database).save(diary);

        assertThatThrownBy(() -> service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDiaryBookmark_otherDatabaseError_rethrowsIt() {
        Diary diary = diary("Chapter one");
        givenOwnedDiary(diary);
        givenPageOfDiary(page(3));
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).save(diary);

        assertThatThrownBy(() -> service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID)))
                .isSameAs(failure);
    }

    @Test
    void updateDiaryBookmark_notOwnedOrMissingDiary_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteDiaryBookmark_diaryWithBookmark_clearsBookmarkAndSaves() {
        Diary diary = diary("Chapter one");
        diary.setBookmarkPage(page(3));
        givenOwnedDiary(diary);

        service.deleteDiaryBookmark(DIARY_ID);

        verify(database).save(diary);
        assertThat(diary.getBookmarkPage()).isNull();
    }

    @Test
    void deleteDiaryBookmark_notOwnedOrMissing_throwsNotFound() {
        givenOwnedDiary(null);

        assertThatThrownBy(() -> service.deleteDiaryBookmark(DIARY_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}

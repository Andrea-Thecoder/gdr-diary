package it.liberheroum.unit.services;

import static it.liberheroum.unit.support.QueryStub.pagedList;
import static it.liberheroum.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.DataIntegrityException;
import io.ebean.Database;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.diary.BaseDiaryDTO;
import it.liberheroum.dto.diary.BookmarkDiaryDTO;
import it.liberheroum.dto.diary.DetailDiaryDTO;
import it.liberheroum.dto.diary.InsertDiaryDTO;
import it.liberheroum.dto.diary.UpdateBookmarkDiaryDTO;
import it.liberheroum.dto.diary.UpdateDiaryDTO;
import it.liberheroum.dto.search.DiarySearchRequest;
import it.liberheroum.model.Diary;
import it.liberheroum.model.DiaryPage;
import it.liberheroum.model.Document;
import it.liberheroum.model.GameCharacter;
import it.liberheroum.security.JwtInspector;
import it.liberheroum.services.DiaryPageTagService;
import it.liberheroum.services.OwnedResources;
import it.liberheroum.services.DiaryService;
import it.liberheroum.services.DocumentService;
import it.liberheroum.unit.support.QueryStub;
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
    @Mock
    private DiaryPageTagService tagService;

    private final MultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[]{1});

    private DiaryService service;

    @BeforeEach
    void setUp() {
        service = new DiaryService(database, jwtInspector, documentService, tagService, new OwnedResources(database, jwtInspector));
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
    void createDiary_ownedCharacter_savesDiaryLinkedToCharacterAndReturnsRequestFields() {
        GameCharacter character = character();
        givenOwnedCharacter(character);
        ArgumentCaptor<Diary> saved = ArgumentCaptor.forClass(Diary.class);

        DetailDiaryDTO result = service.createDiary(CHARACTER_ID, insertRequest());

        verify(database).save(saved.capture());
        assertSoftly(softly -> {
            softly.assertThat(saved.getValue().getCharacter()).isSameAs(character);
            softly.assertThat(result)
                    .extracting(DetailDiaryDTO::title, DetailDiaryDTO::description, DetailDiaryDTO::coverColor,
                            DetailDiaryDTO::font, DetailDiaryDTO::fontSize)
                    .containsExactly("Chapter one", "The journey begins", "#1A2B3C", "Lora", (short) 16);
        });
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
    void updateDiaryCover_previousCover_returnsNewCoverAndDeletesPreviousFileAfterCommit() {
        Diary diary = diary("Chapter one");
        Document previous = document("old.png");
        diary.setCoverDocument(previous);
        givenOwnedDiary(diary);
        Document cover = document("cover.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(cover);
        when(documentService.getDocumentUrl(cover)).thenReturn("https://storage.example/cover");

        DetailDiaryDTO result = service.updateDiaryCover(DIARY_ID, file);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
        assertThat(result).extracting(DetailDiaryDTO::coverDocumentId, DetailDiaryDTO::coverUrl)
                .containsExactly(cover.getId(), "https://storage.example/cover");
    }

    @Test
    void updateDiaryCover_saveFails_rethrowsDeletesNewFileAndKeepsPreviousFile() {
        Diary diary = diary("Chapter one");
        Document previous = document("old.png");
        diary.setCoverDocument(previous);
        givenOwnedDiary(diary);
        Document cover = document("cover.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(cover);
        doThrow(new IllegalStateException("db down")).when(database).save(diary);

        Throwable thrown = catchThrowable(() -> service.updateDiaryCover(DIARY_ID, file));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(documentService).deleteDocumentFile(cover);
        verify(documentService, never()).deleteDocumentFile(previous);
    }

    @Test
    void deleteDiaryCover_withCover_unlinksCoverAndDeletesFileAfterCommit() {
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
        assertThat(diary.getCoverDocument()).isNull();
    }

    @Test
    void deleteDiary_pageAddedMeanwhile_throwsConflictAndKeepsCoverFile() {
        Diary diary = diary("Chapter one");
        Document cover = document("cover.png");
        diary.setCoverDocument(cover);
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).delete(diary);

        Throwable thrown = catchThrowable(() -> service.deleteDiary(DIARY_ID));

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteDiary_otherDatabaseError_rethrowsItAndKeepsCoverFile() {
        Diary diary = diary("Chapter one");
        diary.setCoverDocument(document("cover.png"));
        givenOwnedDiary(diary);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).delete(diary);

        Throwable thrown = catchThrowable(() -> service.deleteDiary(DIARY_ID));

        assertThat(thrown).isSameAs(failure);
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
    void getDiaryBookmark_diaryWithBookmark_returnsPageIdAndNumber() {
        Diary diary = diary("Chapter one");
        diary.setBookmarkPage(page(12));
        givenOwnedDiary(diary);

        BookmarkDiaryDTO result = service.getDiaryBookmark(DIARY_ID);

        assertThat(result).isEqualTo(new BookmarkDiaryDTO(PAGE_ID, (short) 12));
    }

    @Test
    void updateDiaryBookmark_pageOfDiary_setsBookmarkAndReturnsIt() {
        Diary diary = diary("Chapter one");
        DiaryPage page = page(3);
        givenOwnedDiary(diary);
        givenPageOfDiary(page);

        BookmarkDiaryDTO result = service.updateDiaryBookmark(DIARY_ID, new UpdateBookmarkDiaryDTO(PAGE_ID));

        assertSoftly(softly -> {
            softly.assertThat(result).isEqualTo(new BookmarkDiaryDTO(PAGE_ID, (short) 3));
            softly.assertThat(diary.getBookmarkPage()).isSameAs(page);
        });
    }

    @Test
    void updateDiaryBookmark_pageDeletedBeforeSave_throwsPageNotFound() {
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
}

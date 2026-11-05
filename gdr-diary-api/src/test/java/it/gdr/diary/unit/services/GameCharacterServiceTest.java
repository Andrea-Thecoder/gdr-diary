package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import it.gdr.diary.dto.game_character.BaseGameCharacterDTO;
import it.gdr.diary.dto.game_character.DetailGameCharacterDTO;
import it.gdr.diary.dto.game_character.InsertGameCharacterDTO;
import it.gdr.diary.dto.game_character.UpdateGameCharacterDTO;
import it.gdr.diary.dto.search.GameCharacterSearchRequest;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.DocumentService;
import it.gdr.diary.services.GameCharacterService;
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
class GameCharacterServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private DocumentService documentService;
    @Mock
    private Transaction transaction;

    private final MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});

    private GameCharacterService service;

    @BeforeEach
    void setUp() {
        service = new GameCharacterService(database, jwtInspector, documentService);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
    }

    private GameCharacter character(String name) {
        GameCharacter character = new GameCharacter();
        character.setId(CHARACTER_ID);
        character.setName(name);
        character.setKind(GameCharacterKind.PLAYER);
        return character;
    }

    private Document document(String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        return document;
    }

    private Diary diary(Document cover) {
        Diary diary = new Diary();
        diary.setId(UUID.randomUUID());
        diary.setCoverDocument(cover);
        return diary;
    }

    private QueryStub<GameCharacter> givenOwnedCharacter(GameCharacter character) {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        when(characters.where().findOne()).thenReturn(character);
        return characters;
    }

    private QueryStub<Diary> givenDiaries(List<Diary> diaries) {
        QueryStub<Diary> stub = stubFind(database, Diary.class);
        when(stub.where().findList()).thenReturn(diaries);
        return stub;
    }

    private QueryStub<CharacterSheet> givenSheets(List<CharacterSheet> sheets) {
        QueryStub<CharacterSheet> stub = stubFind(database, CharacterSheet.class);
        when(stub.where().findList()).thenReturn(sheets);
        return stub;
    }

    private CharacterSheet sheet(Document document) {
        CharacterSheet sheet = new CharacterSheet();
        sheet.setId(UUID.randomUUID());
        sheet.setDocument(document);
        return sheet;
    }

    private static DataIntegrityException foreignKeyViolation() {
        return new DataIntegrityException("violates foreign key constraint",
                new SQLException("violates foreign key constraint", "23503"));
    }

    private InsertGameCharacterDTO insertRequest() {
        return new InsertGameCharacterDTO("Aragorn", GameCharacterKind.PLAYER, "Ranger of the North", "Lost Mine", "#1A2B3C");
    }

    @Test
    void findAllGameCharacters_anyRequest_filtersByCurrentUser() {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        PagedList<GameCharacter> found = pagedList(List.of(), 0, 20, 0);
        when(characters.where().findPagedList()).thenReturn(found);

        service.findAllGameCharacters(new GameCharacterSearchRequest());

        verify(characters.where()).eq("owner.keycloakId", SUBJECT);
    }

    @Test
    void findAllGameCharacters_noSort_ordersByName() {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        PagedList<GameCharacter> found = pagedList(List.of(), 0, 20, 0);
        when(characters.where().findPagedList()).thenReturn(found);

        service.findAllGameCharacters(new GameCharacterSearchRequest());

        verify(characters.where()).orderBy("name asc, id asc");
    }

    @Test
    void findAllGameCharacters_nameFilter_appliesFilterOfRequest() {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        PagedList<GameCharacter> found = pagedList(List.of(), 0, 20, 0);
        when(characters.where().findPagedList()).thenReturn(found);
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setName("ara");

        service.findAllGameCharacters(request);

        verify(characters.where()).icontains("name", "ara");
    }

    @Test
    void findAllGameCharacters_charactersWithAvatar_mapsAvatarUrl() {
        GameCharacter character = character("Aragorn");
        Document avatar = document("avatar.png");
        character.setAvatarDocument(avatar);
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        PagedList<GameCharacter> found = pagedList(List.of(character), 0, 20, 1);
        when(characters.where().findPagedList()).thenReturn(found);
        when(documentService.getDocumentUrl(avatar)).thenReturn("https://storage.example/avatar");

        PagedResultDTO<BaseGameCharacterDTO> result = service.findAllGameCharacters(new GameCharacterSearchRequest());

        assertThat(result.getList()).extracting(BaseGameCharacterDTO::avatarUrl).containsExactly("https://storage.example/avatar");
    }

    @Test
    void findAllGameCharacters_firstPage_returnsOneBasedPage() {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        PagedList<GameCharacter> found = pagedList(List.of(character("Aragorn")), 0, 20, 1);
        when(characters.where().findPagedList()).thenReturn(found);

        PagedResultDTO<BaseGameCharacterDTO> result = service.findAllGameCharacters(new GameCharacterSearchRequest());

        assertThat(result.getPage()).isEqualTo(1);
    }

    @Test
    void getGameCharacterById_ownedCharacter_returnsDetail() {
        givenOwnedCharacter(character("Aragorn"));

        DetailGameCharacterDTO result = service.getGameCharacterById(CHARACTER_ID);

        assertThat(result).extracting(DetailGameCharacterDTO::id, DetailGameCharacterDTO::name).containsExactly(CHARACTER_ID, "Aragorn");
    }

    @Test
    void getGameCharacterById_notOwnedOrMissing_throwsNotFound() {
        givenOwnedCharacter(null);

        assertThatThrownBy(() -> service.getGameCharacterById(CHARACTER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getGameCharacterById_anyId_filtersByIdAndCurrentUser() {
        QueryStub<GameCharacter> characters = givenOwnedCharacter(character("Aragorn"));

        service.getGameCharacterById(CHARACTER_ID);

        verify(characters.where()).idEq(CHARACTER_ID);
        verify(characters.where()).eq("owner.keycloakId", SUBJECT);
    }

    @Test
    void createGameCharacter_userExisting_savesCharacterOwnedByUser() {
        AppUser owner = new AppUser();
        QueryStub<AppUser> users = stubFind(database, AppUser.class);
        when(users.where().findOne()).thenReturn(owner);
        ArgumentCaptor<GameCharacter> saved = ArgumentCaptor.forClass(GameCharacter.class);

        service.createGameCharacter(insertRequest());

        verify(database).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(owner);
    }

    @Test
    void createGameCharacter_userExisting_copiesRequestFields() {
        QueryStub<AppUser> users = stubFind(database, AppUser.class);
        when(users.where().findOne()).thenReturn(new AppUser());

        DetailGameCharacterDTO result = service.createGameCharacter(insertRequest());

        assertThat(result)
                .extracting(DetailGameCharacterDTO::name, DetailGameCharacterDTO::kind, DetailGameCharacterDTO::description,
                        DetailGameCharacterDTO::campaignName, DetailGameCharacterDTO::backgroundColor)
                .containsExactly("Aragorn", GameCharacterKind.PLAYER, "Ranger of the North", "Lost Mine", "#1A2B3C");
    }

    @Test
    void createGameCharacter_userMissing_throwsNotFound() {
        stubFind(database, AppUser.class);

        assertThatThrownBy(() -> service.createGameCharacter(insertRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createGameCharacter_userMissing_doesNotSave() {
        stubFind(database, AppUser.class);

        catchThrowable(() -> service.createGameCharacter(insertRequest()));

        verify(database, never()).save(any());
    }

    @Test
    void updateGameCharacter_ownedCharacter_replacesEditableFields() {
        GameCharacter character = character("Aragorn");
        character.setDescription("old");
        givenOwnedCharacter(character);

        DetailGameCharacterDTO result = service.updateGameCharacter(CHARACTER_ID,
                new UpdateGameCharacterDTO("Strider", null, "Curse of Strahd", "#000000"));

        assertThat(result)
                .extracting(DetailGameCharacterDTO::name, DetailGameCharacterDTO::description,
                        DetailGameCharacterDTO::campaignName, DetailGameCharacterDTO::backgroundColor)
                .containsExactly("Strider", null, "Curse of Strahd", "#000000");
    }

    @Test
    void updateGameCharacter_ownedCharacter_keepsKind() {
        GameCharacter character = character("Aragorn");
        character.setKind(GameCharacterKind.GAME_MASTER);
        givenOwnedCharacter(character);

        DetailGameCharacterDTO result = service.updateGameCharacter(CHARACTER_ID,
                new UpdateGameCharacterDTO("Strider", null, null, null));

        assertThat(result.kind()).isEqualTo(GameCharacterKind.GAME_MASTER);
    }

    @Test
    void updateGameCharacter_ownedCharacter_saves() {
        GameCharacter character = character("Aragorn");
        givenOwnedCharacter(character);

        service.updateGameCharacter(CHARACTER_ID, new UpdateGameCharacterDTO("Strider", null, null, null));

        verify(database).save(character);
    }

    @Test
    void updateGameCharacter_notOwnedOrMissing_throwsNotFound() {
        givenOwnedCharacter(null);
        UpdateGameCharacterDTO request = new UpdateGameCharacterDTO("Strider", null, null, null);

        assertThatThrownBy(() -> service.updateGameCharacter(CHARACTER_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateGameCharacterAvatar_validFile_returnsDetailWithNewAvatar() {
        givenOwnedCharacter(character("Aragorn"));
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);
        when(documentService.getDocumentUrl(avatar)).thenReturn("https://storage.example/new");

        DetailGameCharacterDTO result = service.updateGameCharacterAvatar(CHARACTER_ID, file);

        assertThat(result).extracting(DetailGameCharacterDTO::avatarDocumentId, DetailGameCharacterDTO::avatarUrl)
                .containsExactly(avatar.getId(), "https://storage.example/new");
    }

    @Test
    void updateGameCharacterAvatar_previousAvatar_deletesRecordThenCommitsThenDeletesFile() {
        GameCharacter character = character("Aragorn");
        Document previous = document("old.png");
        character.setAvatarDocument(previous);
        givenOwnedCharacter(character);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));

        service.updateGameCharacterAvatar(CHARACTER_ID, file);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void updateGameCharacterAvatar_saveFails_deletesNewFile() {
        GameCharacter character = character("Aragorn");
        givenOwnedCharacter(character);
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);
        doThrow(new IllegalStateException("db down")).when(database).save(character);

        catchThrowable(() -> service.updateGameCharacterAvatar(CHARACTER_ID, file));

        verify(documentService).deleteDocumentFile(avatar);
    }

    @Test
    void updateGameCharacterAvatar_saveFails_keepsPreviousFile() {
        GameCharacter character = character("Aragorn");
        Document previous = document("old.png");
        character.setAvatarDocument(previous);
        givenOwnedCharacter(character);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));
        doThrow(new IllegalStateException("db down")).when(database).save(character);

        catchThrowable(() -> service.updateGameCharacterAvatar(CHARACTER_ID, file));

        verify(documentService, never()).deleteDocumentFile(previous);
    }

    @Test
    void updateGameCharacterAvatar_uploadFails_doesNotCommit() {
        givenOwnedCharacter(character("Aragorn"));
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenThrow(new IllegalStateException("storage down"));

        catchThrowable(() -> service.updateGameCharacterAvatar(CHARACTER_ID, file));

        verify(transaction, never()).commit();
    }

    @Test
    void updateGameCharacterAvatar_notOwnedOrMissing_doesNotUploadFile() {
        givenOwnedCharacter(null);

        catchThrowable(() -> service.updateGameCharacterAvatar(CHARACTER_ID, file));

        verify(documentService, never()).createImageDocument(any());
    }

    @Test
    void deleteGameCharacterAvatar_withAvatar_unlinksAvatar() {
        GameCharacter character = character("Aragorn");
        character.setAvatarDocument(document("old.png"));
        givenOwnedCharacter(character);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacterAvatar(CHARACTER_ID);

        verify(database).save(character);
        assertThat(character.getAvatarDocument()).isNull();
    }

    @Test
    void deleteGameCharacterAvatar_withAvatar_deletesRecordThenCommitsThenDeletesFile() {
        GameCharacter character = character("Aragorn");
        Document previous = document("old.png");
        character.setAvatarDocument(previous);
        givenOwnedCharacter(character);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacterAvatar(CHARACTER_ID);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void deleteGameCharacter_withDiaries_deletesPagesOfAllDiaries() {
        givenOwnedCharacter(character("Aragorn"));
        Diary first = diary(null);
        Diary second = diary(null);
        givenSheets(List.of());
        givenDiaries(List.of(first, second));
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        verify(pages.where()).in("diary.id", List.of(first.getId(), second.getId()));
        verify(pages.where()).delete();
    }

    @Test
    void deleteGameCharacter_withDiaries_deletesDiariesBeforeCharacter() {
        GameCharacter character = character("Aragorn");
        givenOwnedCharacter(character);
        List<Diary> diaries = List.of(diary(null));
        givenSheets(List.of());
        givenDiaries(diaries);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        InOrder order = inOrder(database);
        order.verify(database).deleteAll(diaries);
        order.verify(database).delete(character);
    }

    @Test
    void deleteGameCharacter_withoutDiaries_doesNotTouchPagesOrDiaries() {
        givenOwnedCharacter(character("Aragorn"));
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        verify(database, never()).find(DiaryPage.class);
        verify(database, never()).deleteAll(anyList());
    }

    @Test
    void deleteGameCharacter_avatarAndCovers_deletesAllRecordsInsideTransaction() {
        GameCharacter character = character("Aragorn");
        Document avatar = document("avatar.png");
        Document cover = document("cover.png");
        character.setAvatarDocument(avatar);
        givenOwnedCharacter(character);
        givenSheets(List.of());
        givenDiaries(List.of(diary(cover), diary(null)));
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(avatar);
        order.verify(documentService).deleteDocumentRecord(cover);
        order.verify(transaction).commit();
    }

    @Test
    void deleteGameCharacter_avatarAndCovers_deletesAllFilesAfterCommit() {
        GameCharacter character = character("Aragorn");
        Document avatar = document("avatar.png");
        Document cover = document("cover.png");
        character.setAvatarDocument(avatar);
        givenOwnedCharacter(character);
        givenSheets(List.of());
        givenDiaries(List.of(diary(cover)));
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        InOrder order = inOrder(transaction, documentService);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(avatar);
        order.verify(documentService).deleteDocumentFile(cover);
    }

    @Test
    void deleteGameCharacter_deleteFails_doesNotDeleteFiles() {
        GameCharacter character = character("Aragorn");
        character.setAvatarDocument(document("avatar.png"));
        givenOwnedCharacter(character);
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).delete(character);

        catchThrowable(() -> service.deleteGameCharacter(CHARACTER_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteGameCharacter_notOwnedOrMissing_throwsNotFound() {
        givenOwnedCharacter(null);

        assertThatThrownBy(() -> service.deleteGameCharacter(CHARACTER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteGameCharacter_notOwnedOrMissing_doesNotOpenTransaction() {
        givenOwnedCharacter(null);

        catchThrowable(() -> service.deleteGameCharacter(CHARACTER_ID));

        verify(database, never()).beginTransaction();
    }

    @Test
    void deleteGameCharacterAvatar_notOwnedOrMissing_throwsNotFound() {
        givenOwnedCharacter(null);

        assertThatThrownBy(() -> service.deleteGameCharacterAvatar(CHARACTER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteGameCharacterAvatar_notOwnedOrMissing_doesNotDeleteAnyFile() {
        givenOwnedCharacter(null);

        catchThrowable(() -> service.deleteGameCharacterAvatar(CHARACTER_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteGameCharacterAvatar_withoutAvatar_commitsTransaction() {
        givenOwnedCharacter(character("Aragorn"));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacterAvatar(CHARACTER_ID);

        verify(transaction).commit();
    }

    @Test
    void deleteGameCharacterAvatar_withoutAvatar_deletesNoFile() {
        givenOwnedCharacter(character("Aragorn"));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacterAvatar(CHARACTER_ID);

        verify(documentService, never()).deleteDocumentFile(any(Document.class));
    }

    @Test
    void createGameCharacter_userDeletedBeforeCommit_throwsUserNotFound() {
        QueryStub<AppUser> users = stubFind(database, AppUser.class);
        when(users.where().findOne()).thenReturn(new AppUser());
        doThrow(foreignKeyViolation()).when(database).save(any(GameCharacter.class));

        assertThatThrownBy(() -> service.createGameCharacter(insertRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Test
    void createGameCharacter_otherDatabaseError_rethrowsIt() {
        QueryStub<AppUser> users = stubFind(database, AppUser.class);
        when(users.where().findOne()).thenReturn(new AppUser());
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).save(any(GameCharacter.class));

        assertThatThrownBy(() -> service.createGameCharacter(insertRequest())).isSameAs(failure);
    }

    @Test
    void deleteGameCharacter_diaryCreatedMeanwhile_throwsConflict() {
        GameCharacter character = character("Aragorn");
        givenOwnedCharacter(character);
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).delete(character);

        assertThatThrownBy(() -> service.deleteGameCharacter(CHARACTER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.CONFLICT, "The character changed while it was being deleted, try again"));
    }

    @Test
    void deleteGameCharacter_pageCreatedMeanwhile_throwsConflict() {
        givenOwnedCharacter(character("Aragorn"));
        List<Diary> diaries = List.of(diary(null));
        givenSheets(List.of());
        givenDiaries(diaries);
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).deleteAll(diaries);

        assertThatThrownBy(() -> service.deleteGameCharacter(CHARACTER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void deleteGameCharacter_diaryCreatedMeanwhile_deletesNoFile() {
        GameCharacter character = character("Aragorn");
        character.setAvatarDocument(document("avatar.png"));
        givenOwnedCharacter(character);
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).delete(character);

        catchThrowable(() -> service.deleteGameCharacter(CHARACTER_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteGameCharacter_anyCharacter_selectsOnlySheetsOfCharacter() {
        givenOwnedCharacter(character("Aragorn"));
        QueryStub<CharacterSheet> sheets = givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        verify(sheets.where()).eq("character.id", CHARACTER_ID);
    }

    @Test
    void deleteGameCharacter_withoutSheets_doesNotDeleteSheets() {
        givenOwnedCharacter(character("Aragorn"));
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        verify(database, never()).deleteAll(anyList());
    }

    @Test
    void deleteGameCharacter_withSheets_deletesSheetsBeforeCharacter() {
        GameCharacter character = character("Aragorn");
        givenOwnedCharacter(character);
        List<CharacterSheet> sheets = List.of(sheet(document("sheet.pdf")));
        givenSheets(sheets);
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        InOrder order = inOrder(database);
        order.verify(database).deleteAll(sheets);
        order.verify(database).delete(character);
    }

    @Test
    void deleteGameCharacter_withSheets_deletesSheetRecordsBeforeCommit() {
        givenOwnedCharacter(character("Aragorn"));
        Document pdf = document("sheet.pdf");
        givenSheets(List.of(sheet(pdf)));
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(pdf);
        order.verify(transaction).commit();
    }

    @Test
    void deleteGameCharacter_withSheets_deletesSheetFilesAfterCommit() {
        givenOwnedCharacter(character("Aragorn"));
        Document pdf = document("sheet.pdf");
        givenSheets(List.of(sheet(pdf)));
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteGameCharacter(CHARACTER_ID);

        InOrder order = inOrder(transaction, documentService);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(pdf);
    }

    @Test
    void deleteGameCharacter_sheetsDeleteFails_deletesNoFile() {
        givenOwnedCharacter(character("Aragorn"));
        List<CharacterSheet> sheets = List.of(sheet(document("sheet.pdf")));
        givenSheets(sheets);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).deleteAll(sheets);

        catchThrowable(() -> service.deleteGameCharacter(CHARACTER_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteGameCharacter_sheetCreatedMeanwhile_throwsConflict() {
        givenOwnedCharacter(character("Aragorn"));
        List<CharacterSheet> sheets = List.of(sheet(document("sheet.pdf")));
        givenSheets(sheets);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(foreignKeyViolation()).when(database).deleteAll(sheets);

        assertThatThrownBy(() -> service.deleteGameCharacter(CHARACTER_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}

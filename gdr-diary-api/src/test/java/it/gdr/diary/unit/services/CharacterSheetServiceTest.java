package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
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
import it.gdr.diary.config.CharacterSheetConfig;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.character_sheet.BaseCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.DetailCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.InsertCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.UpdateCharacterSheetDTO;
import it.gdr.diary.dto.search.CharacterSheetSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.CharacterSheetService;
import it.gdr.diary.services.DocumentService;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CharacterSheetServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final UUID SHEET_ID = UUID.fromString("7d1e5a52-9a0c-4b57-8c64-0f7a1d9a4e21");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private DocumentService documentService;
    @Mock
    private Transaction transaction;

    private final CharacterSheetConfig config = new CharacterSheetConfig();
    private final MultipartFile file = new MockMultipartFile("file", "sheet.pdf", "application/pdf", new byte[]{1});

    private CharacterSheetService service;

    @BeforeEach
    void setUp() {
        service = new CharacterSheetService(database, jwtInspector, documentService, config);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
    }

    private GameCharacter character() {
        GameCharacter character = new GameCharacter();
        character.setId(CHARACTER_ID);
        character.setName("Alira");
        return character;
    }

    private Document document(String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setSize(2048L);
        return document;
    }

    private CharacterSheet sheet(Document document) {
        CharacterSheet sheet = new CharacterSheet();
        sheet.setId(SHEET_ID);
        sheet.setCharacter(character());
        sheet.setTitle("Alira, level 9");
        sheet.setGameSystem("D&D 5e");
        sheet.setDocument(document);
        return sheet;
    }

    private QueryStub<GameCharacter> givenOwnedCharacter(GameCharacter character) {
        QueryStub<GameCharacter> stub = stubFind(database, GameCharacter.class);
        when(stub.where().findOne()).thenReturn(character);
        return stub;
    }

    private QueryStub<CharacterSheet> givenOwnedSheet(CharacterSheet sheet) {
        QueryStub<CharacterSheet> stub = stubFind(database, CharacterSheet.class);
        when(stub.where().findOne()).thenReturn(sheet);
        return stub;
    }

    private QueryStub<CharacterSheet> givenSheetCount(int count) {
        QueryStub<CharacterSheet> stub = stubFind(database, CharacterSheet.class);
        when(stub.where().findCount()).thenReturn(count);
        return stub;
    }

    private static DataIntegrityException foreignKeyViolation() {
        return new DataIntegrityException("violates foreign key constraint",
                new SQLException("violates foreign key constraint", "23503"));
    }

    private InsertCharacterSheetDTO insertRequest() {
        return new InsertCharacterSheetDTO("Alira, level 9", "D&D 5e");
    }

    private void givenUploadAllowed(Document uploaded) {
        givenOwnedCharacter(character());
        givenSheetCount(0);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createPdfDocument(file)).thenReturn(uploaded);
    }

    // ---- findAllCharacterSheetsByCharacter ----

    @Test
    void findAllCharacterSheetsByCharacter_sheetsFound_returnsMappedItemsOnFirstPage() {
        givenOwnedCharacter(character());
        Document pdf = document("sheet.pdf");
        QueryStub<CharacterSheet> sheets = stubFind(database, CharacterSheet.class);
        PagedList<CharacterSheet> found = pagedList(List.of(sheet(pdf)), 0, 20, 1);
        when(sheets.where().findPagedList()).thenReturn(found);
        when(documentService.getDocumentUrl(pdf)).thenReturn("https://storage.example/view");
        when(documentService.getDocumentDownloadUrl(pdf)).thenReturn("https://storage.example/download");

        PagedResultDTO<BaseCharacterSheetDTO> result = service.findAllCharacterSheetsByCharacter(CHARACTER_ID, new CharacterSheetSearchRequest());

        assertSoftly(softly -> {
            softly.assertThat(result.getList()).extracting(BaseCharacterSheetDTO::id, BaseCharacterSheetDTO::characterId,
                            BaseCharacterSheetDTO::title, BaseCharacterSheetDTO::system, BaseCharacterSheetDTO::sizeBytes,
                            BaseCharacterSheetDTO::url, BaseCharacterSheetDTO::downloadUrl)
                    .containsExactly(tuple(SHEET_ID, CHARACTER_ID, "Alira, level 9", "D&D 5e", 2048L,
                            "https://storage.example/view", "https://storage.example/download"));
            softly.assertThat(result.getPage()).isEqualTo(1);
        });
    }

    @Test
    void findAllCharacterSheetsByCharacter_characterNotOwnedOrMissing_throwsNotFoundWithReason() {
        givenOwnedCharacter(null);
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();

        assertThatThrownBy(() -> service.findAllCharacterSheetsByCharacter(CHARACTER_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Character not found"));
    }

    // ---- getCharacterSheetById ----

    @Test
    void getCharacterSheetById_ownedSheet_returnsDetailWithUrls() {
        Document pdf = document("sheet.pdf");
        givenOwnedSheet(sheet(pdf));
        when(documentService.getDocumentUrl(pdf)).thenReturn("https://storage.example/view");
        when(documentService.getDocumentDownloadUrl(pdf)).thenReturn("https://storage.example/download");

        DetailCharacterSheetDTO result = service.getCharacterSheetById(SHEET_ID);

        assertThat(result).extracting(DetailCharacterSheetDTO::id, DetailCharacterSheetDTO::characterId,
                        DetailCharacterSheetDTO::title, DetailCharacterSheetDTO::system, DetailCharacterSheetDTO::fileName,
                        DetailCharacterSheetDTO::sizeBytes, DetailCharacterSheetDTO::url, DetailCharacterSheetDTO::downloadUrl)
                .containsExactly(SHEET_ID, CHARACTER_ID, "Alira, level 9", "D&D 5e", "sheet.pdf", 2048L,
                        "https://storage.example/view", "https://storage.example/download");
    }

    @Test
    void getCharacterSheetById_notOwnedOrMissing_throwsNotFoundWithReason() {
        givenOwnedSheet(null);

        assertThatThrownBy(() -> service.getCharacterSheetById(SHEET_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Sheet not found"));
    }

    // ---- createCharacterSheet ----

    @Test
    void createCharacterSheet_validRequest_returnsDetailWithUrls() {
        Document uploaded = document("sheet.pdf");
        givenUploadAllowed(uploaded);
        when(documentService.getDocumentUrl(uploaded)).thenReturn("https://storage.example/view");
        when(documentService.getDocumentDownloadUrl(uploaded)).thenReturn("https://storage.example/download");

        DetailCharacterSheetDTO result = service.createCharacterSheet(CHARACTER_ID, insertRequest(), file);

        assertThat(result).extracting(DetailCharacterSheetDTO::characterId, DetailCharacterSheetDTO::title,
                        DetailCharacterSheetDTO::system, DetailCharacterSheetDTO::fileName, DetailCharacterSheetDTO::url,
                        DetailCharacterSheetDTO::downloadUrl)
                .containsExactly(CHARACTER_ID, "Alira, level 9", "D&D 5e", "sheet.pdf",
                        "https://storage.example/view", "https://storage.example/download");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   "})
    void createCharacterSheet_blankOrNullSystem_returnsNullSystem(String system) {
        givenUploadAllowed(document("sheet.pdf"));

        DetailCharacterSheetDTO result = service.createCharacterSheet(CHARACTER_ID, new InsertCharacterSheetDTO("Alira", system), file);

        assertThat(result.system()).isNull();
    }

    @Test
    void createCharacterSheet_customLimitReached_throwsServiceExceptionWithConfiguredLimit() {
        config.setMaxSheetsPerCharacter(2);
        givenOwnedCharacter(character());
        givenSheetCount(2);
        when(database.beginTransaction()).thenReturn(transaction);
        InsertCharacterSheetDTO request = insertRequest();

        assertThatThrownBy(() -> service.createCharacterSheet(CHARACTER_ID, request, file))
                .isInstanceOf(ServiceException.class)
                .hasMessage("The character has too many sheets (2)");
    }

    @Test
    void createCharacterSheet_customLimitHigherThanDefault_allowsMoreSheets() {
        config.setMaxSheetsPerCharacter(50);
        givenOwnedCharacter(character());
        givenSheetCount(10);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createPdfDocument(file)).thenReturn(document("sheet.pdf"));

        DetailCharacterSheetDTO result = service.createCharacterSheet(CHARACTER_ID, insertRequest(), file);

        assertThat(result.title()).isEqualTo("Alira, level 9");
    }

    @Test
    void createCharacterSheet_characterDeletedBeforeLock_throwsNotFoundWithoutUpload() {
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        when(characters.where().findOne()).thenReturn(character(), (GameCharacter) null);
        when(database.beginTransaction()).thenReturn(transaction);
        InsertCharacterSheetDTO request = insertRequest();

        assertThatThrownBy(() -> service.createCharacterSheet(CHARACTER_ID, request, file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(documentService, never()).createPdfDocument(any());
    }

    @Test
    void createCharacterSheet_saveFails_deletesUploadedFileWithoutCommit() {
        Document uploaded = document("sheet.pdf");
        givenUploadAllowed(uploaded);
        doThrow(new IllegalStateException("db down")).when(database).save(any(CharacterSheet.class));

        catchThrowable(() -> service.createCharacterSheet(CHARACTER_ID, insertRequest(), file));

        verify(documentService).deleteDocumentFile(uploaded);
        verify(transaction, never()).commit();
    }

    @Test
    void createCharacterSheet_characterDeletedMeanwhile_throwsNotFoundAndDeletesFile() {
        Document uploaded = document("sheet.pdf");
        givenUploadAllowed(uploaded);
        doThrow(foreignKeyViolation()).when(database).save(any(CharacterSheet.class));

        assertThatThrownBy(() -> service.createCharacterSheet(CHARACTER_ID, insertRequest(), file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.NOT_FOUND, "Character not found"));
        verify(documentService).deleteDocumentFile(uploaded);
    }

    @Test
    void createCharacterSheet_otherDatabaseError_rethrowsItAndDeletesFile() {
        Document uploaded = document("sheet.pdf");
        givenUploadAllowed(uploaded);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).save(any(CharacterSheet.class));

        assertThatThrownBy(() -> service.createCharacterSheet(CHARACTER_ID, insertRequest(), file)).isSameAs(failure);
        verify(documentService).deleteDocumentFile(uploaded);
    }

    // ---- updateCharacterSheet ----

    @Test
    void updateCharacterSheet_valuesWithSpaces_returnsTrimmedUpdatedDetail() {
        givenOwnedSheet(sheet(document("sheet.pdf")));

        DetailCharacterSheetDTO result = service.updateCharacterSheet(SHEET_ID, new UpdateCharacterSheetDTO("  New title ", "  Pathfinder 2e  "));

        assertThat(result).extracting(DetailCharacterSheetDTO::id, DetailCharacterSheetDTO::title, DetailCharacterSheetDTO::system)
                .containsExactly(SHEET_ID, "New title", "Pathfinder 2e");
    }

    // ---- updateCharacterSheetFile ----

    @Test
    void updateCharacterSheetFile_validFile_deletesPreviousRecordThenCommitsThenDeletesPreviousFile() {
        Document previous = document("old.pdf");
        givenOwnedSheet(sheet(previous));
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createPdfDocument(file)).thenReturn(document("new.pdf"));

        service.updateCharacterSheetFile(SHEET_ID, file);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void updateCharacterSheetFile_saveFails_deletesNewFileAndKeepsPreviousOne() {
        CharacterSheet sheet = sheet(document("old.pdf"));
        Document previous = sheet.getDocument();
        givenOwnedSheet(sheet);
        Document replacement = document("new.pdf");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createPdfDocument(file)).thenReturn(replacement);
        doThrow(new IllegalStateException("db down")).when(database).save(sheet);

        catchThrowable(() -> service.updateCharacterSheetFile(SHEET_ID, file));

        verify(documentService).deleteDocumentFile(replacement);
        verify(documentService, never()).deleteDocumentFile(previous);
    }

    // ---- deleteCharacterSheet ----

    @Test
    void deleteCharacterSheet_ownedSheet_deletesRowsInTransactionThenFileAfterCommit() {
        CharacterSheet sheet = sheet(document("sheet.pdf"));
        Document pdf = sheet.getDocument();
        givenOwnedSheet(sheet);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteCharacterSheet(SHEET_ID);

        InOrder order = inOrder(database, documentService, transaction);
        order.verify(database).delete(sheet);
        order.verify(documentService).deleteDocumentRecord(pdf);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(pdf);
    }

    @Test
    void deleteCharacterSheet_deleteFails_keepsTheFile() {
        CharacterSheet sheet = sheet(document("sheet.pdf"));
        givenOwnedSheet(sheet);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).delete(sheet);

        catchThrowable(() -> service.deleteCharacterSheet(SHEET_ID));

        verify(transaction, never()).commit();
        verify(documentService, never()).deleteDocumentFile(any());
    }
}

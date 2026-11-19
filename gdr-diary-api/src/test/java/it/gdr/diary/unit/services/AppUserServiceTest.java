package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static it.gdr.diary.unit.support.QueryStub.stubFindLeniently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.DataIntegrityException;
import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.Transaction;
import it.gdr.diary.config.AppUserConfig;
import it.gdr.diary.dto.app_user.DetailAppUserDTO;
import it.gdr.diary.dto.app_user.UpdateSettingsAppUserDTO;
import it.gdr.diary.exception.RecentAuthenticationRequiredException;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.security.RecentAuthentication;
import it.gdr.diary.services.AppUserService;
import it.gdr.diary.services.DiaryPageTagService;
import it.gdr.diary.services.DocumentService;
import it.gdr.diary.services.KeycloakUserService;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final String EMAIL = "frodo@shire.example";
    private static final UUID USER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

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
    @Mock
    private KeycloakUserService keycloakUserService;

    private final AppUserConfig config = new AppUserConfig();
    private final MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});

    private AppUserService service;
    private QueryStub<AppUser> users;

    @BeforeEach
    void setUp() {
        service = new AppUserService(database, jwtInspector, JsonMapper.builder().build(), config, documentService,
                new RecentAuthentication(jwtInspector, Clock.fixed(NOW, ZoneOffset.UTC)), tagService, keycloakUserService);
        lenient().when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        users = stubFindLeniently(database, AppUser.class);
    }

    private AppUser storedUser(String email) {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        user.setKeycloakId(SUBJECT);
        user.setEmail(email);
        return user;
    }

    private Document document(String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        return document;
    }

    private void givenStoredUser(AppUser user) {
        when(users.where().findOneOrEmpty()).thenReturn(Optional.of(user));
    }

    private static UpdateSettingsAppUserDTO settingsRequest(String key, Object value) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(key, value);
        return new UpdateSettingsAppUserDTO(settings);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createAppUser_tokenWithoutEmail_throwsServiceExceptionAndSavesNothing(String email) {
        when(jwtInspector.getEmail()).thenReturn(email);

        Throwable thrown = catchThrowable(() -> service.createAppUser());

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage("The token has no email address");
        verify(database, never()).save(any());
    }

    @Test
    void createAppUser_userExistingWithSameEmail_returnsItsIdWithoutWriting() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        when(users.where().findOneOrEmpty()).thenReturn(Optional.of(storedUser(EMAIL)));

        UUID id = service.createAppUser();

        assertThat(id).isEqualTo(USER_ID);
        verify(database, never()).save(any());
    }

    @Test
    void createAppUser_concurrentInsert_returnsIdOfUserCreatedByOtherRequest() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        when(users.where().findOneOrEmpty()).thenReturn(Optional.empty(), Optional.of(storedUser(EMAIL)));
        doThrow(new DuplicateKeyException("duplicate", null)).when(database).save(any(AppUser.class));

        UUID id = service.createAppUser();

        assertThat(id).isEqualTo(USER_ID);
    }

    @Test
    void createAppUser_concurrentInsertAndUserStillMissing_throwsServiceException() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        doThrow(new DuplicateKeyException("duplicate", null)).when(database).save(any(AppUser.class));

        assertThatThrownBy(() -> service.createAppUser()).isInstanceOf(ServiceException.class);
    }

    @Test
    void getAppUserBySubject_userWithAvatar_returnsEmailAndAvatarUrl() {
        AppUser user = storedUser(EMAIL);
        Document avatar = document("avatar.png");
        user.setAvatarDocument(avatar);
        givenStoredUser(user);
        when(documentService.getDocumentUrl(avatar)).thenReturn("https://storage.example/avatar");

        DetailAppUserDTO result = service.getAppUserBySubject();

        assertThat(result).extracting(DetailAppUserDTO::email, DetailAppUserDTO::avatarUrl)
                .containsExactly(EMAIL, "https://storage.example/avatar");
    }

    @Test
    void updateAppUserSettings_newOverwrittenAndNullKeys_mergesStoresAndReturnsResult() {
        AppUser user = storedUser(EMAIL);
        user.setSettings(Map.of("font", "Lora", "fontSize", 14, "obsolete", true));
        givenStoredUser(user);
        Map<String, Object> request = new HashMap<>();
        request.put("fontSize", 16);
        request.put("darkMode", true);
        request.put("obsolete", null);

        Map<String, Object> result = service.updateAppUserSettings(new UpdateSettingsAppUserDTO(request));

        Map<String, Object> expected = Map.of("font", "Lora", "fontSize", 16, "darkMode", true);
        assertThat(result).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(user.getSettings()).containsExactlyInAnyOrderEntriesOf(expected);
        verify(database).save(user);
    }

    static Stream<Arguments> storedSettingsBeforeRemovingFont() {
        return Stream.of(Arguments.of(Map.of("font", "Lora")), Arguments.of((Object) null));
    }

    @ParameterizedTest
    @MethodSource("storedSettingsBeforeRemovingFont")
    void updateAppUserSettings_nothingLeftAfterRemoval_storesNullAndReturnsEmptyMap(Map<String, Object> stored) {
        AppUser user = storedUser(EMAIL);
        user.setSettings(stored);
        givenStoredUser(user);

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("font", null));

        assertThat(result).isEmpty();
        assertThat(user.getSettings()).isNull();
    }

    @Test
    void updateAppUserSettings_overMaxSize_throwsServiceExceptionAndDoesNotSave() {
        config.setMaxSettingsSize(DataSize.ofBytes(10));
        givenStoredUser(storedUser(EMAIL));

        Throwable thrown = catchThrowable(() -> service.updateAppUserSettings(settingsRequest("font", "A very long font name")));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage("Settings too large");
        verify(database, never()).save(any());
    }

    static Stream<UpdateSettingsAppUserDTO> settingsWithUnstorableText() {
        return Stream.of(
                settingsRequest("font", "Lo\u0000ra"),
                settingsRequest("fo\u0000nt", "Lora"),
                settingsRequest("editor", Map.of("tags", List.of("a\u0000b"))),
                settingsRequest("tags", List.of("ok", "a\u0000b")));
    }

    @ParameterizedTest
    @MethodSource("settingsWithUnstorableText")
    void updateAppUserSettings_nulCharacterAnywhere_throwsServiceExceptionAndDoesNotSave(UpdateSettingsAppUserDTO request) {
        givenStoredUser(storedUser(EMAIL));

        Throwable thrown = catchThrowable(() -> service.updateAppUserSettings(request));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage("Settings contain characters that cannot be stored");
        verify(database, never()).save(any());
    }

    @Test
    void updateAppUserSettings_exactlyAtMaxSize_savesSettings() {
        config.setMaxSettingsSize(DataSize.ofBytes(JsonMapper.builder().build().writeValueAsBytes(Map.of("font", "Lora")).length));
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);

        service.updateAppUserSettings(settingsRequest("font", "Lora"));

        assertThat(user.getSettings()).containsExactlyEntriesOf(Map.of("font", "Lora"));
    }

    @Test
    void updateAppUserSettings_nestedNullValue_keepsNullInsideObject() {
        givenStoredUser(storedUser(EMAIL));
        Map<String, Object> nested = new HashMap<>();
        nested.put("size", null);

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("editor", nested));

        assertThat(result).containsEntry("editor", nested);
    }

    @Test
    void updateAppUserAvatar_previousAvatar_linksNewOneAndDeletesPreviousRecordBeforeCommitAndFileAfterIt() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);
        when(documentService.getDocumentUrl(avatar)).thenReturn("https://storage.example/new");

        String url = service.updateAppUserAvatar(file);

        assertThat(url).isEqualTo("https://storage.example/new");
        assertThat(user.getAvatarDocument()).isSameAs(avatar);
        InOrder order = inOrder(database, documentService, transaction);
        order.verify(database).save(user);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void updateAppUserAvatar_saveFails_rethrowsDeletesNewFileKeepsPreviousAndDoesNotCommit() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);
        doThrow(new IllegalStateException("db down")).when(database).save(user);

        Throwable thrown = catchThrowable(() -> service.updateAppUserAvatar(file));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(documentService).deleteDocumentFile(avatar);
        verify(documentService, never()).deleteDocumentFile(previous);
        verify(transaction, never()).commit();
    }

    @Test
    void deleteAppUserAvatar_withAvatar_unlinksItAndDeletesRecordThenCommitsThenDeletesFile() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUserAvatar();

        assertThat(user.getAvatarDocument()).isNull();
        InOrder order = inOrder(database, documentService, transaction);
        order.verify(database).save(user);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void deleteAppUserAvatar_withoutAvatar_commitsAndDeletesNoFile() {
        givenStoredUser(storedUser(EMAIL));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUserAvatar();

        verify(transaction).commit();
        verify(documentService, never()).deleteDocumentFile(any(Document.class));
    }

    private void givenRecentAuthentication() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minusSeconds(30)));
    }

    private GameCharacter ownedCharacter(Document avatar) {
        GameCharacter character = new GameCharacter();
        character.setId(UUID.randomUUID());
        character.setAvatarDocument(avatar);
        return character;
    }

    private Diary diaryWithCover(Document cover) {
        Diary diary = new Diary();
        diary.setId(UUID.randomUUID());
        diary.setCoverDocument(cover);
        return diary;
    }

    private QueryStub<GameCharacter> givenCharacters(List<GameCharacter> characters) {
        QueryStub<GameCharacter> stub = stubFind(database, GameCharacter.class);
        when(stub.where().findList()).thenReturn(characters);
        return stub;
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

    @Test
    void deleteAppUser_recentLogin_deletesKeycloakUserBeforeBackendData() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(keycloakUserService, database);
        order.verify(keycloakUserService).deleteKeycloakUser(SUBJECT);
        order.verify(database).beginTransaction();
    }

    @Test
    void deleteAppUser_userMissingInBackend_deletesKeycloakUserAndNothingElse() {
        givenRecentAuthentication();

        service.deleteAppUser();

        verify(keycloakUserService).deleteKeycloakUser(SUBJECT);
        verify(database, never()).beginTransaction();
        verifyNoInteractions(documentService);
    }

    @Test
    void deleteAppUser_keycloakFails_propagatesTheExceptionAndTouchesNoData() {
        givenRecentAuthentication();
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "down")).when(keycloakUserService).deleteKeycloakUser(SUBJECT);

        Throwable thrown = catchThrowable(() -> service.deleteAppUser());

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verifyNoInteractions(database, documentService);
    }

    static Stream<Arguments> staleAuthTimes() {
        return Stream.of(
                Arguments.of(Optional.empty()),
                Arguments.of(Optional.of(NOW.minus(Duration.ofMinutes(6)))),
                Arguments.of(Optional.of(NOW.minus(Duration.ofMinutes(5)).minusSeconds(1))),
                Arguments.of(Optional.of(NOW.minus(Duration.ofHours(1)))));
    }

    @ParameterizedTest
    @MethodSource("staleAuthTimes")
    void deleteAppUser_authTimeMissingOrTooOld_throwsRecentAuthenticationRequiredAndTouchesNothing(Optional<Instant> authTime) {
        when(jwtInspector.getAuthTime()).thenReturn(authTime);

        Throwable thrown = catchThrowable(() -> service.deleteAppUser());

        assertThat(thrown).isInstanceOf(RecentAuthenticationRequiredException.class);
        verifyNoInteractions(keycloakUserService, database, documentService);
    }

    @Test
    void deleteAppUser_authTimeTooOld_reportsConfiguredMaxAgeInSeconds() {
        config.setDeleteMaxAuthAge(Duration.ofMinutes(10));
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(11))));

        assertThatThrownBy(() -> service.deleteAppUser())
                .isInstanceOfSatisfying(RecentAuthenticationRequiredException.class,
                        e -> assertThat(e.getMaxAgeSeconds()).isEqualTo(600));
    }

    @ParameterizedTest
    @CsvSource({"30,20", "5,5"})
    void deleteAppUser_authTimeWithinOrExactlyAtMaxAge_deletesUser(long maxAgeMinutes, long ageMinutes) {
        config.setDeleteMaxAuthAge(Duration.ofMinutes(maxAgeMinutes));
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(ageMinutes))));
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(database).delete(user);
    }

    @Test
    void deleteAppUser_allKindsOfDocuments_deletesRecordsBeforeCommitAndFilesAfterIt() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        Document userAvatar = document("me.png");
        Document characterAvatar = document("aragorn.png");
        Document cover = document("cover.png");
        Document pdf = document("sheet.pdf");
        user.setAvatarDocument(userAvatar);
        givenStoredUser(user);
        givenCharacters(List.of(ownedCharacter(characterAvatar)));
        givenSheets(List.of(sheet(pdf)));
        givenDiaries(List.of(diaryWithCover(cover)));
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(userAvatar);
        order.verify(documentService).deleteDocumentRecord(pdf);
        order.verify(documentService).deleteDocumentRecord(cover);
        order.verify(documentService).deleteDocumentRecord(characterAvatar);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(userAvatar);
        order.verify(documentService).deleteDocumentFile(pdf);
        order.verify(documentService).deleteDocumentFile(cover);
        order.verify(documentService).deleteDocumentFile(characterAvatar);
    }

    @Test
    void deleteAppUser_deleteFails_doesNotCommitAndDeletesNoFile() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        user.setAvatarDocument(document("me.png"));
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).delete(user);

        catchThrowable(() -> service.deleteAppUser());

        verify(transaction, never()).commit();
        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteAppUser_characterCreatedMeanwhile_throwsConflictAndDeletesNoFile() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        user.setAvatarDocument(document("me.png"));
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new DataIntegrityException("still referenced from table game_character",
                new SQLException("violates foreign key constraint", "23503"))).when(database).delete(user);

        Throwable thrown = catchThrowable(() -> service.deleteAppUser());

        assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                        .containsExactly(HttpStatus.CONFLICT, "The account changed while it was being deleted, try again"));
        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteAppUser_otherDatabaseError_rethrowsIt() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(database).delete(user);

        assertThatThrownBy(() -> service.deleteAppUser()).isSameAs(failure);
    }
}

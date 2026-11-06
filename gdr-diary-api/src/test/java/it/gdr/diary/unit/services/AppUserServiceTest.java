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
import it.gdr.diary.services.AppUserService;
import it.gdr.diary.services.DiaryPageTagService;
import it.gdr.diary.services.DocumentService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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

    private final AppUserConfig config = new AppUserConfig();
    private final MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1});

    private AppUserService service;
    private QueryStub<AppUser> users;

    @BeforeEach
    void setUp() {
        service = new AppUserService(database, jwtInspector, JsonMapper.builder().build(), config, documentService,
                Clock.fixed(NOW, ZoneOffset.UTC), tagService);
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

    private UpdateSettingsAppUserDTO settingsRequest(String key, Object value) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(key, value);
        return new UpdateSettingsAppUserDTO(settings);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createAppUser_tokenWithoutEmail_throwsServiceException(String email) {
        when(jwtInspector.getEmail()).thenReturn(email);

        assertThatThrownBy(() -> service.createAppUser())
                .isInstanceOf(ServiceException.class)
                .hasMessage("The token has no email address");
    }

    @Test
    void createAppUser_tokenWithoutEmail_savesNothing() {
        when(jwtInspector.getEmail()).thenReturn(null);

        catchThrowable(() -> service.createAppUser());

        verify(database, never()).save(any());
    }

    @Test
    void createAppUser_userMissing_savesUserWithSubjectAndEmail() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);

        service.createAppUser();

        verify(database).save(saved.capture());
        assertThat(saved.getValue()).extracting(AppUser::getKeycloakId, AppUser::getEmail).containsExactly(SUBJECT, EMAIL);
    }

    @Test
    void createAppUser_userMissing_returnsIdOfSavedUser() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        doAnswer(invocation -> {
            invocation.<AppUser>getArgument(0).setId(USER_ID);
            return null;
        }).when(database).save(any(AppUser.class));

        UUID id = service.createAppUser();

        assertThat(id).isEqualTo(USER_ID);
    }

    @Test
    void createAppUser_userExistingWithSameEmail_doesNotSave() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        givenStoredUser(storedUser(EMAIL));

        service.createAppUser();

        verify(database, never()).save(any());
    }

    @Test
    void createAppUser_userExistingWithDifferentEmail_savesEmailFromToken() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        AppUser user = storedUser("old@shire.example");
        givenStoredUser(user);

        service.createAppUser();

        verify(database).save(user);
        assertThat(user.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    void createAppUser_userExisting_returnsExistingId() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        givenStoredUser(storedUser(EMAIL));

        UUID id = service.createAppUser();

        assertThat(id).isEqualTo(USER_ID);
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
    void createAppUser_anyRequest_looksUpUserBySubject() {
        when(jwtInspector.getEmail()).thenReturn(EMAIL);
        givenStoredUser(storedUser(EMAIL));

        service.createAppUser();

        verify(users.where()).eq("keycloakId", SUBJECT);
    }

    @Test
    void getAppUserBySubject_userMissing_throwsNotFound() {
        assertThatThrownBy(() -> service.getAppUserBySubject())
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAppUserBySubject_userWithAvatar_returnsAvatarUrl() {
        AppUser user = storedUser(EMAIL);
        Document avatar = document("avatar.png");
        user.setAvatarDocument(avatar);
        givenStoredUser(user);
        when(documentService.getDocumentUrl(avatar)).thenReturn("https://storage.example/avatar");

        DetailAppUserDTO result = service.getAppUserBySubject();

        assertThat(result.avatarUrl()).isEqualTo("https://storage.example/avatar");
    }

    @Test
    void getAppUserBySubject_userExisting_returnsEmail() {
        givenStoredUser(storedUser(EMAIL));

        DetailAppUserDTO result = service.getAppUserBySubject();

        assertThat(result.email()).isEqualTo(EMAIL);
    }

    @Test
    void updateAppUserSettings_newKey_mergesWithStoredSettings() {
        AppUser user = storedUser(EMAIL);
        user.setSettings(Map.of("font", "Lora"));
        givenStoredUser(user);

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("darkMode", true));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("font", "Lora", "darkMode", true));
    }

    @Test
    void updateAppUserSettings_existingKey_overwritesValue() {
        AppUser user = storedUser(EMAIL);
        user.setSettings(Map.of("fontSize", 14));
        givenStoredUser(user);

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("fontSize", 16));

        assertThat(result).containsExactlyEntriesOf(Map.of("fontSize", 16));
    }

    @Test
    void updateAppUserSettings_nullValue_removesKey() {
        AppUser user = storedUser(EMAIL);
        user.setSettings(Map.of("font", "Lora", "darkMode", true));
        givenStoredUser(user);

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("font", null));

        assertThat(result).containsExactlyEntriesOf(Map.of("darkMode", true));
    }

    @Test
    void updateAppUserSettings_noStoredSettings_storesRequestedSettings() {
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);

        service.updateAppUserSettings(settingsRequest("font", "Lora"));

        verify(database).save(user);
        assertThat(user.getSettings()).containsExactlyEntriesOf(Map.of("font", "Lora"));
    }

    @Test
    void updateAppUserSettings_lastKeyRemoved_storesNull() {
        AppUser user = storedUser(EMAIL);
        user.setSettings(Map.of("font", "Lora"));
        givenStoredUser(user);

        service.updateAppUserSettings(settingsRequest("font", null));

        assertThat(user.getSettings()).isNull();
    }

    @Test
    void updateAppUserSettings_lastKeyRemoved_returnsEmptyMap() {
        AppUser user = storedUser(EMAIL);
        user.setSettings(Map.of("font", "Lora"));
        givenStoredUser(user);

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("font", null));

        assertThat(result).isEmpty();
    }

    @Test
    void updateAppUserSettings_overMaxSize_throwsServiceException() {
        config.setMaxSettingsSize(DataSize.ofBytes(10));
        givenStoredUser(storedUser(EMAIL));

        assertThatThrownBy(() -> service.updateAppUserSettings(settingsRequest("font", "A very long font name")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Settings too large");
    }

    @Test
    void updateAppUserSettings_nulCharacterInNestedValue_throwsServiceException() {
        givenStoredUser(storedUser(EMAIL));

        assertThatThrownBy(() -> service.updateAppUserSettings(settingsRequest("editor", Map.of("tags", List.of("a\u0000b")))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Settings contain characters that cannot be stored");
    }

    @Test
    void updateAppUserSettings_nulCharacterInKey_throwsServiceException() {
        givenStoredUser(storedUser(EMAIL));

        assertThatThrownBy(() -> service.updateAppUserSettings(settingsRequest("fo\u0000nt", "Lora")))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Settings contain characters that cannot be stored");
    }

    @Test
    void updateAppUserSettings_nulCharacter_doesNotSave() {
        givenStoredUser(storedUser(EMAIL));

        catchThrowable(() -> service.updateAppUserSettings(settingsRequest("font", "Lo\u0000ra")));

        verify(database, never()).save(any());
    }

    @Test
    void updateAppUserSettings_overMaxSize_doesNotSave() {
        config.setMaxSettingsSize(DataSize.ofBytes(10));
        givenStoredUser(storedUser(EMAIL));

        catchThrowable(() -> service.updateAppUserSettings(settingsRequest("font", "A very long font name")));

        verify(database, never()).save(any());
    }

    @Test
    void updateAppUserSettings_userMissing_throwsNotFound() {
        UpdateSettingsAppUserDTO request = settingsRequest("font", "Lora");

        assertThatThrownBy(() -> service.updateAppUserSettings(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateAppUserAvatar_validFile_returnsUrlOfNewAvatar() {
        givenStoredUser(storedUser(EMAIL));
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);
        when(documentService.getDocumentUrl(avatar)).thenReturn("https://storage.example/new");

        String url = service.updateAppUserAvatar(file);

        assertThat(url).isEqualTo("https://storage.example/new");
    }

    @Test
    void updateAppUserAvatar_validFile_linksNewAvatarToUser() {
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);

        service.updateAppUserAvatar(file);

        verify(database).save(user);
        assertThat(user.getAvatarDocument()).isSameAs(avatar);
    }

    @Test
    void updateAppUserAvatar_previousAvatar_deletesPreviousRecordInsideTransaction() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));

        service.updateAppUserAvatar(file);

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
    }

    @Test
    void updateAppUserAvatar_previousAvatar_deletesPreviousFileAfterCommit() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));

        service.updateAppUserAvatar(file);

        InOrder order = inOrder(transaction, documentService);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void updateAppUserAvatar_saveFails_deletesNewFile() {
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        Document avatar = document("avatar.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(avatar);
        doThrow(new IllegalStateException("db down")).when(database).save(user);

        catchThrowable(() -> service.updateAppUserAvatar(file));

        verify(documentService).deleteDocumentFile(avatar);
    }

    @Test
    void updateAppUserAvatar_saveFails_rethrowsException() {
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));
        doThrow(new IllegalStateException("db down")).when(database).save(user);

        assertThatThrownBy(() -> service.updateAppUserAvatar(file)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateAppUserAvatar_saveFails_doesNotCommit() {
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));
        doThrow(new IllegalStateException("db down")).when(database).save(user);

        catchThrowable(() -> service.updateAppUserAvatar(file));

        verify(transaction, never()).commit();
    }

    @Test
    void updateAppUserAvatar_saveFails_keepsPreviousFile() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file)).thenReturn(document("avatar.png"));
        doThrow(new IllegalStateException("db down")).when(database).save(user);

        catchThrowable(() -> service.updateAppUserAvatar(file));

        verify(documentService, never()).deleteDocumentFile(previous);
    }

    @Test
    void updateAppUserAvatar_userMissing_doesNotUploadFile() {
        catchThrowable(() -> service.updateAppUserAvatar(file));

        verify(documentService, never()).createImageDocument(any());
    }

    @Test
    void deleteAppUserAvatar_withAvatar_unlinksAvatarFromUser() {
        AppUser user = storedUser(EMAIL);
        user.setAvatarDocument(document("old.png"));
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUserAvatar();

        verify(database).save(user);
        assertThat(user.getAvatarDocument()).isNull();
    }

    @Test
    void deleteAppUserAvatar_withAvatar_deletesRecordThenCommitsThenDeletesFile() {
        AppUser user = storedUser(EMAIL);
        Document previous = document("old.png");
        user.setAvatarDocument(previous);
        givenStoredUser(user);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUserAvatar();

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(previous);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(previous);
    }

    @Test
    void deleteAppUserAvatar_userMissing_throwsNotFound() {
        assertThatThrownBy(() -> service.deleteAppUserAvatar())
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
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
    void deleteAppUser_userMissing_doesNotOpenTransaction() {
        givenRecentAuthentication();

        service.deleteAppUser();

        verify(database, never()).beginTransaction();
    }

    @Test
    void deleteAppUser_userMissing_deletesNoFile() {
        givenRecentAuthentication();

        service.deleteAppUser();

        verifyNoInteractions(documentService);
    }

    @Test
    void deleteAppUser_userWithoutCharacters_deletesUserThenCommits() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(database, transaction);
        order.verify(database).delete(user);
        order.verify(transaction).commit();
    }

    @Test
    void deleteAppUser_userWithoutCharacters_doesNotLookForDiaries() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(database, never()).find(Diary.class);
    }

    @Test
    void deleteAppUser_anyUser_selectsOnlyCharactersOfUser() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        QueryStub<GameCharacter> characters = givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(characters.where()).eq("owner.id", USER_ID);
    }

    @Test
    void deleteAppUser_charactersWithDiaries_deletesChildrenBeforeParents() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        List<GameCharacter> characters = List.of(ownedCharacter(null));
        List<Diary> diaries = List.of(diaryWithCover(null));
        givenCharacters(characters);
        givenSheets(List.of());
        givenDiaries(diaries);
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(pages.where(), database, transaction);
        order.verify(pages.where()).delete();
        order.verify(database).deleteAll(diaries);
        order.verify(database).deleteAll(characters);
        order.verify(database).delete(user);
        order.verify(transaction).commit();
    }

    @Test
    void deleteAppUser_charactersWithDiaries_deletesPagesOfAllDiaries() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        Diary first = diaryWithCover(null);
        Diary second = diaryWithCover(null);
        givenSheets(List.of());
        givenDiaries(List.of(first, second));
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(pages.where()).in("diary.id", List.of(first.getId(), second.getId()));
    }

    @Test
    void deleteAppUser_charactersWithDiaries_deletesTagsOfAllDiariesBeforePages() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        Diary first = diaryWithCover(null);
        Diary second = diaryWithCover(null);
        givenSheets(List.of());
        givenDiaries(List.of(first, second));
        QueryStub<DiaryPage> pages = stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(tagService, pages.where());
        order.verify(tagService).deleteDiaryPageTagsByDiaries(List.of(first.getId(), second.getId()));
        order.verify(pages.where()).delete();
    }

    @Test
    void deleteAppUser_charactersWithoutDiaries_deletesNoTags() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(tagService, never()).deleteDiaryPageTagsByDiaries(any());
    }

    @Test
    void deleteAppUser_charactersWithoutDiaries_doesNotDeletePages() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(database, never()).find(DiaryPage.class);
    }

    @Test
    void deleteAppUser_allImages_deletesEveryRecordBeforeCommit() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        Document userAvatar = document("me.png");
        Document characterAvatar = document("aragorn.png");
        Document cover = document("cover.png");
        user.setAvatarDocument(userAvatar);
        givenStoredUser(user);
        givenCharacters(List.of(ownedCharacter(characterAvatar)));
        givenSheets(List.of());
        givenDiaries(List.of(diaryWithCover(cover)));
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(userAvatar);
        order.verify(documentService).deleteDocumentRecord(cover);
        order.verify(documentService).deleteDocumentRecord(characterAvatar);
        order.verify(transaction).commit();
    }

    @Test
    void deleteAppUser_allImages_deletesEveryFileAfterCommit() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        Document userAvatar = document("me.png");
        Document characterAvatar = document("aragorn.png");
        Document cover = document("cover.png");
        user.setAvatarDocument(userAvatar);
        givenStoredUser(user);
        givenCharacters(List.of(ownedCharacter(characterAvatar)));
        givenSheets(List.of());
        givenDiaries(List.of(diaryWithCover(cover)));
        stubFind(database, DiaryPage.class);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(transaction, documentService);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(userAvatar);
        order.verify(documentService).deleteDocumentFile(cover);
        order.verify(documentService).deleteDocumentFile(characterAvatar);
    }

    @Test
    void deleteAppUser_deleteFails_deletesNoFile() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        user.setAvatarDocument(document("me.png"));
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).delete(user);

        catchThrowable(() -> service.deleteAppUser());

        verify(documentService, never()).deleteDocumentFile(any());
    }

    @Test
    void deleteAppUser_deleteFails_doesNotCommit() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).delete(user);

        catchThrowable(() -> service.deleteAppUser());

        verify(transaction, never()).commit();
    }

    @Test
    void deleteAppUser_noAuthTimeInToken_throwsRecentAuthenticationRequired() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAppUser()).isInstanceOf(RecentAuthenticationRequiredException.class);
    }

    @Test
    void deleteAppUser_authTimeOlderThanMaxAge_throwsRecentAuthenticationRequired() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(6))));

        assertThatThrownBy(() -> service.deleteAppUser()).isInstanceOf(RecentAuthenticationRequiredException.class);
    }

    @Test
    void deleteAppUser_authTimeTooOld_reportsConfiguredMaxAgeInSeconds() {
        config.setDeleteMaxAuthAge(Duration.ofMinutes(10));
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(11))));

        assertThatThrownBy(() -> service.deleteAppUser())
                .isInstanceOfSatisfying(RecentAuthenticationRequiredException.class,
                        e -> assertThat(e.getMaxAgeSeconds()).isEqualTo(600));
    }

    @Test
    void deleteAppUser_authTimeTooOld_touchesNoData() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        catchThrowable(() -> service.deleteAppUser());

        verifyNoInteractions(database, documentService);
    }

    @Test
    void deleteAppUser_authTimeWithinConfiguredMaxAge_deletesUser() {
        config.setDeleteMaxAuthAge(Duration.ofMinutes(30));
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(20))));
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(database).delete(user);
    }

    @Test
    void deleteAppUserAvatar_withoutAvatar_commitsTransaction() {
        givenStoredUser(storedUser(EMAIL));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUserAvatar();

        verify(transaction).commit();
    }

    @Test
    void deleteAppUserAvatar_withoutAvatar_deletesNoFile() {
        givenStoredUser(storedUser(EMAIL));
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUserAvatar();

        verify(documentService, never()).deleteDocumentFile(any(Document.class));
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
    void updateAppUserSettings_removeMissingKeyOnEmptySettings_storesNull() {
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);

        service.updateAppUserSettings(settingsRequest("font", null));

        assertThat(user.getSettings()).isNull();
    }

    @Test
    void updateAppUserSettings_removeMissingKeyOnEmptySettings_returnsEmptyMap() {
        givenStoredUser(storedUser(EMAIL));

        Map<String, Object> result = service.updateAppUserSettings(settingsRequest("font", null));

        assertThat(result).isEmpty();
    }

    @Test
    void updateAppUserSettings_nulCharacterInListValue_throwsServiceException() {
        givenStoredUser(storedUser(EMAIL));

        assertThatThrownBy(() -> service.updateAppUserSettings(settingsRequest("tags", List.of("ok", "a\u0000b"))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Settings contain characters that cannot be stored");
    }

    @Test
    void deleteAppUser_authTimeExactlyAtMaxAge_deletesUser() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(5))));
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(database).delete(user);
    }

    @Test
    void deleteAppUser_authTimeOneSecondBeyondMaxAge_throwsRecentAuthenticationRequired() {
        when(jwtInspector.getAuthTime()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(5)).minusSeconds(1)));

        assertThatThrownBy(() -> service.deleteAppUser()).isInstanceOf(RecentAuthenticationRequiredException.class);
    }

    @Test
    void deleteAppUser_characterCreatedMeanwhile_throwsConflict() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new DataIntegrityException("still referenced from table game_character",
                new SQLException("violates foreign key constraint", "23503"))).when(database).delete(user);

        assertThatThrownBy(() -> service.deleteAppUser())
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e).extracting(ResponseStatusException::getStatusCode, ResponseStatusException::getReason)
                                .containsExactly(HttpStatus.CONFLICT, "The account changed while it was being deleted, try again"));
    }

    @Test
    void deleteAppUser_characterCreatedMeanwhile_deletesNoFile() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        user.setAvatarDocument(document("me.png"));
        givenStoredUser(user);
        givenCharacters(List.of());
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new DataIntegrityException("still referenced from table game_character",
                new SQLException("violates foreign key constraint", "23503"))).when(database).delete(user);

        catchThrowable(() -> service.deleteAppUser());

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

    @Test
    void deleteAppUser_charactersWithSheets_selectsOnlySheetsOfUserCharacters() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        List<GameCharacter> characters = List.of(ownedCharacter(null));
        givenCharacters(characters);
        QueryStub<CharacterSheet> sheets = givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(sheets.where()).in("character.id", List.of(characters.get(0).getId()));
    }

    @Test
    void deleteAppUser_charactersWithSheets_deletesSheetsBeforeCharacters() {
        givenRecentAuthentication();
        AppUser user = storedUser(EMAIL);
        givenStoredUser(user);
        List<GameCharacter> characters = List.of(ownedCharacter(null));
        givenCharacters(characters);
        List<CharacterSheet> sheets = List.of(sheet(document("sheet.pdf")));
        givenSheets(sheets);
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(database, transaction);
        order.verify(database).deleteAll(sheets);
        order.verify(database).deleteAll(characters);
        order.verify(database).delete(user);
        order.verify(transaction).commit();
    }

    @Test
    void deleteAppUser_charactersWithoutSheets_doesNotDeleteSheets() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        givenSheets(List.of());
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        verify(database, never()).deleteAll(List.<CharacterSheet>of());
    }

    @Test
    void deleteAppUser_charactersWithSheets_deletesSheetRecordsBeforeCommit() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        Document pdf = document("sheet.pdf");
        givenSheets(List.of(sheet(pdf)));
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(documentService, transaction);
        order.verify(documentService).deleteDocumentRecord(pdf);
        order.verify(transaction).commit();
    }

    @Test
    void deleteAppUser_charactersWithSheets_deletesSheetFilesAfterCommit() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        Document pdf = document("sheet.pdf");
        givenSheets(List.of(sheet(pdf)));
        givenDiaries(List.of());
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteAppUser();

        InOrder order = inOrder(transaction, documentService);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(pdf);
    }

    @Test
    void deleteAppUser_sheetsDeleteFails_deletesNoFile() {
        givenRecentAuthentication();
        givenStoredUser(storedUser(EMAIL));
        givenCharacters(List.of(ownedCharacter(null)));
        List<CharacterSheet> sheets = List.of(sheet(document("sheet.pdf")));
        givenSheets(sheets);
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new IllegalStateException("db down")).when(database).deleteAll(sheets);

        catchThrowable(() -> service.deleteAppUser());

        verify(documentService, never()).deleteDocumentFile(any());
    }
}

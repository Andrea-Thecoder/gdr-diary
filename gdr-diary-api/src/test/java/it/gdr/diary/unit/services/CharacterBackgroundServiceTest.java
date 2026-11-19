package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.Transaction;
import it.gdr.diary.config.CharacterBackgroundConfig;
import it.gdr.diary.dto.character_background.UpdateCharacterBackgroundDTO;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.CharacterBackground;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.CharacterBackgroundService;
import it.gdr.diary.services.OwnedResources;
import it.gdr.diary.services.DiaryPageContentValidator;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CharacterBackgroundServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final Map<String, Object> CONTENT = document("Born in Erebor");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;
    @Mock
    private DiaryPageContentValidator contentValidator;
    @Mock
    private Transaction transaction;

    private final CharacterBackgroundConfig config = new CharacterBackgroundConfig();

    private CharacterBackgroundService service;

    @BeforeEach
    void setUp() {
        service = new CharacterBackgroundService(database, jwtInspector, JsonMapper.builder().build(), config, contentValidator, new OwnedResources(database, jwtInspector));
    }

    private static Map<String, Object> document(String text) {
        return Map.of("type", "doc", "content", List.of(
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text)))));
    }

    private GameCharacter character() {
        GameCharacter character = new GameCharacter();
        character.setId(CHARACTER_ID);
        return character;
    }

    private GameCharacter gameMaster() {
        GameCharacter character = character();
        character.setKind(GameCharacterKind.GAME_MASTER);
        return character;
    }

    private CharacterBackground background(long version) {
        CharacterBackground background = new CharacterBackground();
        background.setId(UUID.randomUUID());
        background.setCharacter(character());
        background.setContent(CONTENT);
        background.setPreview("Born in Erebor");
        background.setTextLength(14);
        background.setVersion(version);
        return background;
    }

    private QueryStub<GameCharacter> givenOwnedCharacter(GameCharacter character) {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        QueryStub<GameCharacter> characters = stubFind(database, GameCharacter.class);
        when(characters.where().findOne()).thenReturn(character);
        return characters;
    }

    private QueryStub<CharacterBackground> givenBackground(CharacterBackground background) {
        QueryStub<CharacterBackground> backgrounds = stubFind(database, CharacterBackground.class);
        when(backgrounds.where().findOne()).thenReturn(background);
        return backgrounds;
    }

    private QueryStub<CharacterBackground> givenWritable(CharacterBackground existing) {
        when(database.beginTransaction()).thenReturn(transaction);
        givenOwnedCharacter(character());
        return givenBackground(existing);
    }

    @Test
    void getCharacterBackground_gameMaster_throwsServiceException() {
        givenOwnedCharacter(gameMaster());

        assertThatThrownBy(() -> service.getCharacterBackground(CHARACTER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessage("A game master has no background");
    }

    @Test
    void updateCharacterBackground_contentOverMaxSize_throwsServiceExceptionWithoutValidatingIt() {
        givenOwnedCharacter(character());
        config.setMaxContentSize(DataSize.ofBytes(10));
        UpdateCharacterBackgroundDTO request = new UpdateCharacterBackgroundDTO(CONTENT, 0L);

        Throwable thrown = catchThrowable(() -> service.updateCharacterBackground(CHARACTER_ID, request));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage("Background content too large");
        verify(contentValidator, never()).validate(any());
    }

    @Test
    void updateCharacterBackground_textOverMaxLength_throwsServiceExceptionAndSavesNothing() {
        givenOwnedCharacter(character());
        config.setMaxTextLength(13);
        UpdateCharacterBackgroundDTO request = new UpdateCharacterBackgroundDTO(CONTENT, 0L);

        Throwable thrown = catchThrowable(() -> service.updateCharacterBackground(CHARACTER_ID, request));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage("The background is too long (maximum 13 characters)");
        verify(database, never()).save(any(CharacterBackground.class));
    }

    @Test
    void updateCharacterBackground_anyRequest_locksTheCharacterInsideTheTransactionBeforeReadingTheBackground() {
        when(database.beginTransaction()).thenReturn(transaction);
        QueryStub<GameCharacter> characters = givenOwnedCharacter(character());
        QueryStub<CharacterBackground> backgrounds = givenBackground(null);

        service.updateCharacterBackground(CHARACTER_ID, new UpdateCharacterBackgroundDTO(CONTENT, 0L));

        InOrder order = inOrder(database, characters.query(), backgrounds.where());
        order.verify(database).beginTransaction();
        order.verify(characters.query()).forUpdate();
        order.verify(backgrounds.where()).findOne();
    }

    @Test
    void updateCharacterBackground_characterDeletedBeforeLock_throwsNotFound() {
        QueryStub<GameCharacter> characters = givenOwnedCharacter(character());
        when(characters.where().findOne()).thenReturn(character(), (GameCharacter) null);
        when(database.beginTransaction()).thenReturn(transaction);
        UpdateCharacterBackgroundDTO request = new UpdateCharacterBackgroundDTO(CONTENT, 0L);

        assertThatThrownBy(() -> service.updateCharacterBackground(CHARACTER_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateCharacterBackground_duplicateInsert_throwsConflict() {
        givenWritable(null);
        doThrow(new DuplicateKeyException("duplicate", null)).when(database).save(any(CharacterBackground.class));
        UpdateCharacterBackgroundDTO request = new UpdateCharacterBackgroundDTO(CONTENT, 0L);

        assertThatThrownBy(() -> service.updateCharacterBackground(CHARACTER_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @ParameterizedTest
    @CsvSource({"23505,CONFLICT", "40P01,CONFLICT", "23503,NOT_FOUND"})
    void updateCharacterBackground_sqlStateOnCommit_mapsToHttpStatus(String sqlState, HttpStatus status) {
        givenWritable(null);
        doThrow(new RollbackException("commit failed", new SQLException("failure", sqlState))).when(transaction).commit();
        UpdateCharacterBackgroundDTO request = new UpdateCharacterBackgroundDTO(CONTENT, 0L);

        assertThatThrownBy(() -> service.updateCharacterBackground(CHARACTER_ID, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void updateCharacterBackground_otherPersistenceFailure_isRethrown() {
        givenWritable(null);
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("gone", "08006"));
        doThrow(failure).when(transaction).commit();
        UpdateCharacterBackgroundDTO request = new UpdateCharacterBackgroundDTO(CONTENT, 0L);

        assertThatThrownBy(() -> service.updateCharacterBackground(CHARACTER_ID, request)).isSameAs(failure);
    }

    @Test
    void findCharacterBackgroundPreview_emptyPreview_returnsNull() {
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
        CharacterBackground empty = background(1L);
        empty.setPreview("");
        givenBackground(empty);

        assertThat(service.findCharacterBackgroundPreview(CHARACTER_ID)).isNull();
    }
}

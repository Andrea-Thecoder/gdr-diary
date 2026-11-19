package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.Transaction;
import it.gdr.diary.config.CharacterBackgroundConfig;
import it.gdr.diary.dto.character_background.DetailCharacterBackgroundDTO;
import it.gdr.diary.dto.character_background.UpdateCharacterBackgroundDTO;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.exception.SqlStates;
import it.gdr.diary.model.CharacterBackground;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CharacterBackgroundService {

    private static final long VERSION_WITHOUT_BACKGROUND = 0L;
    private static final Set<String> CONCURRENT_INSERT_STATES = Set.of(SqlStates.UNIQUE_VIOLATION, SqlStates.DEADLOCK);

    private final Database database;
    private final JwtInspector jwtInspector;
    private final ObjectMapper objectMapper;
    private final CharacterBackgroundConfig config;
    private final DiaryPageContentValidator contentValidator;
    private final OwnedResources ownedResources;

    public DetailCharacterBackgroundDTO getCharacterBackground(UUID characterId) {
        GameCharacter character = getPlayerCharacterByIdAndOwner(characterId);
        CharacterBackground background = findBackgroundByCharacter(character.getId());
        return background == null ? DetailCharacterBackgroundDTO.empty() : DetailCharacterBackgroundDTO.from(background);
    }

    public String findCharacterBackgroundPreview(UUID characterId) {
        CharacterBackground background = database.find(CharacterBackground.class)
                .setLabel("findCharacterBackgroundPreview")
                .select("preview")
                .where().eq("character.id", characterId).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (background == null || StringUtils.isBlank(background.getPreview())) {
            return null;
        }
        return background.getPreview();
    }

    public DetailCharacterBackgroundDTO updateCharacterBackground(UUID characterId, UpdateCharacterBackgroundDTO request) {
        getPlayerCharacterByIdAndOwner(characterId);
        validateContent(request.content());
        BackgroundText text = BackgroundText.of(request.content());
        if (text.length() > config.getMaxTextLength()) {
            throw new ServiceException("The background is too long (maximum " + config.getMaxTextLength() + " characters)");
        }
        CharacterBackground background;
        try (Transaction transaction = database.beginTransaction()) {
            GameCharacter character = lockGameCharacter(characterId);
            background = findBackgroundByCharacter(characterId);
            long currentVersion = background == null ? VERSION_WITHOUT_BACKGROUND : background.getVersion();
            if (!Objects.equals(currentVersion, request.version())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The background was modified elsewhere, reload it");
            }
            if (background == null) {
                background = new CharacterBackground();
                background.setCharacter(character);
            }
            background.setContent(request.content());
            background.setPreview(text.preview());
            background.setTextLength(text.length());
            database.save(background);
            transaction.commit();
        } catch (PersistenceException e) {
            if (e instanceof DuplicateKeyException || SqlStates.hasSqlState(e, CONCURRENT_INSERT_STATES)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The background was modified elsewhere, reload it");
            }
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
            }
            throw e;
        }
        return DetailCharacterBackgroundDTO.from(background);
    }

    private void validateContent(Map<String, Object> content) {
        if (objectMapper.writeValueAsBytes(content).length > config.getMaxContentSize().toBytes()) {
            throw new ServiceException("Background content too large");
        }
        contentValidator.validate(content);
    }

    private GameCharacter lockGameCharacter(UUID id) {
        GameCharacter locked = database.find(GameCharacter.class)
                .setLabel("lockGameCharacter")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
        return locked;
    }

    private CharacterBackground findBackgroundByCharacter(UUID characterId) {
        return database.find(CharacterBackground.class)
                .setLabel("findCharacterBackgroundByCharacter")
                .where().eq("character.id", characterId).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
    }

    private GameCharacter getPlayerCharacterByIdAndOwner(UUID id) {
        GameCharacter character = ownedResources.getGameCharacterByIdAndOwner(id);
        if (character.getKind() == GameCharacterKind.GAME_MASTER) {
            throw new ServiceException("A game master has no background");
        }
        return character;
    }
}

package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.game_character.BaseGameCharacterDTO;
import it.gdr.diary.dto.game_character.DetailGameCharacterDTO;
import it.gdr.diary.dto.game_character.InsertGameCharacterDTO;
import it.gdr.diary.dto.game_character.UpdateGameCharacterDTO;
import it.gdr.diary.dto.search.GameCharacterSearchRequest;
import it.gdr.diary.exception.SqlStates;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameCharacterService {

    private final Database database;
    private final JwtInspector jwtInspector;
    private final DocumentService documentService;

    public PagedResultDTO<BaseGameCharacterDTO> findAllGameCharacters(GameCharacterSearchRequest request) {
        ExpressionList<GameCharacter> query = database.find(GameCharacter.class)
                .setLabel("findAllGameCharacters")
                .fetch("avatarDocument")
                .where()
                .eq("owner.keycloakId", jwtInspector.getSubject());
        request.applyFilter(query);
        request.applySortAndPagination(query, "name");
        return PagedResultDTO.of(query.findPagedList(),
                character -> BaseGameCharacterDTO.from(character,
                        documentService.getDocumentUrl(character.getAvatarDocument())));
    }

    public DetailGameCharacterDTO getGameCharacterById(UUID id) {
        return toDetailGameCharacterDTO(getGameCharacterByIdAndOwner(id));
    }

    public DetailGameCharacterDTO createGameCharacter(InsertGameCharacterDTO request) {
        AppUser user = getAppUserBySubject();
        GameCharacter character = new GameCharacter();
        character.setOwner(user);
        character.setKind(request.kind());
        apply(character, request.name(), request.description(), request.campaignName(), request.backgroundColor());
        try {
            database.save(character);
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            throw e;
        }
        return toDetailGameCharacterDTO(character);
    }

    public DetailGameCharacterDTO updateGameCharacter(UUID id, UpdateGameCharacterDTO request) {
        GameCharacter character = getGameCharacterByIdAndOwner(id);
        apply(character, request.name(), request.description(), request.campaignName(), request.backgroundColor());
        database.save(character);
        return toDetailGameCharacterDTO(character);
    }

    public DetailGameCharacterDTO updateGameCharacterAvatar(UUID id, MultipartFile file) {
        GameCharacter character = getGameCharacterByIdAndOwner(id);
        Document previous = character.getAvatarDocument();
        Document avatar = null;
        try (Transaction transaction = database.beginTransaction()) {
            avatar = documentService.createImageDocument(file);
            character.setAvatarDocument(avatar);
            database.save(character);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        } catch (RuntimeException e) {
            documentService.deleteDocumentFile(avatar);
            throw e;
        }
        documentService.deleteDocumentFile(previous);
        return toDetailGameCharacterDTO(character);
    }

    public void deleteGameCharacterAvatar(UUID id) {
        GameCharacter character = getGameCharacterByIdAndOwner(id);
        Document previous = character.getAvatarDocument();
        try (Transaction transaction = database.beginTransaction()) {
            character.setAvatarDocument(null);
            database.save(character);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        }
        documentService.deleteDocumentFile(previous);
    }

    public void deleteGameCharacter(UUID id) {
        GameCharacter character = getGameCharacterByIdAndOwner(id);
        List<Document> documents = new ArrayList<>();
        if (character.getAvatarDocument() != null) {
            documents.add(character.getAvatarDocument());
        }
        try (Transaction transaction = database.beginTransaction()) {
            List<CharacterSheet> sheets = database.find(CharacterSheet.class)
                    .setLabel("deleteGameCharacterSheets")
                    .fetch("document")
                    .where().eq("character.id", character.getId())
                    .findList();
            if (!sheets.isEmpty()) {
                sheets.stream().map(CharacterSheet::getDocument).forEach(documents::add);
                database.deleteAll(sheets);
            }
            List<Diary> diaries = database.find(Diary.class)
                    .setLabel("deleteGameCharacter")
                    .fetch("coverDocument")
                    .where().eq("character.id", character.getId())
                    .findList();
            if (!diaries.isEmpty()) {
                database.find(DiaryPage.class)
                        .setLabel("deleteGameCharacterPages")
                        .where().in("diary.id", diaries.stream().map(Diary::getId).toList())
                        .delete();
                diaries.stream().map(Diary::getCoverDocument).filter(Objects::nonNull).forEach(documents::add);
                database.deleteAll(diaries);
            }
            database.delete(character);
            documents.forEach(documentService::deleteDocumentRecord);
            transaction.commit();
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The character changed while it was being deleted, try again");
            }
            throw e;
        }
        documents.forEach(documentService::deleteDocumentFile);
    }

    private GameCharacter getGameCharacterByIdAndOwner(UUID id) {
        GameCharacter character = database.find(GameCharacter.class)
                .setLabel("getGameCharacterByIdAndOwner")
                .fetch("avatarDocument")
                .where().idEq(id).eq("owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (character == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
        return character;
    }

    private AppUser getAppUserBySubject() {
        AppUser user = database.find(AppUser.class)
                .setLabel("getAppUserBySubject")
                .where().eq("keycloakId", jwtInspector.getSubject())
                .findOne();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    private DetailGameCharacterDTO toDetailGameCharacterDTO(GameCharacter character) {
        return DetailGameCharacterDTO.from(character, documentService.getDocumentUrl(character.getAvatarDocument()));
    }

    private void apply(GameCharacter character, String name, String description, String campaignName,
                       String backgroundColor) {
        character.setName(name);
        character.setDescription(description);
        character.setCampaignName(campaignName);
        character.setBackgroundColor(backgroundColor);
    }
}

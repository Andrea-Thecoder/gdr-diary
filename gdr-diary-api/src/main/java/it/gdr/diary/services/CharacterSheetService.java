package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.gdr.diary.config.CharacterSheetConfig;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.character_sheet.BaseCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.DetailCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.InsertCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.UpdateCharacterSheetDTO;
import it.gdr.diary.dto.search.CharacterSheetSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.exception.SqlStates;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CharacterSheetService {

    private final Database database;
    private final JwtInspector jwtInspector;
    private final DocumentService documentService;
    private final CharacterSheetConfig config;

    public PagedResultDTO<BaseCharacterSheetDTO> findAllCharacterSheetsByCharacter(UUID characterId,
                                                                                    CharacterSheetSearchRequest request) {
        GameCharacter character = getGameCharacterByIdAndOwner(characterId);
        ExpressionList<CharacterSheet> query = database.find(CharacterSheet.class)
                .setLabel("findAllCharacterSheetsByCharacter")
                .fetch("document")
                .where().eq("character.id", character.getId());
        request.applyFilter(query);
        request.applySortAndPagination(query, "createdAt");
        return PagedResultDTO.of(query.findPagedList(), this::toBaseCharacterSheetDTO);
    }

    public DetailCharacterSheetDTO getCharacterSheetById(UUID id) {
        return toDetailCharacterSheetDTO(getCharacterSheetByIdAndOwner(id));
    }

    public DetailCharacterSheetDTO createCharacterSheet(UUID characterId, InsertCharacterSheetDTO request,
                                                        MultipartFile file) {
        GameCharacter character = getGameCharacterByIdAndOwner(characterId);
        CharacterSheet sheet = new CharacterSheet();
        sheet.setCharacter(character);
        apply(sheet, request.title(), request.system());
        Document document = null;
        try (Transaction transaction = database.beginTransaction()) {
            lockGameCharacter(character.getId());
            int count = database.find(CharacterSheet.class)
                    .setLabel("createCharacterSheet")
                    .where().eq("character.id", character.getId())
                    .findCount();
            if (count >= config.getMaxSheetsPerCharacter()) {
                throw new ServiceException("The character has too many sheets (" + config.getMaxSheetsPerCharacter() + ")");
            }
            document = documentService.createPdfDocument(file);
            sheet.setDocument(document);
            database.save(sheet);
            transaction.commit();
        } catch (PersistenceException e) {
            documentService.deleteDocumentFile(document);
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
            }
            throw e;
        } catch (RuntimeException e) {
            documentService.deleteDocumentFile(document);
            throw e;
        }
        return toDetailCharacterSheetDTO(sheet);
    }

    public DetailCharacterSheetDTO updateCharacterSheet(UUID id, UpdateCharacterSheetDTO request) {
        CharacterSheet sheet = getCharacterSheetByIdAndOwner(id);
        apply(sheet, request.title(), request.system());
        database.save(sheet);
        return toDetailCharacterSheetDTO(sheet);
    }

    public DetailCharacterSheetDTO updateCharacterSheetFile(UUID id, MultipartFile file) {
        CharacterSheet sheet = getCharacterSheetByIdAndOwner(id);
        Document previous = sheet.getDocument();
        Document replacement = null;
        try (Transaction transaction = database.beginTransaction()) {
            replacement = documentService.createPdfDocument(file);
            sheet.setDocument(replacement);
            database.save(sheet);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        } catch (RuntimeException e) {
            documentService.deleteDocumentFile(replacement);
            throw e;
        }
        documentService.deleteDocumentFile(previous);
        return toDetailCharacterSheetDTO(sheet);
    }

    public void deleteCharacterSheet(UUID id) {
        CharacterSheet sheet = getCharacterSheetByIdAndOwner(id);
        Document document = sheet.getDocument();
        try (Transaction transaction = database.beginTransaction()) {
            database.delete(sheet);
            documentService.deleteDocumentRecord(document);
            transaction.commit();
        }
        documentService.deleteDocumentFile(document);
    }

    private void lockGameCharacter(UUID id) {
        GameCharacter locked = database.find(GameCharacter.class)
                .setLabel("lockGameCharacter")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
    }

    private GameCharacter getGameCharacterByIdAndOwner(UUID characterId) {
        GameCharacter character = database.find(GameCharacter.class)
                .setLabel("getGameCharacterByIdAndOwner")
                .where().idEq(characterId).eq("owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (character == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
        return character;
    }

    private CharacterSheet getCharacterSheetByIdAndOwner(UUID id) {
        CharacterSheet sheet = database.find(CharacterSheet.class)
                .setLabel("getCharacterSheetByIdAndOwner")
                .fetch("document")
                .where().idEq(id).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (sheet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found");
        }
        return sheet;
    }

    private BaseCharacterSheetDTO toBaseCharacterSheetDTO(CharacterSheet sheet) {
        return BaseCharacterSheetDTO.from(sheet,
                documentService.getDocumentUrl(sheet.getDocument()),
                documentService.getDocumentDownloadUrl(sheet.getDocument()));
    }

    private DetailCharacterSheetDTO toDetailCharacterSheetDTO(CharacterSheet sheet) {
        return DetailCharacterSheetDTO.from(sheet,
                documentService.getDocumentUrl(sheet.getDocument()),
                documentService.getDocumentDownloadUrl(sheet.getDocument()));
    }

    private void apply(CharacterSheet sheet, String title, String system) {
        sheet.setTitle(StringUtils.trim(title));
        sheet.setGameSystem(StringUtils.trimToNull(system));
    }
}

package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary.BaseDiaryDTO;
import it.gdr.diary.dto.diary.BookmarkDiaryDTO;
import it.gdr.diary.dto.diary.DetailDiaryDTO;
import it.gdr.diary.dto.diary.InsertDiaryDTO;
import it.gdr.diary.dto.diary.UpdateBookmarkDiaryDTO;
import it.gdr.diary.dto.diary.UpdateDiaryDTO;
import it.gdr.diary.dto.search.DiarySearchRequest;
import it.gdr.diary.exception.SqlStates;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final Database database;
    private final JwtInspector jwtInspector;
    private final DocumentService documentService;

    public PagedResultDTO<BaseDiaryDTO> findAllDiariesByCharacter(UUID characterId, DiarySearchRequest request) {
        GameCharacter character = getGameCharacterByIdAndOwner(characterId);
        ExpressionList<Diary> query = database.find(Diary.class)
                .setLabel("findAllDiariesByCharacter")
                .fetch("coverDocument")
                .where().eq("character.id", character.getId());
        request.applyFilter(query);
        request.applySortAndPagination(query, "title");
        return PagedResultDTO.of(query.findPagedList(),
                diary -> BaseDiaryDTO.from(diary, documentService.getDocumentUrl(diary.getCoverDocument())));
    }

    public DetailDiaryDTO getDiaryById(UUID id) {
        return toDetailDiaryDTO(getDiaryByIdAndOwner(id));
    }

    public DetailDiaryDTO createDiary(UUID characterId, InsertDiaryDTO request) {
        GameCharacter character = getGameCharacterByIdAndOwner(characterId);
        Diary diary = new Diary();
        diary.setCharacter(character);
        apply(diary, request.title(), request.description(), request.coverColor(), request.font(), request.fontSize());
        try {
            database.save(diary);
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
            }
            throw e;
        }
        return toDetailDiaryDTO(diary);
    }

    public DetailDiaryDTO updateDiary(UUID id, UpdateDiaryDTO request) {
        Diary diary = getDiaryByIdAndOwner(id);
        apply(diary, request.title(), request.description(), request.coverColor(), request.font(), request.fontSize());
        database.save(diary);
        return toDetailDiaryDTO(diary);
    }

    public DetailDiaryDTO updateDiaryCover(UUID id, MultipartFile file) {
        Diary diary = getDiaryByIdAndOwner(id);
        Document previous = diary.getCoverDocument();
        Document cover = null;
        try (Transaction transaction = database.beginTransaction()) {
            cover = documentService.createImageDocument(file);
            diary.setCoverDocument(cover);
            database.save(diary);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        } catch (RuntimeException e) {
            documentService.deleteDocumentFile(cover);
            throw e;
        }
        documentService.deleteDocumentFile(previous);
        return toDetailDiaryDTO(diary);
    }

    public void deleteDiaryCover(UUID id) {
        Diary diary = getDiaryByIdAndOwner(id);
        Document previous = diary.getCoverDocument();
        try (Transaction transaction = database.beginTransaction()) {
            diary.setCoverDocument(null);
            database.save(diary);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        }
        documentService.deleteDocumentFile(previous);
    }

    public BookmarkDiaryDTO getDiaryBookmark(UUID id) {
        Diary diary = database.find(Diary.class)
                .setLabel("getDiaryBookmark")
                .fetch("bookmarkPage", "pageNumber")
                .where().idEq(id).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found");
        }
        return BookmarkDiaryDTO.from(diary.getBookmarkPage());
    }

    public BookmarkDiaryDTO updateDiaryBookmark(UUID id, UpdateBookmarkDiaryDTO request) {
        Diary diary = getDiaryByIdAndOwner(id);
        DiaryPage page = database.find(DiaryPage.class)
                .setLabel("getBookmarkPage")
                .where().idEq(request.pageId()).eq("diary.id", diary.getId())
                .findOne();
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        diary.setBookmarkPage(page);
        try {
            database.save(diary);
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
            }
            throw e;
        }
        return BookmarkDiaryDTO.from(page);
    }

    public void deleteDiaryBookmark(UUID id) {
        Diary diary = getDiaryByIdAndOwner(id);
        diary.setBookmarkPage(null);
        database.save(diary);
    }

    public void deleteDiary(UUID id) {
        Diary diary = getDiaryByIdAndOwner(id);
        Document cover = diary.getCoverDocument();
        try (Transaction transaction = database.beginTransaction()) {
            lockDiary(diary.getId());
            database.find(DiaryPage.class)
                    .setLabel("deleteDiary")
                    .where().eq("diary.id", diary.getId())
                    .delete();
            database.delete(diary);
            documentService.deleteDocumentRecord(cover);
            transaction.commit();
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The diary changed while it was being deleted, try again");
            }
            throw e;
        }
        documentService.deleteDocumentFile(cover);
    }

    private void lockDiary(UUID id) {
        Diary locked = database.find(Diary.class)
                .setLabel("lockDiary")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found");
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

    private Diary getDiaryByIdAndOwner(UUID id) {
        Diary diary = database.find(Diary.class)
                .setLabel("getDiaryByIdAndOwner")
                .fetch("coverDocument")
                .where().idEq(id).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found");
        }
        return diary;
    }

    private DetailDiaryDTO toDetailDiaryDTO(Diary diary) {
        return DetailDiaryDTO.from(diary, documentService.getDocumentUrl(diary.getCoverDocument()));
    }

    private void apply(Diary diary, String title, String description, String coverColor, String font,
                       Short fontSize) {
        diary.setTitle(title);
        diary.setDescription(description);
        diary.setCoverColor(coverColor);
        diary.setFont(font);
        diary.setFontSize(fontSize);
    }
}

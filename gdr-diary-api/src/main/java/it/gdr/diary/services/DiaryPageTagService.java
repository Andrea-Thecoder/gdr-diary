package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page_tag.BaseDiaryTagDTO;
import it.gdr.diary.dto.diary_page_tag.DetailDiaryPageTagDTO;
import it.gdr.diary.dto.diary_page_tag.UpdateDiaryPageTagDTO;
import it.gdr.diary.dto.search.DiaryTagSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.exception.SqlStates;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.DiaryPageTag;
import it.gdr.diary.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryPageTagService {

    /** Massimo numero di tag diversi (ignorando le maiuscole) in un diario. */
    static final int MAX_TAGS_PER_DIARY = 10;

    private final Database database;
    private final JwtInspector jwtInspector;

    public PagedResultDTO<BaseDiaryTagDTO> findAllDiaryTagsByDiary(UUID diaryId, DiaryTagSearchRequest request) {
        getDiaryByIdAndOwner(diaryId);
        ExpressionList<DiaryPageTag> query = database.find(DiaryPageTag.class)
                .setLabel("findAllDiaryTagsByDiary")
                .select("name")
                .fetch("page", "pageNumber")
                .where()
                .eq("diary.id", diaryId)
                .eq("diary.character.owner.keycloakId", jwtInspector.getSubject());
        request.applyFilter(query);
        List<DiaryPageTag> tags = query.orderBy("page.pageNumber").findList();
        Map<String, List<DiaryPageTag>> groups = new LinkedHashMap<>();
        for (DiaryPageTag tag : tags) {
            groups.computeIfAbsent(key(tag.getName()), name -> new ArrayList<>()).add(tag);
        }
        List<BaseDiaryTagDTO> all = groups.values().stream()
                .map(group -> new BaseDiaryTagDTO(group.getFirst().getName(),
                        group.stream().map(tag -> tag.getPage().getPageNumber()).toList()))
                .toList();
        int from = (int) Math.min((request.getPage() - 1L) * request.getSize(), all.size());
        int to = Math.min(from + request.getSize(), all.size());
        return PagedResultDTO.of(all.subList(from, to), all.size(), request.getPage(), request.getSize());
    }

    /** Tag delle pagine date (solo quelle con un tag), filtrati per proprietario. */
    public Map<UUID, String> findTagByPages(Collection<UUID> pageIds) {
        if (pageIds.isEmpty()) {
            return Map.of();
        }
        return database.find(DiaryPageTag.class)
                .setLabel("findTagByPages")
                .select("name, page")
                .where().in("page.id", pageIds).eq("diary.character.owner.keycloakId", jwtInspector.getSubject())
                .findList().stream()
                .collect(Collectors.toMap(tag -> tag.getPage().getId(), DiaryPageTag::getName, (first, second) -> first));
    }

    public DetailDiaryPageTagDTO updateDiaryPageTag(UUID pageId, UpdateDiaryPageTagDTO request) {
        UUID diaryId = getDiaryPageByIdAndOwner(pageId).getDiary().getId();
        String requested = request.tag() == null ? null : request.tag().trim();
        String result;
        try (Transaction transaction = database.beginTransaction()) {
            // Il diario e' bloccato come in DiaryPageService: serializza i cambi dei tag, protegge il limite dei 10 tag
            // e impedisce che la pagina venga cancellata mentre si salva il tag.
            Diary diary = lockDiaryByOwner(diaryId);
            getDiaryPageByIdAndOwner(pageId);
            DiaryPageTag current = database.find(DiaryPageTag.class)
                    .setLabel("updateDiaryPageTag")
                    .where().eq("page.id", pageId).eq("diary.character.owner.keycloakId", jwtInspector.getSubject())
                    .findOne();
            if (requested == null) {
                if (current != null) {
                    database.delete(current);
                }
                result = null;
            } else {
                Map<String, String> others = findOtherTagsOfDiary(diaryId, pageId);
                String name = others.getOrDefault(key(requested), requested);
                if (!others.containsKey(key(name)) && others.size() >= MAX_TAGS_PER_DIARY) {
                    throw new ServiceException("A diary can have at most " + MAX_TAGS_PER_DIARY + " different tags");
                }
                if (current == null) {
                    current = new DiaryPageTag();
                    current.setDiary(diary);
                    current.setPage(getDiaryPageByIdAndOwner(pageId));
                }
                if (!name.equals(current.getName())) {
                    current.setName(name);
                    database.save(current);
                }
                result = name;
            }
            transaction.commit();
        } catch (PersistenceException e) {
            if (e instanceof DuplicateKeyException || SqlStates.hasSqlState(e, Set.of(SqlStates.UNIQUE_VIOLATION))) {
                throw new ServiceException("The tag of the page was changed at the same time, try again");
            }
            throw e;
        }
        return new DetailDiaryPageTagDTO(result);
    }

    public void deleteDiaryPageTagsByPage(UUID pageId) {
        database.find(DiaryPageTag.class)
                .setLabel("deleteDiaryPageTagsByPage")
                .where().eq("page.id", pageId)
                .delete();
    }

    public void deleteDiaryPageTagsByDiaries(Collection<UUID> diaryIds) {
        database.find(DiaryPageTag.class)
                .setLabel("deleteDiaryPageTagsByDiaries")
                .where().in("diary.id", diaryIds)
                .delete();
    }

    /** Tag del diario usati da pagine diverse da quella data, per chiave (nome in minuscolo) con la grafia salvata. */
    private Map<String, String> findOtherTagsOfDiary(UUID diaryId, UUID pageId) {
        return database.find(DiaryPageTag.class)
                .setLabel("findOtherTagsOfDiary")
                .select("name")
                .where().eq("diary.id", diaryId).ne("page.id", pageId).eq("diary.character.owner.keycloakId", jwtInspector.getSubject())
                .orderBy("name")
                .findList().stream()
                .collect(Collectors.toMap(tag -> key(tag.getName()), DiaryPageTag::getName, (first, second) -> first, LinkedHashMap::new));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private Diary lockDiaryByOwner(UUID id) {
        Diary locked = database.find(Diary.class)
                .setLabel("lockDiaryByOwner")
                .select("id")
                .forUpdate()
                .where().idEq(id).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        return locked;
    }

    private Diary getDiaryByIdAndOwner(UUID id) {
        Diary diary = database.find(Diary.class)
                .setLabel("getDiaryByIdAndOwner")
                .select("id")
                .where().idEq(id).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found");
        }
        return diary;
    }

    private DiaryPage getDiaryPageByIdAndOwner(UUID id) {
        DiaryPage page = database.find(DiaryPage.class)
                .setLabel("getDiaryPageByIdAndOwner")
                .select("id, diary")
                .where().idEq(id).eq("diary.character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        return page;
    }
}

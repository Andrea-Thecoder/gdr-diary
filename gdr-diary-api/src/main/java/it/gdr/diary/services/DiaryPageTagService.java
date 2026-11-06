package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page_tag.BaseDiaryTagDTO;
import it.gdr.diary.dto.diary_page_tag.DetailDiaryPageTagsDTO;
import it.gdr.diary.dto.diary_page_tag.UpdateDiaryPageTagsDTO;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryPageTagService {

    private final Database database;
    private final JwtInspector jwtInspector;

    public PagedResultDTO<BaseDiaryTagDTO> findAllDiaryTagsByDiary(UUID diaryId, DiaryTagSearchRequest request) {
        getDiaryByIdAndOwner(diaryId);
        ExpressionList<DiaryPageTag> query = database.find(DiaryPageTag.class)
                .setLabel("findAllDiaryTagsByDiary")
                .select("name")
                .fetch("page", "pageNumber")
                .where().eq("page.diary.id", diaryId).eq("page.diary.character.owner.keycloakId", jwtInspector.getSubject());
        request.applyFilter(query);
        List<DiaryPageTag> tags = query.orderBy("page.pageNumber").findList();
        Map<String, List<DiaryPageTag>> groups = new LinkedHashMap<>();
        for (DiaryPageTag tag : tags) {
            groups.computeIfAbsent(key(tag.getName()), name -> new ArrayList<>()).add(tag);
        }
        List<BaseDiaryTagDTO> all = groups.values().stream()
                .map(group -> new BaseDiaryTagDTO(group.getFirst().getName(),
                        group.stream().map(tag -> tag.getPage().getPageNumber()).toList()))
                .sorted(Comparator.comparing(BaseDiaryTagDTO::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = (int) Math.min((request.getPage() - 1L) * request.getSize(), all.size());
        int to = Math.min(from + request.getSize(), all.size());
        return PagedResultDTO.of(all.subList(from, to), all.size(), request.getPage(), request.getSize());
    }

    public Map<UUID, List<String>> findTagNamesByPages(Collection<UUID> pageIds) {
        if (pageIds.isEmpty()) {
            return Map.of();
        }
        return database.find(DiaryPageTag.class)
                .setLabel("findTagNamesByPages")
                .select("name, page")
                .where().in("page.id", pageIds).eq("page.diary.character.owner.keycloakId", jwtInspector.getSubject())
                .findList().stream()
                .collect(Collectors.groupingBy(tag -> tag.getPage().getId(),
                        Collectors.collectingAndThen(Collectors.mapping(DiaryPageTag::getName, Collectors.toList()),
                                names -> names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList())));
    }

    public DetailDiaryPageTagsDTO updateDiaryPageTags(UUID pageId, UpdateDiaryPageTagsDTO request) {
        getDiaryPageByIdAndOwner(pageId);
        List<String> names = normalize(request.tags());
        List<DiaryPageTag> kept;
        try (Transaction transaction = database.beginTransaction()) {
            DiaryPage page = lockDiaryPage(pageId);
            Map<String, DiaryPageTag> current = database.find(DiaryPageTag.class)
                    .setLabel("updateDiaryPageTags")
                    .where().eq("page.id", pageId)
                    .findList().stream()
                    .collect(Collectors.toMap(tag -> key(tag.getName()), Function.identity(), (first, second) -> first, HashMap::new));
            kept = new ArrayList<>();
            for (String name : names) {
                DiaryPageTag tag = current.remove(key(name));
                if (tag == null) {
                    tag = new DiaryPageTag();
                    tag.setPage(page);
                }
                tag.setName(name);
                kept.add(tag);
            }
            database.deleteAll(current.values());
            database.saveAll(kept);
            transaction.commit();
        } catch (PersistenceException e) {
            if (e instanceof DuplicateKeyException || SqlStates.hasSqlState(e, Set.of(SqlStates.UNIQUE_VIOLATION))) {
                throw new ServiceException("The tags must differ from each other, ignoring case");
            }
            throw e;
        }
        return new DetailDiaryPageTagsDTO(kept.stream().map(DiaryPageTag::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList());
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
                .where().in("page.diary.id", diaryIds)
                .delete();
    }

    private static List<String> normalize(List<String> tags) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String tag : tags) {
            String name = tag.trim();
            unique.putIfAbsent(key(name), name);
        }
        return List.copyOf(unique.values());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private DiaryPage lockDiaryPage(UUID id) {
        DiaryPage locked = database.find(DiaryPage.class)
                .setLabel("lockDiaryPage")
                .select("id")
                .forUpdate()
                .where().idEq(id)
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
                .select("id")
                .where().idEq(id).eq("diary.character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        return page;
    }
}

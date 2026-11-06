package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.ExpressionList;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.gdr.diary.config.DiaryPageConfig;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.diary_page.BaseDiaryPageDTO;
import it.gdr.diary.dto.diary_page.InsertDiaryPageDTO;
import it.gdr.diary.dto.diary_page.UpdateDiaryPageDTO;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.exception.SqlStates;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryPageService {

    private static final int MAX_PAGES = 100;
    private static final int PAGES_PER_VIEW = 2;
    private static final Set<String> CONCURRENT_INSERT_STATES =
            Set.of(SqlStates.UNIQUE_VIOLATION, SqlStates.CHECK_VIOLATION, SqlStates.DEADLOCK);

    private final Database database;
    private final JwtInspector jwtInspector;
    private final ObjectMapper objectMapper;
    private final DiaryPageConfig config;
    private final DiaryPageContentValidator contentValidator;
    private final DiaryPageTagService tagService;

    public PagedResultDTO<BaseDiaryPageDTO> findAllDiaryPagesByDiary(UUID diaryId, int page) {
        if (page < 1) {
            throw new ServiceException("The page must be 1 or greater");
        }
        Diary diary = getDiaryByIdAndOwner(diaryId);
        ExpressionList<DiaryPage> query = database.find(DiaryPage.class)
                .setLabel("findAllDiaryPagesByDiary")
                .where().eq("diary.id", diary.getId())
                .orderBy("pageNumber")
                .setFirstRow((int) Math.min((page - 1L) * PAGES_PER_VIEW, Integer.MAX_VALUE))
                .setMaxRows(PAGES_PER_VIEW);
        PagedList<DiaryPage> pages = query.findPagedList();
        Map<UUID, List<String>> tags = tagService.findTagNamesByPages(pages.getList().stream().map(DiaryPage::getId).toList());
        return PagedResultDTO.of(pages, found -> BaseDiaryPageDTO.from(found, tags.getOrDefault(found.getId(), List.of())));
    }

    public BaseDiaryPageDTO createDiaryPage(UUID diaryId, InsertDiaryPageDTO request) {
        Diary diary = getDiaryByIdAndOwner(diaryId);
        validateContent(request.content());
        DiaryPage page = new DiaryPage();
        page.setDiary(diary);
        page.setContent(request.content());
        try (Transaction transaction = database.beginTransaction()) {
            lockDiary(diary.getId(), "Diary not found");
            int count = database.find(DiaryPage.class).setLabel("createDiaryPage").where().eq("diary.id", diary.getId()).findCount();
            if (count >= MAX_PAGES) {
                throw new ServiceException("The diary is full (" + MAX_PAGES + " pages)");
            }
            page.setPageNumber((short) (count + 1));
            database.save(page);
            transaction.commit();
        } catch (PersistenceException e) {
            if (isConcurrentInsert(e)) {
                throw new ServiceException("Another page was added at the same time, try again");
            }
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary not found");
            }
            throw e;
        }
        return BaseDiaryPageDTO.from(page, List.of());
    }

    public BaseDiaryPageDTO updateDiaryPage(UUID id, UpdateDiaryPageDTO request) {
        DiaryPage page = getDiaryPageByIdAndOwner(id);
        if (!Objects.equals(page.getVersion(), request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The page was modified elsewhere, reload it");
        }
        validateContent(request.content());
        page.setContent(request.content());
        database.save(page);
        return BaseDiaryPageDTO.from(page, tagService.findTagNamesByPages(List.of(page.getId())).getOrDefault(page.getId(), List.of()));
    }

    public void deleteDiaryPage(UUID id) {
        UUID diaryId = getDiaryPageByIdAndOwner(id).getDiary().getId();
        try (Transaction transaction = database.beginTransaction()) {
            lockDiary(diaryId, "Page not found");
            DiaryPage page = getDiaryPageByIdAndOwner(id);
            tagService.deleteDiaryPageTagsByPage(page.getId());
            database.delete(page);
            List<DiaryPage> following = database.find(DiaryPage.class)
                    .setLabel("deleteDiaryPage")
                    .select("pageNumber, version")
                    .where().eq("diary.id", diaryId).gt("pageNumber", page.getPageNumber())
                    .orderBy("pageNumber")
                    .findList();
            for (DiaryPage next : following) {
                next.setPageNumber((short) (next.getPageNumber() - 1));
            }
            database.saveAll(following);
            transaction.commit();
        }
    }

    private static boolean isConcurrentInsert(PersistenceException e) {
        return e instanceof DuplicateKeyException || SqlStates.hasSqlState(e, CONCURRENT_INSERT_STATES);
    }

    private void lockDiary(UUID id, String notFoundMessage) {
        Diary locked = database.find(Diary.class)
                .setLabel("lockDiary")
                .select("id")
                .forUpdate()
                .where().idEq(id)
                .findOne();
        if (locked == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
    }

    private void validateContent(Map<String, Object> content) {
        if (objectMapper.writeValueAsBytes(content).length > config.getMaxPageContentSize().toBytes()) {
            throw new ServiceException("Page content too large");
        }
        contentValidator.validate(content);
    }

    private Diary getDiaryByIdAndOwner(UUID id) {
        Diary diary = database.find(Diary.class)
                .setLabel("getDiaryByIdAndOwner")
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
                .where().idEq(id).eq("diary.character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found");
        }
        return page;
    }
}

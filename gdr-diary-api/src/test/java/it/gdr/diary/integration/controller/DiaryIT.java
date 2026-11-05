package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.ConcurrentCalls;
import it.gdr.diary.integration.support.TestContainers;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class DiaryIT extends AbstractIntegrationTest {

    @Test
    void createDiary_ownCharacter_linksDiaryToCharacter() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        UUID diaryId = createDiary(characterId, "Chapter one");

        UUID storedCharacter = jdbc.queryForObject("select character_id from diary where id = ?", UUID.class, diaryId);
        assertThat(storedCharacter).isEqualTo(characterId);
    }

    @Test
    void createDiary_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", othersCharacter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Stolen\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDiary_characterOfOtherUser_storesNothing() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        mvc.post().uri("/characters/{characterId}/diaries", othersCharacter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Stolen\"}").exchange();

        assertThat(countRows("diary")).isZero();
    }

    @Test
    void findAllDiariesByCharacter_titleFilter_returnsMatchingDiaries() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Chapter one");
        createDiary(characterId, "Notes");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?title=chapter", characterId)).bodyJson()
                .extractingPath("$.list[*].title").isEqualTo(List.of("Chapter one"));
    }

    @Test
    void findAllDiariesByCharacter_twoCharacters_returnsOnlyDiariesOfRequestedCharacter() {
        createAppUser();
        UUID aragorn = createGameCharacter("Aragorn");
        UUID legolas = createGameCharacter("Legolas");
        createDiary(aragorn, "Ranger notes");
        createDiary(legolas, "Elf notes");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", aragorn)).bodyJson()
                .extractingPath("$.list[*].title").isEqualTo(List.of("Ranger notes"));
    }

    @Test
    void getDiaryById_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{id}", othersDiary)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiary_ownDiary_storesNewValues() {
        UUID diaryId = createDiaryWithOwner();

        mvc.put().uri("/diaries/{id}", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Chapter two\",\"fontSize\":18}").exchange();

        assertThat(mvc.get().uri("/diaries/{id}", diaryId)).bodyJson().extractingPath("$['title','fontSize']")
                .isEqualTo(Map.of("title", "Chapter two", "fontSize", 18));
    }

    @Test
    void updateDiaryCover_validImage_returnsCoverUrl() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png"))).bodyJson()
                .extractingPath("$.coverUrl").asString().contains(TestContainers.storageEndpoint() + "/" + TestContainers.BUCKET + "/");
    }

    @Test
    void updateDiaryCover_replacingCover_keepsOnlyNewFile() throws Exception {
        UUID diaryId = createDiaryWithOwner();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("first.png")).exchange();

        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("second.png")).exchange();

        String newKey = jdbc.queryForObject("select object_key from document", String.class);
        assertThat(storedObjectKeys()).containsExactly(newKey);
    }

    @Test
    void deleteDiaryCover_withCover_removesFileAndRow() throws Exception {
        UUID diaryId = createDiaryWithOwner();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/diaries/{id}/cover", diaryId).exchange();

        assertThat(List.of(storedObjectKeys().size(), countRows("document"))).containsOnly(0);
    }

    @Test
    void deleteDiary_withPages_deletesPagesAndDiary() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Day one");
        createDiaryPage(diaryId, "Day two");

        mvc.delete().uri("/diaries/{id}", diaryId).exchange();

        assertThat(List.of(countRows("diary"), countRows("diary_page"))).containsOnly(0);
    }

    @Test
    void deleteDiary_characterWithTwoDiaries_keepsOtherDiaryAndItsPages() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID deleted = createDiary(characterId, "Chapter one");
        UUID kept = createDiary(characterId, "Chapter two");
        createDiaryPage(deleted, "Gone");
        createDiaryPage(kept, "Still here");

        mvc.delete().uri("/diaries/{id}", deleted).exchange();

        Integer keptPages = jdbc.queryForObject("select count(*) from diary_page where diary_id = ?", Integer.class, kept);
        assertThat(keptPages).isEqualTo(1);
    }

    @Test
    void deleteDiary_withCover_removesCoverFile() throws Exception {
        UUID diaryId = createDiaryWithOwner();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/diaries/{id}", diaryId).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void deleteDiary_diaryOfOtherUser_keepsDiary() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        mvc.delete().uri("/diaries/{id}", othersDiary).exchange();

        assertThat(countRows("diary")).isEqualTo(1);
    }

    @Test
    void updateDiary_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.put().uri("/diaries/{id}", othersDiary).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hacked\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiary_diaryOfOtherUser_keepsDiaryUnchanged() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        mvc.put().uri("/diaries/{id}", othersDiary).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hacked\"}").exchange();

        String title = jdbc.queryForObject("select title from diary where id = ?", String.class, othersDiary);
        assertThat(title).isEqualTo("Chapter one");
    }

    @Test
    void findAllDiariesByCharacter_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryCover_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.put().uri("/diaries/{id}/cover", othersDiary).multipart().file(pngFile("cover.png")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryCover_diaryOfOtherUser_storesNoFile() throws Exception {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        mvc.put().uri("/diaries/{id}/cover", othersDiary).multipart().file(pngFile("cover.png")).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void deleteDiaryCover_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.delete().uri("/diaries/{id}/cover", othersDiary)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDiaryCover_diaryOfOtherUser_keepsCover() throws Exception {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        mvc.put().uri("/diaries/{id}/cover", othersDiary).with(TestJwt.otherUser()).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/diaries/{id}/cover", othersDiary).exchange();

        assertThat(storedObjectKeys()).hasSize(1);
    }

    private UUID createDiaryWithDescription(UUID characterId, String title, String description) {
        MvcTestResult result = mvc.post().uri("/characters/{characterId}/diaries", characterId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"" + description + "\"}")
                .exchange();
        return UUID.fromString(json(result, "$.id"));
    }

    @Test
    void findAllDiariesByCharacter_injectedSortFields_keepsTitleOrder() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiaryWithDescription(characterId, "Alpha", "z");
        createDiaryWithDescription(characterId, "Beta", "a");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId)
                .param("SORT_FIELDS[description]", "description").param("sort", "description"))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("Alpha", "Beta"));
    }

    @Test
    void findAllDiariesByCharacter_bogusInjectedSortFields_doesNotReturnServerError() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Chapter one");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId)
                .param("SORT_FIELDS[x]", "nonexistent").param("sort", "x")).hasStatusOk();
    }

    @Test
    void findAllDiariesByCharacter_sortByTitleDescending_ordersByTitleDescending() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Alpha");
        createDiary(characterId, "Gamma");
        createDiary(characterId, "Beta");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?sort=title&descending=true", characterId))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("Gamma", "Beta", "Alpha"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdBy", "description", "title;drop"})
    void findAllDiariesByCharacter_unknownSortKey_ordersByTitle(String sort) {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Gamma");
        createDiary(characterId, "Alpha");
        createDiary(characterId, "Beta");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId).param("sort", sort))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("Alpha", "Beta", "Gamma"));
    }

    @Test
    void findAllDiariesByCharacter_secondPage_returnsRemainingDiary() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Alpha");
        createDiary(characterId, "Beta");
        createDiary(characterId, "Gamma");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?page=2&size=2", characterId))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("Gamma"));
    }

    @Test
    void findAllDiariesByCharacter_secondPage_returnsTotals() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Alpha");
        createDiary(characterId, "Beta");
        createDiary(characterId, "Gamma");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?page=2&size=2", characterId)).bodyJson()
                .extractingPath("$['page','totalRows','totalPages']").isEqualTo(Map.of("page", 2, "totalRows", 3, "totalPages", 2));
    }

    @Test
    void findAllDiariesByCharacter_sizeOver100_returnsBadRequest() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?size=101", characterId)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiariesByCharacter_titleWithSurroundingSpaces_matchesTrimmedTitle() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "Chapter one");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId).param("title", " Chapter "))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("Chapter one"));
    }

    @Test
    void findAllDiariesByCharacter_characterWithoutDiaries_returnsEmptyList() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId))
                .bodyJson().extractingPath("$.list").isEqualTo(List.of());
    }

    @Test
    void findAllDiariesByCharacter_characterWithoutDiaries_returnsZeroTotals() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId)).bodyJson()
                .extractingPath("$['totalRows','totalPages']").isEqualTo(Map.of("totalRows", 0, "totalPages", 0));
    }

    @Test
    void findAllDiariesByCharacter_percentInTitleFilter_matchesOnlyLiteralPercent() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "100% done");
        createDiary(characterId, "Chapter one");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId).param("title", "%"))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("100% done"));
    }

    @Test
    void findAllDiariesByCharacter_underscoreInTitleFilter_matchesOnlyLiteralUnderscore() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        createDiary(characterId, "snake_case");
        createDiary(characterId, "Chapter one");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries", characterId).param("title", "_"))
                .bodyJson().extractingPath("$.list[*].title").isEqualTo(List.of("snake_case"));
    }

    @Test
    void createDiary_nulCharacterInTitle_returnsBadRequest() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        assertThat(mvc.post().uri("/characters/{characterId}/diaries", characterId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"a\\u0000b\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiariesByCharacter_nulCharacterInTitleFilter_doesNotReturnServerError() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        int status = mvc.get().uri("/characters/{characterId}/diaries", characterId).param("title", "a\u0000b")
                .exchange().getResponse().getStatus();

        assertThat(status).isLessThan(500);
    }

    @Test
    void updateDiary_optionalFieldsNull_clearsThem() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiaryWithDescription(characterId, "Chapter one", "Long story");

        mvc.put().uri("/diaries/{id}", diaryId).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Chapter one\"}").exchange();

        Map<String, Object> stored = jdbc.queryForMap("select description, cover_color, font, font_size from diary where id = ?", diaryId);
        assertThat(stored.values()).containsOnlyNulls();
    }

    @Test
    void deleteDiaryCover_withoutCover_returnsOk() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.delete().uri("/diaries/{id}/cover", diaryId)).hasStatusOk();
    }

    @Test
    void updateDiaryCover_pdfContent_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();
        MockMultipartFile pdf = new MockMultipartFile("file", "cover.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 content".getBytes());

        assertThat(mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pdf)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryCover_pdfContent_storesNothing() throws Exception {
        UUID diaryId = createDiaryWithOwner();
        MockMultipartFile pdf = new MockMultipartFile("file", "cover.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 content".getBytes());

        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pdf).exchange();

        assertThat(List.of(storedObjectKeys().size(), countRows("document"))).containsOnly(0);
    }

    @Test
    void deleteDiary_oneOfTwoCharactersDiaries_keepsDiariesAndPagesOfOtherCharacter() {
        createAppUser();
        UUID first = createGameCharacter("Aragorn");
        UUID second = createGameCharacter("Legolas");
        UUID deleted = createDiary(first, "Gone");
        UUID kept = createDiary(second, "Still here");
        createDiaryPage(deleted, "Gone");
        createDiaryPage(kept, "Still here");

        mvc.delete().uri("/diaries/{id}", deleted).exchange();

        assertThat(List.of(countRows("diary"), countRows("diary_page"))).containsOnly(1);
    }

    @Test
    void deleteDiary_oneOfTwoCharactersDiaries_keepsCoverOfOtherCharacterDiary() throws Exception {
        createAppUser();
        UUID deleted = createDiary(createGameCharacter("Aragorn"), "Gone");
        UUID kept = createDiary(createGameCharacter("Legolas"), "Still here");
        mvc.put().uri("/diaries/{id}/cover", deleted).multipart().file(pngFile("a.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", kept).multipart().file(pngFile("b.png")).exchange();

        mvc.delete().uri("/diaries/{id}", deleted).exchange();

        assertThat(storedObjectKeys()).hasSize(1);
    }

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private static final String INSERT_PAGE = "insert into diary_page (id, page_number, diary_id, created_at, updated_at, version, "
            + "content, created_by, updated_by) values (?, ?, ?, now(), now(), 1, '{\"type\":\"doc\"}'::jsonb, 'test', 'test')";

    private MvcTestResult deleteDiaryWhilePageIsAdded(UUID diaryId) {
        try (BlockingTransaction creation = BlockingTransaction.begin(jdbc)
                .execute("select id from diary where id = ? for update", diaryId)
                .execute(INSERT_PAGE, UUID.randomUUID(), (short) 2, diaryId)) {
            CompletableFuture<MvcTestResult> delete = creation.startBlockedCall(
                    () -> mvc.delete().uri("/diaries/{id}", diaryId).with(currentUser()).exchange());
            creation.commit();
            return delete.join();
        }
    }

    private MvcTestResult createDiaryWhileCharacterIsDeleted(UUID characterId) {
        try (BlockingTransaction deletion = BlockingTransaction.begin(jdbc)
                .execute("delete from game_character where id = ?", characterId)) {
            CompletableFuture<MvcTestResult> create = deletion.startBlockedCall(
                    () -> mvc.post().uri("/characters/{characterId}/diaries", characterId).with(currentUser())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Too late\"}").exchange());
            deletion.commit();
            return create.join();
        }
    }

    private List<MvcTestResult> updateDiaryInParallel(UUID diaryId) {
        return ConcurrentCalls.fireAtOnce(2, index -> mvc.put().uri("/diaries/{id}", diaryId).with(currentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Renamed " + index + "\",\"font\":\"Lora\",\"fontSize\":18}").exchange());
    }

    @Test
    void deleteDiary_pageAddedWhileWaitingForLock_returnsOk() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        MvcTestResult result = deleteDiaryWhilePageIsAdded(diaryId);

        assertThat(result).hasStatusOk();
    }

    @Test
    void deleteDiary_pageAddedWhileWaitingForLock_deletesAlsoTheNewPage() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        deleteDiaryWhilePageIsAdded(diaryId);

        assertThat(countRows("diary_page")).isZero();
    }

    @Test
    void deleteDiary_pageAddedWhileWaitingForLock_deletesDiary() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        deleteDiaryWhilePageIsAdded(diaryId);

        assertThat(countRows("diary")).isZero();
    }

    @Test
    void createDiary_characterDeletedBeforeCommit_returnsNotFound() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = createDiaryWhileCharacterIsDeleted(characterId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDiary_characterDeletedBeforeCommit_returnsCharacterNotFoundMessage() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = createDiaryWhileCharacterIsDeleted(characterId);

        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("Character not found");
    }

    @Test
    void createDiary_characterDeletedBeforeCommit_storesNoDiary() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        createDiaryWhileCharacterIsDeleted(characterId);

        assertThat(countRows("diary")).isZero();
    }

    @Test
    void updateDiary_twoParallelUpdates_neverReturnsServerError() {
        UUID diaryId = createDiaryWithOwner();

        List<Integer> statuses = ConcurrentCalls.statuses(updateDiaryInParallel(diaryId));

        assertThat(statuses).allMatch(status -> status < 500);
    }

    @Test
    void updateDiary_twoParallelUpdates_answerOkOrConflictOnly() {
        UUID diaryId = createDiaryWithOwner();

        List<Integer> statuses = ConcurrentCalls.statuses(updateDiaryInParallel(diaryId));

        assertThat(statuses).isSubsetOf(200, 409);
    }

    private MvcTestResult putBookmark(UUID diaryId, UUID pageId) {
        return mvc.put().uri("/diaries/{id}/bookmark", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pageId\":\"" + pageId + "\"}").exchange();
    }

    private UUID storedBookmark(UUID diaryId) {
        return jdbc.queryForObject("select bookmark_page_id from diary where id = ?", UUID.class, diaryId);
    }

    @Test
    void getDiaryBookmark_diaryWithoutBookmark_returnsNullFields() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.get().uri("/diaries/{id}/bookmark", diaryId)).bodyJson()
                .isStrictlyEqualTo("{\"pageId\":null,\"pageNumber\":null}");
    }

    @Test
    void getDiaryBookmark_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{id}/bookmark", othersDiary)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryBookmark_pageOfOwnDiary_storesBookmark() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);

        putBookmark(diaryId, pages.get(1));

        assertThat(storedBookmark(diaryId)).isEqualTo(pages.get(1));
    }

    @Test
    void updateDiaryBookmark_pageOfOwnDiary_returnsPageNumber() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);

        assertThat(putBookmark(diaryId, pages.get(1))).bodyJson().extractingPath("$.pageNumber").isEqualTo(2);
    }

    @Test
    void getDiaryBookmark_afterUpdate_returnsTheBookmarkedPage() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        putBookmark(diaryId, pages.get(2));

        assertThat(mvc.get().uri("/diaries/{id}/bookmark", diaryId)).bodyJson()
                .extractingPath("$.pageId").isEqualTo(pages.get(2).toString());
    }

    @Test
    void updateDiaryBookmark_secondBookmark_replacesTheFirst() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        putBookmark(diaryId, pages.get(0));

        putBookmark(diaryId, pages.get(2));

        assertThat(storedBookmark(diaryId)).isEqualTo(pages.get(2));
    }

    @Test
    void updateDiaryBookmark_pageOfAnotherDiary_returnsNotFound() {
        UUID diaryId = createDiaryWithOwner();
        UUID otherDiary = createDiary(jdbc.queryForObject("select character_id from diary where id = ?", UUID.class, diaryId), "Notes");
        UUID otherPage = createDiaryPage(otherDiary, "Elsewhere");

        assertThat(putBookmark(diaryId, otherPage)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryBookmark_pageOfAnotherDiary_storesNothing() {
        UUID diaryId = createDiaryWithOwner();
        UUID otherDiary = createDiary(jdbc.queryForObject("select character_id from diary where id = ?", UUID.class, diaryId), "Notes");
        UUID otherPage = createDiaryPage(otherDiary, "Elsewhere");

        putBookmark(diaryId, otherPage);

        assertThat(storedBookmark(diaryId)).isNull();
    }

    @Test
    void updateDiaryBookmark_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersPage = createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        assertThat(putBookmark(othersDiary, othersPage)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryBookmark_missingPageId_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.put().uri("/diaries/{id}/bookmark", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteDiaryBookmark_existingBookmark_clearsIt() {
        UUID diaryId = createDiaryWithOwner();
        putBookmark(diaryId, createDiaryPage(diaryId, "First"));

        mvc.delete().uri("/diaries/{id}/bookmark", diaryId).exchange();

        assertThat(storedBookmark(diaryId)).isNull();
    }

    @Test
    void deleteDiaryBookmark_withoutBookmark_returnsOk() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.delete().uri("/diaries/{id}/bookmark", diaryId)).hasStatusOk();
    }

    @Test
    void deleteDiaryBookmark_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.delete().uri("/diaries/{id}/bookmark", othersDiary)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDiaryPage_bookmarkedPage_clearsTheBookmark() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        putBookmark(diaryId, pages.get(1));

        mvc.delete().uri("/pages/{id}", pages.get(1)).exchange();

        assertThat(storedBookmark(diaryId)).isNull();
    }

    @Test
    void deleteDiaryPage_pageBeforeTheBookmark_keepsTheBookmarkOnTheSamePage() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        putBookmark(diaryId, pages.get(2));

        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(mvc.get().uri("/diaries/{id}/bookmark", diaryId)).bodyJson()
                .extractingPath("$['pageId','pageNumber']")
                .isEqualTo(Map.of("pageId", pages.get(2).toString(), "pageNumber", 2));
    }

    @Test
    void deleteDiary_withBookmark_deletesTheDiary() {
        UUID diaryId = createDiaryWithOwner();
        putBookmark(diaryId, createDiaryPage(diaryId, "First"));

        mvc.delete().uri("/diaries/{id}", diaryId).exchange();

        assertThat(countRows("diary")).isZero();
    }
}

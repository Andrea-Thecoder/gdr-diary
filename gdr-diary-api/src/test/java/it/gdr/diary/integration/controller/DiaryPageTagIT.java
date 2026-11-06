package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.ConcurrentCalls;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class DiaryPageTagIT extends AbstractIntegrationTest {

    private static final String LOCK_PAGE = "select id from diary_page where id = ? for update";

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private static String tagsBody(String... tags) {
        return "{\"tags\":[" + String.join(",", Arrays.stream(tags).map(tag -> "\"" + tag + "\"").toList()) + "]}";
    }

    private MvcTestResult putTags(UUID pageId, RequestPostProcessor user, String... tags) {
        return mvc.put().uri("/pages/{id}/tags", pageId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(tagsBody(tags)).exchange();
    }

    private MvcTestResult putTags(UUID pageId, String... tags) {
        return putTags(pageId, CURRENT_USER, tags);
    }

    private List<String> storedTags(UUID pageId) {
        return jdbc.queryForList("select name from diary_page_tag where page_id = ? order by lower(name)", String.class, pageId);
    }

    private long storedPageVersion(UUID pageId) {
        return jdbc.queryForObject("select version from diary_page where id = ?", Long.class, pageId);
    }

    private String storedPageText(UUID pageId) {
        return jdbc.queryForObject("select content->'content'->0->'content'->0->>'text' from diary_page where id = ?", String.class, pageId);
    }

    private List<UUID> storedTagIds(UUID pageId) {
        return jdbc.queryForList("select id from diary_page_tag where page_id = ? order by id", UUID.class, pageId);
    }

    private List<Map<String, Object>> storedTagRows(UUID pageId) {
        return jdbc.queryForList("select id, version from diary_page_tag where page_id = ? order by id", pageId);
    }

    private UUID taggedPage(String... tags) {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Page one");
        putTags(pageId, tags);
        return pageId;
    }

    private UUID othersTaggedPage(String... tags) {
        RequestPostProcessor other = TestJwt.otherUser();
        UUID diaryId = createDiaryWithOwner(other);
        UUID pageId = createDiaryPage(diaryId, "Secret", other);
        putTags(pageId, other, tags);
        return pageId;
    }

    @Test
    void updateDiaryPageTags_newTags_storesThem() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTags(pageId, "combat", "clues");

        assertThat(storedTags(pageId)).containsExactly("clues", "combat");
    }

    @Test
    void updateDiaryPageTags_newTags_returnsThemSortedIgnoringCase() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(mvc.put().uri("/pages/{id}/tags", pageId).contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody("clues", "Alpha", "beta")))
                .bodyJson().extractingPath("$.tags").isEqualTo(List.of("Alpha", "beta", "clues"));
    }

    @Test
    void updateDiaryPageTags_secondCall_replacesTheTags() {
        UUID pageId = taggedPage("combat", "clues");

        putTags(pageId, "clues", "npc");

        assertThat(storedTags(pageId)).containsExactly("clues", "npc");
    }

    @Test
    void updateDiaryPageTags_sameTagsAgain_leavesStoredRowsUntouched() {
        UUID pageId = taggedPage("combat", "clues");
        List<Map<String, Object>> before = storedTagRows(pageId);

        putTags(pageId, "clues", "combat");

        assertThat(storedTagRows(pageId)).isEqualTo(before);
    }

    @Test
    void updateDiaryPageTags_sameTagWithOtherCase_renamesTheStoredRow() {
        UUID pageId = taggedPage("combat");

        putTags(pageId, "Combat");

        assertThat(storedTags(pageId)).containsExactly("Combat");
    }

    @Test
    void updateDiaryPageTags_sameTagWithOtherCase_keepsTheSameRow() {
        UUID pageId = taggedPage("combat");
        List<UUID> before = storedTagIds(pageId);

        putTags(pageId, "Combat");

        assertThat(storedTagIds(pageId)).isEqualTo(before);
    }

    @Test
    void updateDiaryPageTags_emptyList_removesAllTags() {
        UUID pageId = taggedPage("combat", "clues");

        putTags(pageId);

        assertThat(storedTags(pageId)).isEmpty();
    }

    @Test
    void updateDiaryPageTags_namesDifferingByCase_storesTheFirstOnly() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTags(pageId, "Combat", "combat", "COMBAT");

        assertThat(storedTags(pageId)).containsExactly("Combat");
    }

    @Test
    void updateDiaryPageTags_namesWithSurroundingSpaces_storesThemTrimmed() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTags(pageId, "  combat ");

        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTags_validRequest_doesNotChangeThePageVersion() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");
        long before = storedPageVersion(pageId);

        putTags(pageId, "combat");

        assertThat(storedPageVersion(pageId)).isEqualTo(before);
    }

    @Test
    void updateDiaryPageTags_validRequest_keepsThePageContent() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTags(pageId, "combat");

        assertThat(storedPageText(pageId)).isEqualTo("Page one");
    }

    @Test
    void updateDiaryPageTags_sameNameOnTwoPages_storesBothRows() {
        UUID diaryId = createDiaryWithOwner();
        UUID first = createDiaryPage(diaryId, "Page one");
        UUID second = createDiaryPage(diaryId, "Page two");

        putTags(first, "combat");
        putTags(second, "combat");

        assertThat(countRows("diary_page_tag")).isEqualTo(2);
    }

    @Test
    void updateDiaryPageTags_elevenTags_returnsBadRequest() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");
        String[] tags = IntStream.rangeClosed(1, 11).mapToObj(number -> "tag" + number).toArray(String[]::new);

        assertThat(mvc.put().uri("/pages/{id}/tags", pageId).contentType(MediaType.APPLICATION_JSON).content(tagsBody(tags)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTags_elevenTags_keepsStoredTags() {
        UUID pageId = taggedPage("combat");
        String[] tags = IntStream.rangeClosed(1, 11).mapToObj(number -> "tag" + number).toArray(String[]::new);

        putTags(pageId, tags);

        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTags_tagOfThirtyOneCharacters_returnsBadRequest() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(mvc.put().uri("/pages/{id}/tags", pageId).contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody("a".repeat(31))))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTags_tagWithEmoji_isStored() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTags(pageId, "dragon 🐉");

        assertThat(storedTags(pageId)).containsExactly("dragon 🐉");
    }

    @Test
    void updateDiaryPageTags_unknownPage_returnsNotFound() {
        createDiaryWithOwner();

        assertThat(mvc.put().uri("/pages/{id}/tags", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody("combat")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTags_pageOfOtherUser_returnsNotFound() {
        UUID othersPage = othersTaggedPage("secret");

        assertThat(mvc.put().uri("/pages/{id}/tags", othersPage).contentType(MediaType.APPLICATION_JSON)
                .content(tagsBody("hacked")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTags_pageOfOtherUser_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");

        putTags(othersPage, "hacked");

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void updateDiaryPageTags_emptyListOnPageOfOtherUser_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");

        putTags(othersPage);

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void updateDiaryPageTags_twoParallelCallsWithSameTags_bothSucceed() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        List<Integer> statuses = ConcurrentCalls.statuses(
                ConcurrentCalls.fireAtOnce(2, index -> putTags(pageId, currentUser(), "combat", "clues")));

        assertThat(statuses).containsExactly(200, 200);
    }

    @Test
    void updateDiaryPageTags_twoParallelCallsWithSameTags_storeEachTagOnce() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        ConcurrentCalls.fireAtOnce(2, index -> putTags(pageId, currentUser(), "combat", "clues"));

        assertThat(storedTags(pageId)).containsExactly("clues", "combat");
    }

    private MvcTestResult putTagsWhilePageIsDeleted(UUID pageId) {
        try (BlockingTransaction deletion = BlockingTransaction.begin(jdbc)
                .execute(LOCK_PAGE, pageId)
                .execute("delete from diary_page_tag where page_id = ?", pageId)
                .execute("delete from diary_page where id = ?", pageId)) {
            CompletableFuture<MvcTestResult> put = deletion.startBlockedCall(() -> putTags(pageId, currentUser(), "combat"));
            deletion.commit();
            return put.join();
        }
    }

    @Test
    void updateDiaryPageTags_pageDeletedWhileWaitingForLock_returnsNotFound() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        MvcTestResult result = putTagsWhilePageIsDeleted(pageId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTags_pageDeletedWhileWaitingForLock_leavesNoTag() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTagsWhilePageIsDeleted(pageId);

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiaryPage_pageWithTags_deletesItsTags() {
        UUID pageId = taggedPage("combat", "clues");

        mvc.delete().uri("/pages/{id}", pageId).exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiaryPage_pageWithTags_keepsTagsOfOtherPages() {
        UUID diaryId = createDiaryWithOwner();
        UUID first = createDiaryPage(diaryId, "Page one");
        UUID second = createDiaryPage(diaryId, "Page two");
        putTags(first, "combat");
        putTags(second, "clues");

        mvc.delete().uri("/pages/{id}", first).exchange();

        assertThat(storedTags(second)).containsExactly("clues");
    }

    @Test
    void deleteDiaryPage_pageOfOtherUser_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");

        mvc.delete().uri("/pages/{id}", othersPage).exchange();

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void deleteDiary_diaryWithTaggedPages_deletesAllItsTags() {
        UUID diaryId = createDiaryWithOwner();
        putTags(createDiaryPage(diaryId, "Page one"), "combat");
        putTags(createDiaryPage(diaryId, "Page two"), "clues", "npc");

        mvc.delete().uri("/diaries/{id}", diaryId).exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiary_diaryWithTaggedPages_keepsTagsOfOtherDiary() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID doomed = createDiary(characterId, "Doomed");
        UUID kept = createDiary(characterId, "Kept");
        putTags(createDiaryPage(doomed, "Page one"), "combat");
        UUID keptPage = createDiaryPage(kept, "Page one");
        putTags(keptPage, "clues");

        mvc.delete().uri("/diaries/{id}", doomed).exchange();

        assertThat(storedTags(keptPage)).containsExactly("clues");
    }

    @Test
    void deleteDiary_diaryOfOtherUser_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");
        UUID othersDiary = jdbc.queryForObject("select diary_id from diary_page where id = ?", UUID.class, othersPage);

        mvc.delete().uri("/diaries/{id}", othersDiary).exchange();

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void deleteGameCharacter_characterWithTaggedPages_deletesTagsOfAllItsDiaries() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        putTags(createDiaryPage(createDiary(characterId, "First"), "Page one"), "combat");
        putTags(createDiaryPage(createDiary(characterId, "Second"), "Page one"), "clues");

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteGameCharacter_characterWithTaggedPages_keepsTagsOfOtherCharacter() {
        createAppUser();
        UUID doomed = createGameCharacter("Boromir");
        UUID kept = createGameCharacter("Aragorn");
        putTags(createDiaryPage(createDiary(doomed, "First"), "Page one"), "combat");
        UUID keptPage = createDiaryPage(createDiary(kept, "Second"), "Page one");
        putTags(keptPage, "clues");

        mvc.delete().uri("/characters/{id}", doomed).exchange();

        assertThat(storedTags(keptPage)).containsExactly("clues");
    }

    @Test
    void deleteAppUser_userWithTaggedPages_deletesAllTheirTags() {
        UUID diaryId = createDiaryWithOwner();
        putTags(createDiaryPage(diaryId, "Page one"), "combat");
        putTags(createDiaryPage(diaryId, "Page two"), "clues");

        mvc.delete().uri("/me").exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteAppUser_otherUserWithTaggedPages_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");
        createDiaryWithOwner();

        mvc.delete().uri("/me").exchange();

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    private UUID diaryOf(UUID pageId) {
        return jdbc.queryForObject("select diary_id from diary_page where id = ?", UUID.class, pageId);
    }

    private UUID characterOf(UUID diaryId) {
        return jdbc.queryForObject("select character_id from diary where id = ?", UUID.class, diaryId);
    }

    private UUID diaryWithTaggedPage(UUID characterId, String title, String... tags) {
        UUID diaryId = createDiary(characterId, title);
        putTags(createDiaryPage(diaryId, "Page one"), tags);
        return diaryId;
    }

    private UUID diaryWithTaggedPages(String[]... tagsOfPages) {
        UUID diaryId = createDiaryWithOwner();
        for (String[] tags : tagsOfPages) {
            putTags(createDiaryPage(diaryId, "Page"), tags);
        }
        return diaryId;
    }

    private MvcTestResult diaryTags(UUID diaryId, String query) {
        return mvc.get().uri("/diaries/{diaryId}/tags" + query, diaryId).exchange();
    }

    private MvcTestResult diariesWithTag(UUID characterId, String tag) {
        return mvc.get().uri("/characters/{characterId}/diaries?tag={tag}", characterId, tag).exchange();
    }

    private static String updateBody(String text, long version) {
        return "{\"content\":" + pageContent(text) + ",\"version\":" + version + "}";
    }

    @Test
    void findAllDiaryTagsByDiary_taggedPages_returnsTagsWithPageNumbers() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"combat", "clues"}, new String[]{"Combat"});

        MvcTestResult result = diaryTags(diaryId, "");

        assertThat(result).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("clues", "combat"));
        assertThat(result).bodyJson().extractingPath("$.list[*].pageNumbers").isEqualTo(List.of(List.of(1), List.of(1, 2)));
    }

    @Test
    void findAllDiaryTagsByDiary_noTags_returnsEmptyList() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(diaryTags(diaryId, "")).bodyJson().extractingPath("$.totalRows").isEqualTo(0);
    }

    @Test
    void findAllDiaryTagsByDiary_nameFilter_returnsOnlyMatchingTags() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"Combat", "clues", "npc"});

        assertThat(diaryTags(diaryId, "?name=COMB")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Combat"));
    }

    @Test
    void findAllDiaryTagsByDiary_nameWithPercent_matchesItLiterally() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"100%", "1000"});

        assertThat(mvc.get().uri("/diaries/{diaryId}/tags?name={name}", diaryId, "0%").exchange())
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("100%"));
    }

    @Test
    void findAllDiaryTagsByDiary_nameWithUnderscore_matchesItLiterally() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"a_b", "axb"});

        assertThat(mvc.get().uri("/diaries/{diaryId}/tags?name={name}", diaryId, "a_b").exchange())
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("a_b"));
    }

    @Test
    void findAllDiaryTagsByDiary_secondPage_returnsRemainingTags() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"a", "b", "c"});

        MvcTestResult result = diaryTags(diaryId, "?page=2&size=2");

        assertThat(result).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("c"));
        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(3);
    }

    @Test
    void findAllDiaryTagsByDiary_sizeAboveHundred_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(diaryTags(diaryId, "?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryTagsByDiary_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = diaryOf(othersTaggedPage("secret"));

        assertThat(diaryTags(othersDiary, "")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllDiaryTagsByDiary_diaryOfOtherUser_doesNotLeakTheirTags() {
        UUID othersDiary = diaryOf(othersTaggedPage("secret"));

        assertThat(responseBody(diaryTags(othersDiary, "?name=secret"))).doesNotContain("secret\"");
    }

    @Test
    void findAllDiaryPagesByDiary_taggedPage_returnsItsTags() {
        UUID diaryId = createDiaryWithOwner();
        putTags(createDiaryPage(diaryId, "Page one"), "combat", "clues");
        createDiaryPage(diaryId, "Page two");

        MvcTestResult result = mvc.get().uri("/diaries/{diaryId}/pages", diaryId).exchange();

        assertThat(result).bodyJson().extractingPath("$.list[*].tags").isEqualTo(List.of(List.of("clues", "combat"), List.of()));
    }

    @Test
    void createDiaryPage_newPage_returnsEmptyTags() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent("Page one") + "}"))
                .bodyJson().extractingPath("$.tags").isEqualTo(List.of());
    }

    @Test
    void updateDiaryPage_taggedPage_returnsItsTagsAndKeepsThem() {
        UUID pageId = taggedPage("combat", "clues");

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Final", storedPageVersion(pageId))))
                .bodyJson().extractingPath("$.tags").isEqualTo(List.of("clues", "combat"));
        assertThat(storedTags(pageId)).containsExactly("clues", "combat");
    }

    @Test
    void findAllDiariesByCharacter_tag_returnsOnlyDiariesWithThatTag() {
        UUID first = createDiaryWithOwner();
        UUID characterId = characterOf(first);
        putTags(createDiaryPage(first, "Page"), "combat");
        diaryWithTaggedPage(characterId, "Second", "clues");
        createDiary(characterId, "Third");

        MvcTestResult result = diariesWithTag(characterId, "comb");

        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.list[0].id").isEqualTo(first.toString());
    }

    @Test
    void findAllDiariesByCharacter_tagWithOtherCase_returnsTheDiary() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"Combat"});

        assertThat(diariesWithTag(characterOf(diaryId), "cOMBat")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_diaryWithTagOnSeveralPages_returnsItOnce() {
        UUID diaryId = diaryWithTaggedPages(new String[]{"combat"}, new String[]{"combat boss"});

        assertThat(diariesWithTag(characterOf(diaryId), "combat")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_tagWithPercent_matchesItLiterally() {
        UUID first = diaryWithTaggedPages(new String[]{"100%"});
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "1000");

        MvcTestResult result = diariesWithTag(characterId, "0%");

        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.list[0].id").isEqualTo(first.toString());
    }

    @Test
    void findAllDiariesByCharacter_tagWithUnderscore_matchesItLiterally() {
        UUID first = diaryWithTaggedPages(new String[]{"a_b"});
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "axb");

        assertThat(diariesWithTag(characterId, "a_b")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_tagAndTitle_combinesBothFilters() {
        UUID first = createDiaryWithOwner();
        UUID characterId = characterOf(first);
        putTags(createDiaryPage(first, "Page"), "combat");
        diaryWithTaggedPage(characterId, "Second", "combat");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?tag=combat&title=Second", characterId).exchange())
                .bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_sameTagOfOtherUser_doesNotReturnTheirDiary() {
        othersTaggedPage("shared");
        UUID mine = diaryWithTaggedPages(new String[]{"shared"});

        MvcTestResult result = diariesWithTag(characterOf(mine), "shared");

        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.list[0].id").isEqualTo(mine.toString());
    }

    @Test
    void findAllDiariesByCharacter_tagOnCharacterOfOtherUser_returnsNotFound() {
        UUID othersCharacter = characterOf(diaryOf(othersTaggedPage("secret")));

        assertThat(diariesWithTag(othersCharacter, "secret")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllDiariesByCharacter_tagLongerThanThirtyCharacters_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(diariesWithTag(characterOf(diaryId), "a".repeat(31))).hasStatus(HttpStatus.BAD_REQUEST);
    }
}

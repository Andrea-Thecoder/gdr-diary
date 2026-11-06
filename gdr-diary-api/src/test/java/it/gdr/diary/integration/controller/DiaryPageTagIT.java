package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.ConcurrentCalls;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.util.Arrays;
import java.util.List;
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

    private static final String LOCK_DIARY = "select id from diary where id = ? for update";

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private static String tagBody(String tag) {
        return tag == null ? "{\"tag\":null}" : "{\"tag\":\"" + tag + "\"}";
    }

    private MvcTestResult putTag(UUID pageId, RequestPostProcessor user, String tag) {
        return mvc.put().uri("/pages/{id}/tag", pageId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(tagBody(tag)).exchange();
    }

    private MvcTestResult putTag(UUID pageId, String tag) {
        return putTag(pageId, CURRENT_USER, tag);
    }

    private List<String> storedTags(UUID pageId) {
        return jdbc.queryForList("select name from diary_page_tag where page_id = ?", String.class, pageId);
    }

    private List<UUID> storedTagIds(UUID pageId) {
        return jdbc.queryForList("select id from diary_page_tag where page_id = ?", UUID.class, pageId);
    }

    private UUID storedTagDiary(UUID pageId) {
        return jdbc.queryForObject("select diary_id from diary_page_tag where page_id = ?", UUID.class, pageId);
    }

    private long storedPageVersion(UUID pageId) {
        return jdbc.queryForObject("select version from diary_page where id = ?", Long.class, pageId);
    }

    private String storedPageText(UUID pageId) {
        return jdbc.queryForObject("select content->'content'->0->'content'->0->>'text' from diary_page where id = ?", String.class, pageId);
    }

    private UUID taggedPage(String tag) {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Page one");
        putTag(pageId, tag);
        return pageId;
    }

    private UUID othersTaggedPage(String tag) {
        RequestPostProcessor other = TestJwt.otherUser();
        UUID diaryId = createDiaryWithOwner(other);
        UUID pageId = createDiaryPage(diaryId, "Secret", other);
        putTag(pageId, other, tag);
        return pageId;
    }

    private UUID diaryOf(UUID pageId) {
        return jdbc.queryForObject("select diary_id from diary_page where id = ?", UUID.class, pageId);
    }

    private UUID characterOf(UUID diaryId) {
        return jdbc.queryForObject("select character_id from diary where id = ?", UUID.class, diaryId);
    }

    /** Diary with a page for each given tag (null means a page without tag), in this order. */
    private UUID diaryWithTaggedPages(String... tagsOfPages) {
        UUID diaryId = createDiaryWithOwner();
        for (String tag : tagsOfPages) {
            putTag(createDiaryPage(diaryId, "Page"), tag);
        }
        return diaryId;
    }

    private UUID diaryWithTaggedPage(UUID characterId, String title, String tag) {
        UUID diaryId = createDiary(characterId, title);
        putTag(createDiaryPage(diaryId, "Page one"), tag);
        return diaryId;
    }

    private List<UUID> pagesWithTags(UUID diaryId, String... tags) {
        return Arrays.stream(tags).map(tag -> {
            UUID pageId = createDiaryPage(diaryId, "Page");
            putTag(pageId, tag);
            return pageId;
        }).toList();
    }

    @Test
    void updateDiaryPageTag_newTag_storesIt() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTag(pageId, "combat");

        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_newTag_returnsIt() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(putTag(pageId, "combat")).bodyJson().extractingPath("$.tag").isEqualTo("combat");
    }

    @Test
    void updateDiaryPageTag_newTag_storesTheDiaryOfThePage() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Page one");

        putTag(pageId, "combat");

        assertThat(storedTagDiary(pageId)).isEqualTo(diaryId);
    }

    @Test
    void updateDiaryPageTag_secondCall_replacesTheTagWithoutAddingOne() {
        UUID pageId = taggedPage("combat");

        putTag(pageId, "clues");

        assertThat(storedTags(pageId)).containsExactly("clues");
    }

    @Test
    void updateDiaryPageTag_secondCall_keepsTheSameRow() {
        UUID pageId = taggedPage("combat");
        List<UUID> before = storedTagIds(pageId);

        putTag(pageId, "clues");

        assertThat(storedTagIds(pageId)).isEqualTo(before);
    }

    @Test
    void updateDiaryPageTag_sameTagAgain_leavesStoredRowUntouched() {
        UUID pageId = taggedPage("combat");
        long before = jdbc.queryForObject("select version from diary_page_tag where page_id = ?", Long.class, pageId);

        putTag(pageId, "combat");

        assertThat(jdbc.queryForObject("select version from diary_page_tag where page_id = ?", Long.class, pageId)).isEqualTo(before);
    }

    @Test
    void updateDiaryPageTag_sameTagWithOtherCase_renamesTheStoredRow() {
        UUID pageId = taggedPage("combat");

        putTag(pageId, "Combat");

        assertThat(storedTags(pageId)).containsExactly("Combat");
    }

    @Test
    void updateDiaryPageTag_nullTag_removesTheTag() {
        UUID pageId = taggedPage("combat");

        putTag(pageId, null);

        assertThat(storedTags(pageId)).isEmpty();
    }

    @Test
    void updateDiaryPageTag_nullTag_returnsNullTag() {
        UUID pageId = taggedPage("combat");

        assertThat(putTag(pageId, null)).bodyJson().extractingPath("$.tag").isNull();
    }

    @Test
    void updateDiaryPageTag_nullTagOnPageWithoutTag_returnsOk() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(putTag(pageId, null)).hasStatusOk();
    }

    @Test
    void updateDiaryPageTag_nullTag_keepsTagOfOtherPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = pagesWithTags(diaryId, "combat", "clues");

        putTag(pages.get(0), null);

        assertThat(storedTags(pages.get(1))).containsExactly("clues");
    }

    @Test
    void updateDiaryPageTag_nameWithSurroundingSpaces_storesItTrimmed() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTag(pageId, "  combat ");

        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_validRequest_doesNotChangeThePageVersion() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");
        long before = storedPageVersion(pageId);

        putTag(pageId, "combat");

        assertThat(storedPageVersion(pageId)).isEqualTo(before);
    }

    @Test
    void updateDiaryPageTag_validRequest_keepsThePageContent() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTag(pageId, "combat");

        assertThat(storedPageText(pageId)).isEqualTo("Page one");
    }

    @Test
    void updateDiaryPageTag_sameNameOnTwoPages_storesBothRows() {
        UUID diaryId = createDiaryWithOwner();

        pagesWithTags(diaryId, "combat", "combat");

        assertThat(countRows("diary_page_tag")).isEqualTo(2);
    }

    @Test
    void updateDiaryPageTag_sameNameWithOtherCaseOnOtherPage_keepsTheSpellingOfTheDiary() {
        UUID diaryId = createDiaryWithOwner();

        List<UUID> pages = pagesWithTags(diaryId, "Combat", "combat");

        assertThat(storedTags(pages.get(1))).containsExactly("Combat");
    }

    @Test
    void updateDiaryPageTag_sameNameWithOtherCaseOnOtherPage_returnsTheSpellingOfTheDiary() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, "Combat");
        UUID second = createDiaryPage(diaryId, "Page two");

        assertThat(putTag(second, "combat")).bodyJson().extractingPath("$.tag").isEqualTo("Combat");
    }

    @Test
    void updateDiaryPageTag_sameNameInOtherDiary_isUnrelated() {
        UUID first = createDiaryWithOwner();
        pagesWithTags(first, "Combat");
        UUID second = createDiary(characterOf(first), "Second");
        UUID page = createDiaryPage(second, "Page");

        putTag(page, "combat");

        assertThat(storedTags(page)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_tenDifferentTagsInTheDiary_areAccepted() {
        UUID diaryId = createDiaryWithOwner();

        pagesWithTags(diaryId, IntStream.rangeClosed(1, 10).mapToObj(number -> "tag" + number).toArray(String[]::new));

        assertThat(countRows("diary_page_tag")).isEqualTo(10);
    }

    @Test
    void updateDiaryPageTag_eleventhDifferentTag_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, IntStream.rangeClosed(1, 10).mapToObj(number -> "tag" + number).toArray(String[]::new));
        UUID extra = createDiaryPage(diaryId, "Extra");

        assertThat(putTag(extra, "tag11")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTag_eleventhDifferentTag_storesNothing() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, IntStream.rangeClosed(1, 10).mapToObj(number -> "tag" + number).toArray(String[]::new));
        UUID extra = createDiaryPage(diaryId, "Extra");

        putTag(extra, "tag11");

        assertThat(storedTags(extra)).isEmpty();
    }

    @Test
    void updateDiaryPageTag_diaryWithTenTags_acceptsOneOfThemOnAnotherPageIgnoringCase() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, IntStream.rangeClosed(1, 10).mapToObj(number -> "tag" + number).toArray(String[]::new));
        UUID extra = createDiaryPage(diaryId, "Extra");

        putTag(extra, "TAG3");

        assertThat(storedTags(extra)).containsExactly("tag3");
    }

    @Test
    void updateDiaryPageTag_diaryWithTenTags_acceptsReplacingTheOnlyPageOfATag() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = pagesWithTags(diaryId, IntStream.rangeClosed(1, 10).mapToObj(number -> "tag" + number).toArray(String[]::new));

        putTag(pages.get(0), "brandnew");

        assertThat(storedTags(pages.get(0))).containsExactly("brandnew");
    }

    @Test
    void updateDiaryPageTag_tenTagsInOtherDiaryOfTheUser_doNotCount() {
        UUID first = createDiaryWithOwner();
        pagesWithTags(first, IntStream.rangeClosed(1, 10).mapToObj(number -> "tag" + number).toArray(String[]::new));
        UUID second = createDiary(characterOf(first), "Second");
        UUID page = createDiaryPage(second, "Page");

        assertThat(putTag(page, "other")).hasStatusOk();
    }

    @Test
    void updateDiaryPageTag_tagOfThirtyOneCharacters_returnsBadRequest() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(putTag(pageId, "a".repeat(31))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTag_tagWithEmoji_isStored() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTag(pageId, "dragon \uD83D\uDC09");

        assertThat(storedTags(pageId)).containsExactly("dragon \uD83D\uDC09");
    }

    @Test
    void updateDiaryPageTag_unknownPage_returnsNotFound() {
        createDiaryWithOwner();

        assertThat(putTag(UUID.randomUUID(), "combat")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTag_pageOfOtherUser_returnsNotFound() {
        UUID othersPage = othersTaggedPage("secret");

        assertThat(putTag(othersPage, "hacked")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTag_pageOfOtherUser_keepsTheirTag() {
        UUID othersPage = othersTaggedPage("secret");

        putTag(othersPage, "hacked");

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void updateDiaryPageTag_nullTagOnPageOfOtherUser_keepsTheirTag() {
        UUID othersPage = othersTaggedPage("secret");

        putTag(othersPage, null);

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void updateDiaryPageTag_pageOfOtherUserWithoutTag_getsNoTag() {
        RequestPostProcessor other = TestJwt.otherUser();
        UUID othersPage = createDiaryPage(createDiaryWithOwner(other), "Secret", other);

        putTag(othersPage, "hacked");

        assertThat(storedTags(othersPage)).isEmpty();
    }

    @Test
    void updateDiaryPageTag_twoParallelCallsOnTheSamePage_bothSucceed() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        List<Integer> statuses = ConcurrentCalls.statuses(
                ConcurrentCalls.fireAtOnce(2, index -> putTag(pageId, currentUser(), "combat")));

        assertThat(statuses).containsExactly(200, 200);
    }

    @Test
    void updateDiaryPageTag_twoParallelCallsOnTheSamePage_storeOneRow() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        ConcurrentCalls.fireAtOnce(2, index -> putTag(pageId, currentUser(), "combat"));

        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_parallelCallsOnDifferentPagesWithTheEleventhTag_storeAtMostTenTags() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, IntStream.rangeClosed(1, 9).mapToObj(number -> "tag" + number).toArray(String[]::new));
        UUID first = createDiaryPage(diaryId, "First");
        UUID second = createDiaryPage(diaryId, "Second");

        ConcurrentCalls.fireAtOnce(2, index -> putTag(index == 0 ? first : second, currentUser(), index == 0 ? "tag10" : "tag11"));

        assertThat(jdbc.queryForObject("select count(distinct lower(name)) from diary_page_tag where diary_id = ?", Long.class, diaryId))
                .isEqualTo(10L);
    }

    private MvcTestResult putTagWhilePageIsDeleted(UUID pageId) {
        UUID diaryId = diaryOf(pageId);
        try (BlockingTransaction deletion = BlockingTransaction.begin(jdbc)
                .execute(LOCK_DIARY, diaryId)
                .execute("delete from diary_page_tag where page_id = ?", pageId)
                .execute("delete from diary_page where id = ?", pageId)) {
            CompletableFuture<MvcTestResult> put = deletion.startBlockedCall(() -> putTag(pageId, currentUser(), "combat"));
            deletion.commit();
            return put.join();
        }
    }

    @Test
    void updateDiaryPageTag_pageDeletedWhileWaitingForLock_returnsNotFound() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        MvcTestResult result = putTagWhilePageIsDeleted(pageId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTag_pageDeletedWhileWaitingForLock_leavesNoTag() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTagWhilePageIsDeleted(pageId);

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiaryPage_pageWithTags_deletesItsTags() {
        UUID pageId = taggedPage("combat");

        mvc.delete().uri("/pages/{id}", pageId).exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiaryPage_pageWithTags_keepsTagsOfOtherPages() {
        UUID diaryId = createDiaryWithOwner();
        UUID first = createDiaryPage(diaryId, "Page one");
        UUID second = createDiaryPage(diaryId, "Page two");
        putTag(first, "combat");
        putTag(second, "clues");

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
        putTag(createDiaryPage(diaryId, "Page one"), "combat");
        putTag(createDiaryPage(diaryId, "Page two"), "npc");

        mvc.delete().uri("/diaries/{id}", diaryId).exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiary_diaryWithTaggedPages_keepsTagsOfOtherDiary() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID doomed = createDiary(characterId, "Doomed");
        UUID kept = createDiary(characterId, "Kept");
        putTag(createDiaryPage(doomed, "Page one"), "combat");
        UUID keptPage = createDiaryPage(kept, "Page one");
        putTag(keptPage, "clues");

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
        putTag(createDiaryPage(createDiary(characterId, "First"), "Page one"), "combat");
        putTag(createDiaryPage(createDiary(characterId, "Second"), "Page one"), "clues");

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteGameCharacter_characterWithTaggedPages_keepsTagsOfOtherCharacter() {
        createAppUser();
        UUID doomed = createGameCharacter("Boromir");
        UUID kept = createGameCharacter("Aragorn");
        putTag(createDiaryPage(createDiary(doomed, "First"), "Page one"), "combat");
        UUID keptPage = createDiaryPage(createDiary(kept, "Second"), "Page one");
        putTag(keptPage, "clues");

        mvc.delete().uri("/characters/{id}", doomed).exchange();

        assertThat(storedTags(keptPage)).containsExactly("clues");
    }

    @Test
    void deleteAppUser_userWithTaggedPages_deletesAllTheirTags() {
        UUID diaryId = createDiaryWithOwner();
        putTag(createDiaryPage(diaryId, "Page one"), "combat");
        putTag(createDiaryPage(diaryId, "Page two"), "clues");

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
        UUID diaryId = diaryWithTaggedPages("combat", "clues", "Combat");

        MvcTestResult result = diaryTags(diaryId, "");

        assertThat(result).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("clues", "combat"));
        assertThat(result).bodyJson().extractingPath("$.list[*].pageNumbers").isEqualTo(List.of(List.of(2), List.of(1, 3)));
    }

    @Test
    void findAllDiaryTagsByDiary_pagesWithoutTag_areNotListed() {
        UUID diaryId = diaryWithTaggedPages("combat", null);

        assertThat(diaryTags(diaryId, "")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiaryTagsByDiary_noTags_returnsEmptyList() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(diaryTags(diaryId, "")).bodyJson().extractingPath("$.totalRows").isEqualTo(0);
    }

    @Test
    void findAllDiaryTagsByDiary_nameFilter_returnsOnlyMatchingTags() {
        UUID diaryId = diaryWithTaggedPages("Combat", "clues", "npc");

        assertThat(diaryTags(diaryId, "?name=COMB")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Combat"));
    }

    @Test
    void findAllDiaryTagsByDiary_nameWithPercent_matchesItLiterally() {
        UUID diaryId = diaryWithTaggedPages("100%", "1000");

        assertThat(mvc.get().uri("/diaries/{diaryId}/tags?name={name}", diaryId, "0%").exchange())
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("100%"));
    }

    @Test
    void findAllDiaryTagsByDiary_nameWithUnderscore_matchesItLiterally() {
        UUID diaryId = diaryWithTaggedPages("a_b", "axb");

        assertThat(mvc.get().uri("/diaries/{diaryId}/tags?name={name}", diaryId, "a_b").exchange())
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("a_b"));
    }

    @Test
    void findAllDiaryTagsByDiary_secondPage_returnsRemainingTags() {
        UUID diaryId = diaryWithTaggedPages("a", "b", "c");

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
    void findAllDiaryPagesByDiary_taggedPage_returnsItsTag() {
        UUID diaryId = createDiaryWithOwner();
        putTag(createDiaryPage(diaryId, "Page one"), "combat");
        createDiaryPage(diaryId, "Page two");

        MvcTestResult result = mvc.get().uri("/diaries/{diaryId}/pages", diaryId).exchange();

        assertThat(result).bodyJson().extractingPath("$.list[*].tag").isEqualTo(Arrays.asList("combat", null));
    }

    @Test
    void createDiaryPage_newPage_returnsNullTag() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent("Page one") + "}"))
                .bodyJson().extractingPath("$.tag").isNull();
    }

    @Test
    void updateDiaryPage_taggedPage_returnsItsTagAndKeepsIt() {
        UUID pageId = taggedPage("combat");

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Final", storedPageVersion(pageId))))
                .bodyJson().extractingPath("$.tag").isEqualTo("combat");
        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void findAllDiariesByCharacter_tag_returnsOnlyDiariesWithThatTag() {
        UUID first = diaryWithTaggedPages("combat");
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "clues");
        createDiary(characterId, "Third");

        MvcTestResult result = diariesWithTag(characterId, "comb");

        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.list[0].id").isEqualTo(first.toString());
    }

    @Test
    void findAllDiariesByCharacter_tagWithOtherCase_returnsTheDiary() {
        UUID diaryId = diaryWithTaggedPages("Combat");

        assertThat(diariesWithTag(characterOf(diaryId), "cOMBat")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_diaryWithTagOnSeveralPages_returnsItOnce() {
        UUID diaryId = diaryWithTaggedPages("combat", "combat", "combat boss");

        assertThat(diariesWithTag(characterOf(diaryId), "combat")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_tagWithPercent_matchesItLiterally() {
        UUID first = diaryWithTaggedPages("100%");
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "1000");

        MvcTestResult result = diariesWithTag(characterId, "0%");

        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.list[0].id").isEqualTo(first.toString());
    }

    @Test
    void findAllDiariesByCharacter_tagWithUnderscore_matchesItLiterally() {
        UUID first = diaryWithTaggedPages("a_b");
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "axb");

        assertThat(diariesWithTag(characterId, "a_b")).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_tagAndTitle_combinesBothFilters() {
        UUID first = diaryWithTaggedPages("combat");
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "combat");

        assertThat(mvc.get().uri("/characters/{characterId}/diaries?tag=combat&title=Second", characterId).exchange())
                .bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllDiariesByCharacter_sameTagOfOtherUser_doesNotReturnTheirDiary() {
        othersTaggedPage("shared");
        UUID mine = diaryWithTaggedPages("shared");

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

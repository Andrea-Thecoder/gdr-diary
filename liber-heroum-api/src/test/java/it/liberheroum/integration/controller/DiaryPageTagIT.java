package it.liberheroum.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.liberheroum.integration.support.AbstractIntegrationTest;
import it.liberheroum.integration.support.BlockingTransaction;
import it.liberheroum.integration.support.ConcurrentCalls;
import it.liberheroum.integration.support.TestJwt;
import it.liberheroum.integration.support.WithMockJwt;
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

    private static String[] tagNames(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> "tag" + number).toArray(String[]::new);
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
    void updateDiaryPageTag_newTag_storesTagOfThePageAndDiaryWithoutTouchingThePage() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Page one");
        long versionBefore = storedPageVersion(pageId);

        MvcTestResult result = putTag(pageId, "combat");

        assertThat(result).bodyJson().extractingPath("$.tag").isEqualTo("combat");
        assertThat(storedTags(pageId)).containsExactly("combat");
        assertThat(storedTagDiary(pageId)).isEqualTo(diaryId);
        assertThat(storedPageVersion(pageId)).isEqualTo(versionBefore);
        assertThat(storedPageText(pageId)).isEqualTo("Page one");
    }

    @Test
    void updateDiaryPageTag_secondCall_replacesTheTagKeepingTheSameRow() {
        UUID pageId = taggedPage("combat");
        List<UUID> before = storedTagIds(pageId);

        putTag(pageId, "clues");

        assertThat(storedTags(pageId)).containsExactly("clues");
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
    void updateDiaryPageTag_nullTag_removesOnlyThatTagAndReturnsNullTag() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = pagesWithTags(diaryId, "combat", "clues");

        MvcTestResult result = putTag(pages.get(0), null);

        assertThat(result).bodyJson().extractingPath("$.tag").isNull();
        assertThat(storedTags(pages.get(0))).isEmpty();
        assertThat(storedTags(pages.get(1))).containsExactly("clues");
    }

    @Test
    void updateDiaryPageTag_nullTagOnPageWithoutTag_returnsOk() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(putTag(pageId, null)).hasStatusOk();
    }

    @Test
    void updateDiaryPageTag_nameWithSurroundingSpaces_storesItTrimmed() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTag(pageId, "  combat ");

        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_tagWithEmoji_isStored() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        putTag(pageId, "dragon 🐉");

        assertThat(storedTags(pageId)).containsExactly("dragon 🐉");
    }

    @Test
    void updateDiaryPageTag_sameNameOnTwoPages_storesBothRows() {
        UUID diaryId = createDiaryWithOwner();

        pagesWithTags(diaryId, "combat", "combat");

        assertThat(countRows("diary_page_tag")).isEqualTo(2);
    }

    @Test
    void updateDiaryPageTag_sameNameWithOtherCaseOnOtherPage_storesAndReturnsTheSpellingOfTheDiary() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> first = pagesWithTags(diaryId, "Combat");
        UUID second = createDiaryPage(diaryId, "Page two");

        MvcTestResult result = putTag(second, "combat");

        assertThat(result).bodyJson().extractingPath("$.tag").isEqualTo("Combat");
        assertThat(storedTags(second)).containsExactly("Combat");
        assertThat(storedTags(first.get(0))).containsExactly("Combat");
    }

    @Test
    void updateDiaryPageTag_tagsOfOtherDiaryOfTheUser_doNotCountNorGiveTheSpelling() {
        UUID first = createDiaryWithOwner();
        pagesWithTags(first, "Combat", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10");
        UUID second = createDiary(characterOf(first), "Second");
        UUID page = createDiaryPage(second, "Page");

        MvcTestResult result = putTag(page, "combat");

        assertThat(result).hasStatusOk();
        assertThat(storedTags(page)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_tenDifferentTagsInTheDiary_areAccepted() {
        UUID diaryId = createDiaryWithOwner();

        pagesWithTags(diaryId, tagNames(10));

        assertThat(countRows("diary_page_tag")).isEqualTo(10);
    }

    @Test
    void updateDiaryPageTag_eleventhDifferentTag_returnsBadRequestAndStoresNothing() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, tagNames(10));
        UUID extra = createDiaryPage(diaryId, "Extra");

        MvcTestResult result = putTag(extra, "tag11");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(storedTags(extra)).isEmpty();
    }

    @Test
    void updateDiaryPageTag_diaryWithTenTags_acceptsOneOfThemOnAnotherPageIgnoringCase() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, tagNames(10));
        UUID extra = createDiaryPage(diaryId, "Extra");

        putTag(extra, "TAG3");

        assertThat(storedTags(extra)).containsExactly("tag3");
    }

    @Test
    void updateDiaryPageTag_diaryWithTenTags_acceptsReplacingTheOnlyPageOfATag() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = pagesWithTags(diaryId, tagNames(10));

        putTag(pages.get(0), "brandnew");

        assertThat(storedTags(pages.get(0))).containsExactly("brandnew");
    }

    @Test
    void updateDiaryPageTag_tagOfThirtyOneCharacters_returnsBadRequest() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        assertThat(putTag(pageId, "a".repeat(31))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPageTag_unknownPage_returnsNotFound() {
        createDiaryWithOwner();

        assertThat(putTag(UUID.randomUUID(), "combat")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPageTag_pageOfOtherUser_returnsNotFoundAndKeepsTags() {
        UUID taggedPage = othersTaggedPage("secret");
        RequestPostProcessor other = TestJwt.otherUser();
        UUID untaggedPage = createDiaryPage(diaryOf(taggedPage), "Other page", other);

        List<Integer> statuses = List.of(
                putTag(taggedPage, "hacked").getResponse().getStatus(),
                putTag(taggedPage, null).getResponse().getStatus(),
                putTag(untaggedPage, "hacked").getResponse().getStatus());

        assertThat(statuses).containsOnly(404);
        assertThat(storedTags(taggedPage)).containsExactly("secret");
        assertThat(storedTags(untaggedPage)).isEmpty();
    }

    @Test
    void updateDiaryPageTag_twoParallelCallsOnTheSamePage_bothSucceedAndStoreOneRow() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        List<Integer> statuses = ConcurrentCalls.statuses(
                ConcurrentCalls.fireAtOnce(2, index -> putTag(pageId, currentUser(), "combat")));

        assertThat(statuses).containsExactly(200, 200);
        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void updateDiaryPageTag_parallelCallsOnDifferentPagesWithTheEleventhTag_storeAtMostTenTags() {
        UUID diaryId = createDiaryWithOwner();
        pagesWithTags(diaryId, tagNames(9));
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
    void updateDiaryPageTag_pageDeletedWhileWaitingForLock_returnsNotFoundAndLeavesNoTag() {
        UUID pageId = createDiaryPage(createDiaryWithOwner(), "Page one");

        MvcTestResult result = putTagWhilePageIsDeleted(pageId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(countRows("diary_page_tag")).isZero();
    }

    @Test
    void deleteDiaryPage_pageWithTags_deletesItsTagsAndKeepsTagsOfOtherPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = pagesWithTags(diaryId, "combat", "clues");

        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(storedTags(pages.get(1))).containsExactly("clues");
        assertThat(countRows("diary_page_tag")).isEqualTo(1);
    }

    @Test
    void deleteDiaryPage_pageOfOtherUser_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");

        mvc.delete().uri("/pages/{id}", othersPage).exchange();

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void deleteDiary_diaryWithTaggedPages_deletesItsTagsAndKeepsTagsOfOtherDiary() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID doomed = createDiary(characterId, "Doomed");
        UUID kept = createDiary(characterId, "Kept");
        putTag(createDiaryPage(doomed, "Page one"), "combat");
        putTag(createDiaryPage(doomed, "Page two"), "npc");
        UUID keptPage = createDiaryPage(kept, "Page one");
        putTag(keptPage, "clues");

        mvc.delete().uri("/diaries/{id}", doomed).exchange();

        assertThat(storedTags(keptPage)).containsExactly("clues");
        assertThat(countRows("diary_page_tag")).isEqualTo(1);
    }

    @Test
    void deleteDiary_diaryOfOtherUser_keepsTheirTags() {
        UUID othersPage = othersTaggedPage("secret");

        mvc.delete().uri("/diaries/{id}", diaryOf(othersPage)).exchange();

        assertThat(storedTags(othersPage)).containsExactly("secret");
    }

    @Test
    void deleteGameCharacter_characterWithTaggedPages_deletesTagsOfAllItsDiariesAndKeepsOtherCharacter() {
        createAppUser();
        UUID doomed = createGameCharacter("Boromir");
        UUID kept = createGameCharacter("Aragorn");
        putTag(createDiaryPage(createDiary(doomed, "First"), "Page one"), "combat");
        putTag(createDiaryPage(createDiary(doomed, "Second"), "Page one"), "npc");
        UUID keptPage = createDiaryPage(createDiary(kept, "Third"), "Page one");
        putTag(keptPage, "clues");

        mvc.delete().uri("/characters/{id}", doomed).exchange();

        assertThat(storedTags(keptPage)).containsExactly("clues");
        assertThat(countRows("diary_page_tag")).isEqualTo(1);
    }

    @Test
    void deleteAppUser_userWithTaggedPages_deletesTheirTagsAndKeepsTagsOfOtherUser() {
        UUID othersPage = othersTaggedPage("secret");
        UUID diaryId = createDiaryWithOwner();
        putTag(createDiaryPage(diaryId, "Page one"), "combat");
        putTag(createDiaryPage(diaryId, "Page two"), "clues");

        mvc.delete().uri("/me").exchange();

        assertThat(storedTags(othersPage)).containsExactly("secret");
        assertThat(countRows("diary_page_tag")).isEqualTo(1);
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
    void findAllDiaryTagsByDiary_taggedPages_listsTagsByFirstPageWithPageNumbersIgnoringOtherDiariesAndUntaggedPages() {
        UUID diaryId = diaryWithTaggedPages(null, "combat", "clues", null, "Combat");
        putTag(createDiaryPage(createDiary(characterOf(diaryId), "Second"), "Page"), "other");

        MvcTestResult result = diaryTags(diaryId, "");

        assertThat(result).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("combat", "clues"));
        assertThat(result).bodyJson().extractingPath("$.list[*].pageNumbers").isEqualTo(List.of(List.of(2, 5), List.of(3)));
        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(2);
    }

    @Test
    void findAllDiaryTagsByDiary_noTags_returnsEmptyList() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(diaryTags(diaryId, "")).bodyJson().extractingPath("$.totalRows").isEqualTo(0);
    }

    @Test
    void findAllDiaryTagsByDiary_nameFilter_returnsOnlyMatchingTagsIgnoringCase() {
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
    void findAllDiaryTagsByDiary_diaryOfOtherUser_returnsNotFoundWithoutTheirTags() {
        UUID othersDiary = diaryOf(othersTaggedPage("secret"));

        MvcTestResult result = diaryTags(othersDiary, "?name=secret");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(responseBody(result)).doesNotContain("secret\"");
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

        MvcTestResult result = mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Final", storedPageVersion(pageId))).exchange();

        assertThat(result).bodyJson().extractingPath("$.tag").isEqualTo("combat");
        assertThat(storedTags(pageId)).containsExactly("combat");
    }

    @Test
    void findAllDiariesByCharacter_tag_returnsOnlyDiariesWithPartialTagIgnoringCase() {
        UUID first = diaryWithTaggedPages("Combat");
        UUID characterId = characterOf(first);
        diaryWithTaggedPage(characterId, "Second", "clues");
        createDiary(characterId, "Third");

        MvcTestResult result = diariesWithTag(characterId, "cOMB");

        assertThat(result).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.list[0].id").isEqualTo(first.toString());
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

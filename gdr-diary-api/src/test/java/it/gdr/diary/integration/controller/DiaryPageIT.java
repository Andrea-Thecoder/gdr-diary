package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.ConcurrentCalls;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class DiaryPageIT extends AbstractIntegrationTest {

    private static final String RICH_CONTENT = """
            {"type":"doc","content":[
              {"type":"paragraph","attrs":{"textAlign":"center"},"content":[
                {"type":"text","text":"Day one","marks":[{"type":"title","attrs":{"level":1}}]},
                {"type":"hardBreak"},
                {"type":"text","text":"We left the Shire 🐉","marks":[{"type":"bold"},{"type":"italic"}]}]},
              {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"Not all who wander are lost"}]}]},
              {"type":"orderedList","attrs":{"start":3,"type":"i"},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Bree"}]}]}]},
              {"type":"horizontalRule"}]}
            """;

    private static final String NOT_FROM_EDITOR = "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"javascript:alert(1)\"}}]}";
    private static final String LOCK_DIARY = "select id from diary where id = ? for update";
    private static final int PARALLEL_REQUESTS = 8;

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private static String contentOfBytes(int bytes) {
        return pageContent("a".repeat(bytes - pageContent("").length()));
    }

    private List<Object[]> storedPages(UUID diaryId) {
        return jdbc.query("select id, page_number from diary_page where diary_id = ? order by page_number",
                (row, index) -> new Object[]{row.getObject("id", UUID.class), row.getShort("page_number")}, diaryId);
    }

    private List<Short> storedPageNumbers(UUID diaryId) {
        return jdbc.queryForList("select page_number from diary_page where diary_id = ? order by page_number", Short.class, diaryId);
    }

    private static List<Short> consecutiveNumbers(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(number -> (short) number).toList();
    }

    private long storedVersion(UUID pageId) {
        return jdbc.queryForObject("select version from diary_page where id = ?", Long.class, pageId);
    }

    private List<Long> storedVersions(List<UUID> pageIds) {
        return pageIds.stream().map(this::storedVersion).toList();
    }

    private String storedText(UUID pageId) {
        return jdbc.queryForObject("select content->'content'->0->'content'->0->>'text' from diary_page where id = ?", String.class, pageId);
    }

    private String updateBody(String text, long version) {
        return "{\"content\":" + pageContent(text) + ",\"version\":" + version + "}";
    }

    private MvcTestResult putPage(UUID pageId, String body) {
        return mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult postPageContent(UUID diaryId, String content) {
        return mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + content + "}").exchange();
    }

    private List<UUID> createTwoDiariesOfSameUser() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        return List.of(createDiary(characterId, "Chapter one"), createDiary(characterId, "Chapter two"));
    }

    private MvcTestResult postPage(UUID diaryId, String text) {
        return mvc.post().uri("/diaries/{diaryId}/pages", diaryId).with(currentUser())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent(text) + "}").exchange();
    }

    private MvcTestResult deletePage(UUID pageId) {
        return mvc.delete().uri("/pages/{id}", pageId).with(currentUser()).exchange();
    }

    private List<MvcTestResult> createPagesInParallel(UUID diaryId) {
        return ConcurrentCalls.fireAtOnce(PARALLEL_REQUESTS, index -> postPage(diaryId, "Parallel " + index));
    }

    private List<MvcTestResult> updatePageInParallel(UUID pageId, long version, List<String> texts) {
        return ConcurrentCalls.fireAtOnce(texts.size(), index -> mvc.put().uri("/pages/{id}", pageId)
                .with(currentUser()).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(texts.get(index), version)).exchange());
    }

    private static String textOfWinner(List<MvcTestResult> results, List<String> texts) {
        return IntStream.range(0, results.size())
                .filter(index -> results.get(index).getResponse().getStatus() == 200)
                .mapToObj(texts::get)
                .findFirst()
                .orElseThrow();
    }

    private List<MvcTestResult> deleteInParallel(List<UUID> pageIds) {
        return ConcurrentCalls.fireAtOnce(pageIds.size(), index -> deletePage(pageIds.get(index)));
    }

    private List<Integer> createAndDeletePageWaitingForDiaryLock(UUID diaryId, UUID pageToDelete) {
        try (BlockingTransaction lock = BlockingTransaction.begin(jdbc).execute(LOCK_DIARY, diaryId)) {
            CompletableFuture<MvcTestResult> create = lock.startBlockedCall(() -> postPage(diaryId, "Racing"));
            CompletableFuture<MvcTestResult> delete = lock.startBlockedCall(() -> deletePage(pageToDelete));
            lock.commit();
            return ConcurrentCalls.statuses(List.of(create.join(), delete.join()));
        }
    }

    private MvcTestResult createPageWhileDiaryIsDeleted(UUID diaryId) {
        try (BlockingTransaction deletion = BlockingTransaction.begin(jdbc)
                .execute(LOCK_DIARY, diaryId)
                .execute("delete from diary_page where diary_id = ?", diaryId)
                .execute("delete from diary where id = ?", diaryId)) {
            CompletableFuture<MvcTestResult> create = deletion.startBlockedCall(() -> postPage(diaryId, "Too late"));
            deletion.commit();
            return create.join();
        }
    }

    private MvcTestResult deletePageWhilePageIsDeleted(UUID diaryId, UUID pageId) {
        try (BlockingTransaction other = BlockingTransaction.begin(jdbc)
                .execute(LOCK_DIARY, diaryId)
                .execute("delete from diary_page where id = ?", pageId)) {
            CompletableFuture<MvcTestResult> delete = other.startBlockedCall(() -> deletePage(pageId));
            other.commit();
            return delete.join();
        }
    }

    private MvcTestResult deletePageWhileDiaryIsDeleted(UUID diaryId, UUID pageId) {
        try (BlockingTransaction other = BlockingTransaction.begin(jdbc)
                .execute(LOCK_DIARY, diaryId)
                .execute("delete from diary_page where diary_id = ?", diaryId)
                .execute("delete from diary where id = ?", diaryId)) {
            CompletableFuture<MvcTestResult> delete = other.startBlockedCall(() -> deletePage(pageId));
            other.commit();
            return delete.join();
        }
    }

    private MvcTestResult deletePageWhileFirstPageIsDeletedAndPagesRenumbered(UUID diaryId, UUID firstPageId, UUID pageId) {
        try (BlockingTransaction other = BlockingTransaction.begin(jdbc)
                .execute(LOCK_DIARY, diaryId)
                .execute("delete from diary_page where id = ?", firstPageId)
                .execute("update diary_page set page_number = page_number - 1 where diary_id = ?", diaryId)) {
            CompletableFuture<MvcTestResult> delete = other.startBlockedCall(() -> deletePage(pageId));
            other.commit();
            return delete.join();
        }
    }

    @Test
    void createDiaryPage_threePages_numbersThemConsecutively() {
        UUID diaryId = createDiaryWithOwner();

        List<UUID> pages = createDiaryPages(diaryId, 3);

        assertThat(storedPages(diaryId)).extracting(page -> page[0], page -> page[1])
                .containsExactly(tuple(pages.get(0), (short) 1), tuple(pages.get(1), (short) 2), tuple(pages.get(2), (short) 3));
    }

    @Test
    void createDiaryPage_hundredPagesExist_returnsBadRequestAndKeepsHundredPages() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 100);

        MvcTestResult result = postPageContent(diaryId, pageContent("One too many"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(countRows("diary_page")).isEqualTo(100);
    }

    @Test
    void createDiaryPage_afterDeletingFromFullDiary_returnsCreated() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 100);
        mvc.delete().uri("/pages/{id}", pages.get(49)).exchange();

        assertThat(postPageContent(diaryId, pageContent("Room again"))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createDiaryPage_richEditorContent_returnsSameContentFromList() {
        UUID diaryId = createDiaryWithOwner();
        postPageContent(diaryId, RICH_CONTENT);

        Map<String, Object> stored = JsonPath.read(responseBody(mvc.get().uri("/diaries/{diaryId}/pages", diaryId).exchange()), "$.list[0].content");

        assertThat(stored).isEqualTo(JsonPath.parse(RICH_CONTENT).read("$"));
    }

    @Test
    void createDiaryPage_contentNotFromEditor_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(postPageContent(diaryId, NOT_FROM_EDITOR)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @CsvSource({"51200,201", "51201,400"})
    void createDiaryPage_contentAroundMaxSize_acceptsUpToTheLimit(int bytes, int expectedStatus) {
        UUID diaryId = createDiaryWithOwner();

        MvcTestResult result = postPageContent(diaryId, contentOfBytes(bytes));

        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
    }

    @Test
    void createDiaryPage_diaryOfOtherUser_returnsNotFoundAndStoresNothing() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        MvcTestResult result = postPageContent(othersDiary, pageContent("Intruder"));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(countRows("diary_page")).isZero();
    }

    @Test
    void createDiaryPage_otherDiaryOfSameUserHasPages_startsAtPageOne() {
        List<UUID> diaries = createTwoDiariesOfSameUser();
        createDiaryPages(diaries.get(0), 3);

        UUID created = createDiaryPage(diaries.get(1), "First of B");

        Short number = jdbc.queryForObject("select page_number from diary_page where id = ?", Short.class, created);
        assertThat(number).isEqualTo((short) 1);
    }

    @Test
    void createDiaryPage_parallelRequests_allReturnCreatedWithUniqueContiguousPageNumbers() {
        UUID diaryId = createDiaryWithOwner();

        List<Integer> statuses = ConcurrentCalls.statuses(createPagesInParallel(diaryId));

        assertThat(statuses).containsOnly(201);
        assertThat(storedPageNumbers(diaryId)).containsExactlyElementsOf(consecutiveNumbers(PARALLEL_REQUESTS));
    }

    @Test
    void createDiaryPage_waitingForConcurrentDeleteOfPage_bothCallsSucceedAndKeepPageNumbersConsecutive() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        List<Integer> statuses = createAndDeletePageWaitingForDiaryLock(diaryId, pages.get(1));

        assertThat(statuses).containsExactly(201, 200);
        assertThat(storedPageNumbers(diaryId)).containsExactlyElementsOf(consecutiveNumbers(4));
    }

    @Test
    void createDiaryPage_diaryDeletedWhileWaitingForLock_returnsDiaryNotFoundAndStoresNoPage() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        MvcTestResult result = createPageWhileDiaryIsDeleted(diaryId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Diary not found");
        assertThat(countRows("diary_page")).isZero();
    }

    @ParameterizedTest
    @MethodSource("spreadsOfFivePages")
    void findAllDiaryPagesByDiary_fivePages_returnsRequestedSpreadAndTotals(int page, List<Integer> expectedNumbers) {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 5);

        MvcTestResult result = mvc.get().uri("/diaries/{diaryId}/pages?page={page}", diaryId, page).exchange();

        assertThat(result).bodyJson().extractingPath("$.list[*].pageNumber").isEqualTo(expectedNumbers);
        assertThat(result).bodyJson().extractingPath("$['totalRows','totalPages']")
                .isEqualTo(Map.of("totalRows", 5, "totalPages", 3));
    }

    static Stream<Arguments> spreadsOfFivePages() {
        return Stream.of(
                Arguments.of(1, List.of(1, 2)),
                Arguments.of(2, List.of(3, 4)),
                Arguments.of(3, List.of(5)));
    }

    @Test
    void findAllDiaryPagesByDiary_emptyDiary_returnsEmptyListAndZeroTotals() {
        UUID diaryId = createDiaryWithOwner();

        MvcTestResult result = mvc.get().uri("/diaries/{diaryId}/pages", diaryId).exchange();

        assertThat(result).bodyJson().extractingPath("$.list").isEqualTo(List.of());
        assertThat(result).bodyJson().extractingPath("$['totalRows','totalPages']")
                .isEqualTo(Map.of("totalRows", 0, "totalPages", 0));
    }

    @Test
    void findAllDiaryPagesByDiary_hugePageNumber_returnsOkWithEmptyList() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 3);

        MvcTestResult result = mvc.get().uri("/diaries/{diaryId}/pages?page=2147483647", diaryId).exchange();

        assertThat(result).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.list").isEqualTo(List.of());
    }

    @Test
    void findAllDiaryPagesByDiary_pageZero_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=0", diaryId)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryPagesByDiary_twoDiariesOfSameUser_returnsOnlyPagesOfRequestedDiary() {
        List<UUID> diaries = createTwoDiariesOfSameUser();
        createDiaryPages(diaries.get(0), 2);
        List<UUID> pagesOfB = createDiaryPages(diaries.get(1), 2);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaries.get(1))).bodyJson()
                .extractingPath("$.list[*].id").isEqualTo(pagesOfB.stream().map(UUID::toString).toList());
    }

    @Test
    void findAllDiaryPagesByDiary_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", othersDiary)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPage_currentVersion_storesNewContentAndReturnsIncrementedVersion() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        long version = storedVersion(pageId);

        MvcTestResult result = putPage(pageId, updateBody("Final", version));

        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo((int) version + 1);
        assertThat(storedText(pageId)).isEqualTo("Final");
    }

    @Test
    void updateDiaryPage_staleVersion_returnsConflictAndKeepsNewerContent() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        long staleVersion = storedVersion(pageId);
        putPage(pageId, updateBody("Other tab", staleVersion));

        MvcTestResult result = putPage(pageId, updateBody("This tab", staleVersion));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(storedText(pageId)).isEqualTo("Other tab");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void updateDiaryPage_afterFirstOfThreeDeleted_followingPageWithOldVersionReturnsConflict(int index) {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        long oldVersion = storedVersion(pages.get(index));
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        MvcTestResult result = putPage(pages.get(index), updateBody("Stale", oldVersion));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateDiaryPage_contentNotFromEditor_returnsBadRequestAndKeepsContent() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        MvcTestResult result = putPage(pageId, "{\"content\":" + NOT_FROM_EDITOR + ",\"version\":" + storedVersion(pageId) + "}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(storedText(pageId)).isEqualTo("Draft");
    }

    @Test
    void updateDiaryPage_pageOfOtherUser_returnsNotFoundAndKeepsContent() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersPage = createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        MvcTestResult result = putPage(othersPage, updateBody("Hacked", storedVersion(othersPage)));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedText(othersPage)).isEqualTo("Secret");
    }

    @Test
    void updateDiaryPage_twoParallelUpdatesWithSameVersion_returnsOneOkAndOneConflictAndStoresContentOfWinner() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        List<String> texts = List.of("Tab A", "Tab B");

        List<MvcTestResult> results = updatePageInParallel(pageId, storedVersion(pageId), texts);

        assertThat(ConcurrentCalls.statuses(results)).containsExactlyInAnyOrder(200, 409);
        assertThat(storedText(pageId)).isEqualTo(textOfWinner(results, texts));
    }

    @Test
    void deleteDiaryPage_middlePage_renumbersFollowingPagesKeepingIdsContentAndBumpingTheirVersion() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);
        long firstVersion = storedVersion(pages.get(0));
        List<Long> followingVersions = storedVersions(pages.subList(2, 4));

        mvc.delete().uri("/pages/{id}", pages.get(1)).exchange();

        assertThat(storedPages(diaryId)).extracting(page -> page[0], page -> page[1])
                .containsExactly(tuple(pages.get(0), (short) 1), tuple(pages.get(2), (short) 2), tuple(pages.get(3), (short) 3));
        assertThat(List.of(storedText(pages.get(2)), storedText(pages.get(3)))).containsExactly("Page 3", "Page 4");
        assertThat(storedVersions(pages.subList(2, 4))).containsExactly(followingVersions.get(0) + 1, followingVersions.get(1) + 1);
        assertThat(storedVersion(pages.get(0))).isEqualTo(firstVersion);
    }

    @Test
    void deleteDiaryPage_firstPage_renumbersAllFollowingPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(storedPages(diaryId)).extracting(page -> page[0], page -> page[1])
                .containsExactly(tuple(pages.get(1), (short) 1), tuple(pages.get(2), (short) 2), tuple(pages.get(3), (short) 3));
    }

    @Test
    void deleteDiaryPage_lastPage_leavesVersionOfPreviousPagesUnchanged() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        List<Long> before = storedVersions(pages.subList(0, 2));

        mvc.delete().uri("/pages/{id}", pages.get(2)).exchange();

        assertThat(storedVersions(pages.subList(0, 2))).isEqualTo(before);
    }

    @Test
    void deleteDiaryPage_onlyPage_leavesEmptyDiary() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Only");

        mvc.delete().uri("/pages/{id}", pageId).exchange();

        assertThat(storedPages(diaryId)).isEmpty();
    }

    @Test
    void createDiaryPage_afterDeletingFirstOfThree_appendsAfterLastPageInPageOrder() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        UUID created = createDiaryPage(diaryId, "New last page");

        Short number = jdbc.queryForObject("select page_number from diary_page where id = ?", Short.class, created);
        assertThat(number).isEqualTo((short) 3);
        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaryId)).bodyJson()
                .extractingPath("$.list[*].content.content[0].content[0].text").isEqualTo(List.of("Page 2", "Page 3"));
        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=2", diaryId)).bodyJson()
                .extractingPath("$.list[*].pageNumber").isEqualTo(List.of(3));
    }

    @Test
    void deleteDiaryPage_twoDiariesOfSameUser_keepsPageNumbersAndVersionsOfOtherDiary() {
        List<UUID> diaries = createTwoDiariesOfSameUser();
        List<UUID> pagesOfA = createDiaryPages(diaries.get(0), 3);
        List<UUID> pagesOfB = createDiaryPages(diaries.get(1), 3);
        List<Long> before = storedVersions(pagesOfB);

        mvc.delete().uri("/pages/{id}", pagesOfA.get(0)).exchange();

        assertThat(storedPageNumbers(diaries.get(1))).containsExactlyElementsOf(consecutiveNumbers(3));
        assertThat(storedVersions(pagesOfB)).isEqualTo(before);
    }

    @Test
    void deleteDiaryPage_pageOfOtherUser_returnsNotFoundAndKeepsPageNumbersOfItsDiary() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersFirst = createDiaryPage(othersDiary, "First", TestJwt.otherUser());
        createDiaryPage(othersDiary, "Second", TestJwt.otherUser());

        MvcTestResult result = mvc.delete().uri("/pages/{id}", othersFirst).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedPageNumbers(othersDiary)).containsExactly((short) 1, (short) 2);
    }

    @Test
    void deleteDiaryPage_twoParallelDeletesOfSamePage_returnsOkAndNotFoundAndKeepsPageNumbersConsecutive() {
        UUID diaryId = createDiaryWithOwner();
        UUID target = createDiaryPages(diaryId, 3).get(1);

        List<Integer> statuses = ConcurrentCalls.statuses(deleteInParallel(List.of(target, target)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 404);
        assertThat(storedPageNumbers(diaryId)).containsExactly((short) 1, (short) 2);
    }

    @Test
    void deleteDiaryPage_parallelDeletesOfDifferentPages_bothSucceedAndKeepPageNumbersConsecutive() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        List<Integer> statuses = ConcurrentCalls.statuses(deleteInParallel(List.of(pages.get(0), pages.get(1))));

        assertThat(statuses).containsExactly(200, 200);
        assertThat(storedPageNumbers(diaryId)).containsExactlyElementsOf(consecutiveNumbers(2));
    }

    @Test
    void deleteDiaryPage_pageDeletedWhileWaitingForLock_returnsPageNotFoundAndKeepsOtherPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);

        MvcTestResult result = deletePageWhilePageIsDeleted(diaryId, pages.get(1));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Page not found");
        assertThat(storedPages(diaryId)).extracting(page -> page[0]).containsExactly(pages.get(0), pages.get(2));
    }

    @Test
    void deleteDiaryPage_diaryDeletedWhileWaitingForLock_returnsPageNotFound() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Existing");

        MvcTestResult result = deletePageWhileDiaryIsDeleted(diaryId, pageId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .extractingPath("$.violations[0].message").isEqualTo("Page not found");
    }

    @Test
    void deleteDiaryPage_pageRenumberedWhileWaitingForLock_renumbersFollowingPagesFromCurrentNumber() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        MvcTestResult result = deletePageWhileFirstPageIsDeletedAndPagesRenumbered(diaryId, pages.get(0), pages.get(2));

        assertThat(result).hasStatusOk();
        assertThat(storedPages(diaryId)).extracting(page -> page[0], page -> page[1])
                .containsExactly(tuple(pages.get(1), (short) 1), tuple(pages.get(3), (short) 2));
    }
}

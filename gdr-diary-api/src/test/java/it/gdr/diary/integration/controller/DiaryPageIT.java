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
import org.junit.jupiter.api.Test;
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

    private List<Object[]> storedPages(UUID diaryId) {
        return jdbc.query("select id, page_number from diary_page where diary_id = ? order by page_number",
                (row, index) -> new Object[]{row.getObject("id", UUID.class), row.getShort("page_number")}, diaryId);
    }

    private long storedVersion(UUID pageId) {
        return jdbc.queryForObject("select version from diary_page where id = ?", Long.class, pageId);
    }

    private String storedText(UUID pageId) {
        return jdbc.queryForObject("select content->'content'->0->'content'->0->>'text' from diary_page where id = ?", String.class, pageId);
    }

    private String updateBody(String text, long version) {
        return "{\"content\":" + pageContent(text) + ",\"version\":" + version + "}";
    }

    @Test
    void createDiaryPage_threePages_numbersThemConsecutively() {
        UUID diaryId = createDiaryWithOwner();

        List<UUID> pages = createDiaryPages(diaryId, 3);

        assertThat(storedPages(diaryId)).extracting(page -> page[0], page -> page[1])
                .containsExactly(tuple(pages.get(0), (short) 1), tuple(pages.get(1), (short) 2), tuple(pages.get(2), (short) 3));
    }

    @Test
    void createDiaryPage_hundredPagesExist_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 100);

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent("One too many") + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_hundredPagesExist_keepsHundredPages() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 100);

        mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent("One too many") + "}").exchange();

        assertThat(countRows("diary_page")).isEqualTo(100);
    }

    @Test
    void createDiaryPage_richEditorContent_returnsSameContentFromList() {
        UUID diaryId = createDiaryWithOwner();
        mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + RICH_CONTENT + "}").exchange();

        Map<String, Object> stored = JsonPath.read(responseBody(mvc.get().uri("/diaries/{diaryId}/pages", diaryId).exchange()), "$.list[0].content");

        assertThat(stored).isEqualTo(JsonPath.parse(RICH_CONTENT).read("$"));
    }

    @Test
    void createDiaryPage_contentNotFromEditor_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"src\":\"javascript:alert(1)\"}}]}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_nulCharacterInText_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent("a\\u0000b") + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDiaryPage_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", othersDiary)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent("Intruder") + "}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllDiaryPagesByDiary_secondSpread_returnsPagesThreeAndFour() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 5);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=2", diaryId)).bodyJson()
                .extractingPath("$.list[*].pageNumber").isEqualTo(List.of(3, 4));
    }

    @Test
    void findAllDiaryPagesByDiary_fivePages_returnsTotals() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 5);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaryId)).bodyJson()
                .extractingPath("$['totalRows','totalPages']").isEqualTo(Map.of("totalRows", 5, "totalPages", 3));
    }

    @Test
    void findAllDiaryPagesByDiary_diaryOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", othersDiary)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPage_currentVersion_storesNewContent() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(updateBody("Final", storedVersion(pageId))).exchange();

        assertThat(storedText(pageId)).isEqualTo("Final");
    }

    @Test
    void updateDiaryPage_currentVersion_returnsIncrementedVersion() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        long version = storedVersion(pageId);

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(updateBody("Final", version)))
                .bodyJson().extractingPath("$.version").isEqualTo((int) version + 1);
    }

    @Test
    void updateDiaryPage_staleVersion_returnsConflict() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        long staleVersion = storedVersion(pageId);
        mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(updateBody("Other tab", staleVersion)).exchange();

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(updateBody("This tab", staleVersion)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateDiaryPage_staleVersion_keepsNewerContent() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        long staleVersion = storedVersion(pageId);
        mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(updateBody("Other tab", staleVersion)).exchange();

        mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON).content(updateBody("This tab", staleVersion)).exchange();

        assertThat(storedText(pageId)).isEqualTo("Other tab");
    }

    @Test
    void deleteDiaryPage_middlePage_renumbersFollowingPagesKeepingIds() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        mvc.delete().uri("/pages/{id}", pages.get(1)).exchange();

        assertThat(storedPages(diaryId)).extracting(page -> page[0], page -> page[1])
                .containsExactly(tuple(pages.get(0), (short) 1), tuple(pages.get(2), (short) 2), tuple(pages.get(3), (short) 3));
    }

    @Test
    void deleteDiaryPage_middlePage_incrementsVersionOfRenumberedPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        long before = storedVersion(pages.get(2));

        mvc.delete().uri("/pages/{id}", pages.get(1)).exchange();

        assertThat(storedVersion(pages.get(2))).isEqualTo(before + 1);
    }

    @Test
    void deleteDiaryPage_middlePage_keepsContentOfRenumberedPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);

        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(List.of(storedText(pages.get(1)), storedText(pages.get(2)))).containsExactly("Page 2", "Page 3");
    }

    @Test
    void deleteDiaryPage_lastPage_leavesVersionOfPreviousPagesUnchanged() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        long firstPageVersion = storedVersion(pages.get(0));

        mvc.delete().uri("/pages/{id}", pages.get(2)).exchange();

        assertThat(storedVersion(pages.get(0))).isEqualTo(firstPageVersion);
    }

    @Test
    void deleteDiaryPage_thenCreate_appendsAfterLastPage() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        UUID created = createDiaryPage(diaryId, "New last page");

        Short number = jdbc.queryForObject("select page_number from diary_page where id = ?", Short.class, created);
        assertThat(number).isEqualTo((short) 3);
    }

    @Test
    void deleteDiaryPage_pageOfOtherUser_keepsPage() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersPage = createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        mvc.delete().uri("/pages/{id}", othersPage).exchange();

        assertThat(countRows("diary_page")).isEqualTo(1);
    }

    @Test
    void updateDiaryPage_pageOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersPage = createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        assertThat(mvc.put().uri("/pages/{id}", othersPage).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Hacked", storedVersion(othersPage))))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateDiaryPage_pageOfOtherUser_keepsContent() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersPage = createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        mvc.put().uri("/pages/{id}", othersPage).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Hacked", storedVersion(othersPage))).exchange();

        assertThat(storedText(othersPage)).isEqualTo("Secret");
    }

    @Test
    void updateDiaryPage_contentNotFromEditor_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"image\"}]},\"version\":" + storedVersion(pageId) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPage_contentNotFromEditor_keepsContent() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"image\"}]},\"version\":" + storedVersion(pageId) + "}").exchange();

        assertThat(storedText(pageId)).isEqualTo("Draft");
    }

    @Test
    void updateDiaryPage_contentOverMaxSize_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("a".repeat(52_000), storedVersion(pageId))))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateDiaryPage_missingVersion_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        assertThat(mvc.put().uri("/pages/{id}", pageId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent("Final") + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryPagesByDiary_emptyDiary_returnsEmptyList() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaryId)).bodyJson().extractingPath("$.list").isEqualTo(List.of());
    }

    @Test
    void findAllDiaryPagesByDiary_emptyDiary_returnsZeroTotals() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaryId)).bodyJson()
                .extractingPath("$['totalRows','totalPages']").isEqualTo(Map.of("totalRows", 0, "totalPages", 0));
    }

    @Test
    void findAllDiaryPagesByDiary_lastSpreadOfFivePages_returnsSinglePage() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 5);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=3", diaryId)).bodyJson()
                .extractingPath("$.list[*].pageNumber").isEqualTo(List.of(5));
    }

    @Test
    void findAllDiaryPagesByDiary_pageZero_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=0", diaryId)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryPagesByDiary_hugePageNumber_returnsOk() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 3);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=2147483647", diaryId)).hasStatus(HttpStatus.OK);
    }

    @Test
    void findAllDiaryPagesByDiary_hugePageNumber_returnsEmptyList() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPages(diaryId, 3);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=2147483647", diaryId)).bodyJson()
                .extractingPath("$.list").isEqualTo(List.of());
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
    void deleteDiaryPage_onlyPage_leavesEmptyDiary() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Only");

        mvc.delete().uri("/pages/{id}", pageId).exchange();

        assertThat(storedPages(diaryId)).isEmpty();
    }

    @Test
    void createDiaryPage_afterDeletingFromFullDiary_returnsCreated() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 100);
        mvc.delete().uri("/pages/{id}", pages.get(49)).exchange();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent("Room again") + "}"))
                .hasStatus(HttpStatus.CREATED);
    }

    private static final int MAX_CONTENT_BYTES = 50 * 1024;
    private static final int PARALLEL_REQUESTS = 8;

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private static String contentOfBytes(int bytes) {
        return pageContent("a".repeat(bytes - pageContent("").length()));
    }

    private List<MvcTestResult> createPagesInParallel(UUID diaryId) {
        return ConcurrentCalls.fireAtOnce(PARALLEL_REQUESTS, index -> mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .with(currentUser()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + pageContent("Parallel " + index) + "}").exchange());
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

    private List<Short> storedPageNumbers(UUID diaryId) {
        return jdbc.queryForList("select page_number from diary_page where diary_id = ? order by page_number", Short.class, diaryId);
    }

    @Test
    void createDiaryPage_parallelRequests_allReturnCreated() {
        UUID diaryId = createDiaryWithOwner();

        List<Integer> statuses = ConcurrentCalls.statuses(createPagesInParallel(diaryId));

        assertThat(statuses).containsOnly(201);
    }

    @Test
    void createDiaryPage_parallelRequests_storesUniqueContiguousPageNumbers() {
        UUID diaryId = createDiaryWithOwner();
        createPagesInParallel(diaryId);

        List<Short> numbers = storedPageNumbers(diaryId);

        assertThat(numbers).containsExactlyElementsOf(IntStream.rangeClosed(1, numbers.size()).mapToObj(number -> (short) number).toList());
    }

    @Test
    void createDiaryPage_parallelRequests_storesOnePagePerCreatedResponse() {
        UUID diaryId = createDiaryWithOwner();

        List<Integer> statuses = ConcurrentCalls.statuses(createPagesInParallel(diaryId));

        assertThat(storedPageNumbers(diaryId)).hasSize((int) statuses.stream().filter(status -> status == 201).count());
    }

    @Test
    void updateDiaryPage_twoParallelUpdatesWithSameVersion_returnsOneOkAndOneConflict() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");

        List<Integer> statuses = ConcurrentCalls.statuses(updatePageInParallel(pageId, storedVersion(pageId), List.of("Tab A", "Tab B")));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void updateDiaryPage_twoParallelUpdatesWithSameVersion_storesContentOfWinner() {
        UUID diaryId = createDiaryWithOwner();
        UUID pageId = createDiaryPage(diaryId, "Draft");
        List<String> texts = List.of("Tab A", "Tab B");

        List<MvcTestResult> results = updatePageInParallel(pageId, storedVersion(pageId), texts);

        assertThat(storedText(pageId)).isEqualTo(textOfWinner(results, texts));
    }

    @Test
    void createDiaryPage_contentExactlyAtMaxSize_returnsCreated() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + contentOfBytes(MAX_CONTENT_BYTES) + "}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createDiaryPage_contentOneByteOverMaxSize_returnsBadRequest() {
        UUID diaryId = createDiaryWithOwner();

        assertThat(mvc.post().uri("/diaries/{diaryId}/pages", diaryId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + contentOfBytes(MAX_CONTENT_BYTES + 1) + "}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllDiaryPagesByDiary_afterDeleteAndCreate_returnsFirstSpreadInPageOrder() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();
        createDiaryPage(diaryId, "New last page");

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaryId)).bodyJson()
                .extractingPath("$.list[*].content.content[0].content[0].text").isEqualTo(List.of("Page 2", "Page 3"));
    }

    @Test
    void findAllDiaryPagesByDiary_afterDeleteAndCreate_returnsNewPageInSecondSpread() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();
        createDiaryPage(diaryId, "New last page");

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages?page=2", diaryId)).bodyJson()
                .extractingPath("$.list[*].pageNumber").isEqualTo(List.of(3));
    }

    private List<UUID> createTwoDiariesOfSameUser() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        return List.of(createDiary(characterId, "Chapter one"), createDiary(characterId, "Chapter two"));
    }

    private List<Long> storedVersions(List<UUID> pageIds) {
        return pageIds.stream().map(this::storedVersion).toList();
    }

    private List<Short> pageNumbersOf(UUID diaryId) {
        return storedPageNumbers(diaryId);
    }

    @Test
    void deleteDiaryPage_twoDiariesOfSameUser_keepsPageNumbersOfOtherDiary() {
        List<UUID> diaries = createTwoDiariesOfSameUser();
        List<UUID> pagesOfA = createDiaryPages(diaries.get(0), 3);
        createDiaryPages(diaries.get(1), 3);

        mvc.delete().uri("/pages/{id}", pagesOfA.get(0)).exchange();

        assertThat(pageNumbersOf(diaries.get(1))).containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    void deleteDiaryPage_twoDiariesOfSameUser_keepsVersionsOfOtherDiary() {
        List<UUID> diaries = createTwoDiariesOfSameUser();
        List<UUID> pagesOfA = createDiaryPages(diaries.get(0), 3);
        List<UUID> pagesOfB = createDiaryPages(diaries.get(1), 3);
        List<Long> before = storedVersions(pagesOfB);

        mvc.delete().uri("/pages/{id}", pagesOfA.get(0)).exchange();

        assertThat(storedVersions(pagesOfB)).isEqualTo(before);
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
    void findAllDiaryPagesByDiary_twoDiariesOfSameUser_returnsOnlyPagesOfRequestedDiary() {
        List<UUID> diaries = createTwoDiariesOfSameUser();
        createDiaryPages(diaries.get(0), 2);
        List<UUID> pagesOfB = createDiaryPages(diaries.get(1), 2);

        assertThat(mvc.get().uri("/diaries/{diaryId}/pages", diaries.get(1))).bodyJson()
                .extractingPath("$.list[*].id").isEqualTo(pagesOfB.stream().map(UUID::toString).toList());
    }

    @Test
    void updateDiaryPage_afterFirstOfThreeDeleted_secondPageWithOldVersionReturnsConflict() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        long oldVersion = storedVersion(pages.get(1));
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(mvc.put().uri("/pages/{id}", pages.get(1)).contentType(MediaType.APPLICATION_JSON).content(updateBody("Stale", oldVersion)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateDiaryPage_afterFirstOfThreeDeleted_thirdPageWithOldVersionReturnsConflict() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        long oldVersion = storedVersion(pages.get(2));
        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(mvc.put().uri("/pages/{id}", pages.get(2)).contentType(MediaType.APPLICATION_JSON).content(updateBody("Stale", oldVersion)))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteDiaryPage_firstOfThree_incrementsVersionOfAllFollowingPages() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 3);
        List<Long> before = storedVersions(pages.subList(1, 3));

        mvc.delete().uri("/pages/{id}", pages.get(0)).exchange();

        assertThat(storedVersions(pages.subList(1, 3))).containsExactly(before.get(0) + 1, before.get(1) + 1);
    }

    private List<MvcTestResult> deleteInParallel(List<UUID> pageIds) {
        return ConcurrentCalls.fireAtOnce(pageIds.size(), index -> mvc.delete().uri("/pages/{id}", pageIds.get(index))
                .with(currentUser()).exchange());
    }

    @Test
    void deleteDiaryPage_twoParallelDeletesOfSamePage_returnsOkAndNotFound() {
        UUID diaryId = createDiaryWithOwner();
        UUID target = createDiaryPages(diaryId, 3).get(1);

        List<Integer> statuses = ConcurrentCalls.statuses(deleteInParallel(List.of(target, target)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 404);
    }

    @Test
    void deleteDiaryPage_twoParallelDeletesOfSamePage_succeedsExactlyOnce() {
        UUID diaryId = createDiaryWithOwner();
        UUID target = createDiaryPages(diaryId, 3).get(1);

        List<Integer> statuses = ConcurrentCalls.statuses(deleteInParallel(List.of(target, target)));

        assertThat(statuses).filteredOn(status -> status < 300).hasSize(1);
    }

    @Test
    void deleteDiaryPage_twoParallelDeletesOfSamePage_keepsPageNumbersConsecutive() {
        UUID diaryId = createDiaryWithOwner();
        UUID target = createDiaryPages(diaryId, 3).get(1);

        deleteInParallel(List.of(target, target));

        assertThat(pageNumbersOf(diaryId)).containsExactly((short) 1, (short) 2);
    }

    @Test
    void deleteDiaryPage_parallelDeletesOfDifferentPages_bothSucceed() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        List<Integer> statuses = ConcurrentCalls.statuses(deleteInParallel(List.of(pages.get(0), pages.get(1))));

        assertThat(statuses).containsExactly(200, 200);
    }

    @Test
    void deleteDiaryPage_parallelDeletesOfDifferentPages_keepsPageNumbersConsecutive() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        deleteInParallel(List.of(pages.get(0), pages.get(1)));

        List<Short> numbers = pageNumbersOf(diaryId);
        assertThat(numbers).containsExactlyElementsOf(IntStream.rangeClosed(1, numbers.size()).mapToObj(number -> (short) number).toList());
    }

    private static final String LOCK_DIARY = "select id from diary where id = ? for update";

    private MvcTestResult postPage(UUID diaryId, String text) {
        return mvc.post().uri("/diaries/{diaryId}/pages", diaryId).with(currentUser())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent(text) + "}").exchange();
    }

    private MvcTestResult deletePage(UUID pageId) {
        return mvc.delete().uri("/pages/{id}", pageId).with(currentUser()).exchange();
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

    @Test
    void createDiaryPage_waitingForConcurrentDeleteOfPage_keepsPageNumbersConsecutive() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        createAndDeletePageWaitingForDiaryLock(diaryId, pages.get(1));

        assertThat(pageNumbersOf(diaryId)).containsExactly((short) 1, (short) 2, (short) 3, (short) 4);
    }

    @Test
    void createDiaryPage_waitingForConcurrentDeleteOfPage_bothCallsSucceed() {
        UUID diaryId = createDiaryWithOwner();
        List<UUID> pages = createDiaryPages(diaryId, 4);

        List<Integer> statuses = createAndDeletePageWaitingForDiaryLock(diaryId, pages.get(1));

        assertThat(statuses).containsExactly(201, 200);
    }

    @Test
    void createDiaryPage_diaryDeletedWhileWaitingForLock_returnsNotFound() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        MvcTestResult result = createPageWhileDiaryIsDeleted(diaryId);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDiaryPage_diaryDeletedWhileWaitingForLock_returnsDiaryNotFoundMessage() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        MvcTestResult result = createPageWhileDiaryIsDeleted(diaryId);

        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("Diary not found");
    }

    @Test
    void createDiaryPage_diaryDeletedWhileWaitingForLock_storesNoPage() {
        UUID diaryId = createDiaryWithOwner();
        createDiaryPage(diaryId, "Existing");

        createPageWhileDiaryIsDeleted(diaryId);

        assertThat(countRows("diary_page")).isZero();
    }

    @Test
    void createDiaryPage_diaryOfOtherUser_storesNothing() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());

        mvc.post().uri("/diaries/{diaryId}/pages", othersDiary)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":" + pageContent("Intruder") + "}").exchange();

        assertThat(countRows("diary_page")).isZero();
    }

    @Test
    void deleteDiaryPage_pageOfOtherUser_returnsNotFound() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersPage = createDiaryPage(othersDiary, "Secret", TestJwt.otherUser());

        assertThat(mvc.delete().uri("/pages/{id}", othersPage)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteDiaryPage_pageOfOtherUser_keepsPageNumbersOfItsDiary() {
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        UUID othersFirst = createDiaryPage(othersDiary, "First", TestJwt.otherUser());
        createDiaryPage(othersDiary, "Second", TestJwt.otherUser());

        mvc.delete().uri("/pages/{id}", othersFirst).exchange();

        assertThat(storedPageNumbers(othersDiary)).containsExactly((short) 1, (short) 2);
    }
}

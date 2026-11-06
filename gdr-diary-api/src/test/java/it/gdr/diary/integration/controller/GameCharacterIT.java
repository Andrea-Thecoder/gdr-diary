package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class GameCharacterIT extends AbstractIntegrationTest {

    @Test
    void createGameCharacter_existingUser_storesCharacterOwnedByUser() {
        createAppUser();

        UUID id = createGameCharacter("Aragorn");

        String owner = jdbc.queryForObject(
                "select u.keycloak_id from game_character c join app_user u on u.id = c.owner_id where c.id = ?", String.class, id);
        assertThat(owner).isEqualTo(TestJwt.USER_SUBJECT);
    }

    @Test
    void createGameCharacter_userNotCreated_returnsNotFound() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\",\"kind\":\"PLAYER\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createGameCharacter_existingUser_writesAuditWithSubject() {
        createAppUser();

        UUID id = createGameCharacter("Aragorn");

        String createdBy = jdbc.queryForObject("select created_by from game_character where id = ?", String.class, id);
        assertThat(createdBy).isEqualTo(TestJwt.USER_SUBJECT);
    }

    @Test
    void getGameCharacterById_ownCharacter_returnsDetail() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters/{id}", id)).bodyJson().extractingPath("$.name").isEqualTo("Aragorn");
    }

    @Test
    void getGameCharacterById_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.get().uri("/characters/{id}", othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void findAllGameCharacters_charactersOfTwoUsers_returnsOnlyOwnCharacters() {
        createAppUser();
        createGameCharacter("Aragorn");
        createAppUser(TestJwt.otherUser());
        createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.get().uri("/characters")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn"));
    }

    @Test
    void findAllGameCharacters_nameFilter_matchesIgnoringCase() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Legolas");

        assertThat(mvc.get().uri("/characters?name=ARA")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn"));
    }

    @Test
    void findAllGameCharacters_sortByNameDescending_ordersByNameDescending() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Legolas");
        createGameCharacter("Gimli");

        assertThat(mvc.get().uri("/characters?sort=name&descending=true")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Legolas", "Gimli", "Aragorn"));
    }

    @Test
    void findAllGameCharacters_noSort_ordersByName() {
        createAppUser();
        createGameCharacter("Legolas");
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn", "Legolas"));
    }

    @Test
    void findAllGameCharacters_secondPage_returnsRemainingCharacter() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Gimli");
        createGameCharacter("Legolas");

        assertThat(mvc.get().uri("/characters?page=2&size=2")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Legolas"));
    }

    @Test
    void findAllGameCharacters_secondPage_returnsTotals() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Gimli");
        createGameCharacter("Legolas");

        assertThat(mvc.get().uri("/characters?page=2&size=2")).bodyJson()
                .extractingPath("$['page','totalRows','totalPages']").isEqualTo(Map.of("page", 2, "totalRows", 3, "totalPages", 2));
    }

    @Test
    void updateGameCharacter_ownCharacter_storesNewValues() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        mvc.put().uri("/characters/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Strider\",\"campaignName\":\"Curse of Strahd\",\"backgroundColor\":\"#000000\"}").exchange();

        assertThat(mvc.get().uri("/characters/{id}", id)).bodyJson()
                .extractingPath("$['name','campaignName','backgroundColor']")
                .isEqualTo(Map.of("name", "Strider", "campaignName", "Curse of Strahd", "backgroundColor", "#000000"));
    }

    @Test
    void updateGameCharacter_characterOfOtherUser_keepsCharacterUnchanged() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        mvc.put().uri("/characters/{id}", othersCharacter).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}").exchange();

        String name = jdbc.queryForObject("select name from game_character where id = ?", String.class, othersCharacter);
        assertThat(name).isEqualTo("Samwise");
    }

    @Test
    void deleteGameCharacter_withDiariesAndPages_deletesEverythingOfCharacter() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");
        createDiaryPage(diaryId, "Day one");

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(List.of(countRows("game_character"), countRows("diary"), countRows("diary_page"))).containsOnly(0);
    }

    @Test
    void deleteGameCharacter_withAvatarAndCover_removesFilesFromStorage() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void deleteGameCharacter_withAvatarAndCover_deletesDocumentRows() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(countRows("document")).isZero();
    }

    @Test
    void deleteGameCharacter_characterOfOtherUser_keepsCharacter() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        mvc.delete().uri("/characters/{id}", othersCharacter).exchange();

        assertThat(countRows("game_character")).isEqualTo(1);
    }

    @Test
    void updateGameCharacterAvatar_validImage_storesOneFile() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();

        assertThat(storedObjectKeys()).hasSize(1);
    }

    @Test
    void updateGameCharacterAvatar_contentNotAnImage_leavesNoFileInStorage() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        MockMultipartFile fake = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "<svg onload=alert(1)>".getBytes());

        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(fake).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void updateGameCharacterAvatar_replacingAvatar_keepsOnlyNewFile() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("first.png")).exchange();

        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("second.png")).exchange();

        String newKey = jdbc.queryForObject("select object_key from document", String.class);
        assertThat(storedObjectKeys()).containsExactly(newKey);
    }

    @Test
    void deleteGameCharacterAvatar_withAvatar_removesFileAndRow() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();

        mvc.delete().uri("/characters/{id}/avatar", characterId).exchange();

        assertThat(List.of(storedObjectKeys().size(), countRows("document"))).containsOnly(0);
    }

    @Test
    void updateGameCharacterAvatar_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.put().uri("/characters/{id}/avatar", othersCharacter).multipart().file(pngFile("avatar.png")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateGameCharacterAvatar_characterOfOtherUser_storesNoFile() throws Exception {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        mvc.put().uri("/characters/{id}/avatar", othersCharacter).multipart().file(pngFile("avatar.png")).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void deleteGameCharacterAvatar_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.delete().uri("/characters/{id}/avatar", othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteGameCharacterAvatar_characterOfOtherUser_keepsAvatar() throws Exception {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        mvc.put().uri("/characters/{id}/avatar", othersCharacter).with(TestJwt.otherUser()).multipart().file(pngFile("avatar.png")).exchange();

        mvc.delete().uri("/characters/{id}/avatar", othersCharacter).exchange();

        assertThat(storedObjectKeys()).hasSize(1);
    }

    @Test
    void findAllGameCharacters_nameFilterWithSurroundingSpaces_matchesTrimmedName() {
        createAppUser();
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters").param("name", " Aragorn ")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn"));
    }

    @Test
    void updateGameCharacterAvatar_fileOverMaxFileSize_returnsBadRequest() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, oversized);

        assertThat(mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(file)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateGameCharacterAvatar_fileOverMaxFileSize_storesNoFile() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, oversized);

        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(file).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private UUID createCharacterWith(String name, String kind, String description, String campaignName) {
        MvcTestResult result = mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":" + quoted(name) + ",\"kind\":\"" + kind + "\",\"description\":" + quoted(description)
                        + ",\"campaignName\":" + quoted(campaignName) + "}")
                .exchange();
        return UUID.fromString(json(result, "$.id"));
    }

    private List<String> idsAcrossPages(int pages, int size) {
        List<String> ids = new ArrayList<>();
        IntStream.rangeClosed(1, pages).forEach(page -> ids.addAll(JsonPath.read(
                responseBody(mvc.get().uri("/characters").param("page", String.valueOf(page)).param("size", String.valueOf(size)).exchange()),
                "$.list[*].id")));
        return ids;
    }

    @Test
    void findAllGameCharacters_injectedSortFields_keepsNameOrder() {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", "z", null);
        createCharacterWith("Legolas", "PLAYER", "a", null);

        assertThat(mvc.get().uri("/characters").param("SORT_FIELDS[description]", "description").param("sort", "description"))
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn", "Legolas"));
    }

    @Test
    void findAllGameCharacters_injectedOwnerEmailSortFields_keepsNameOrder() {
        createAppUser();
        createGameCharacter("Legolas");
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters").param("SORT_FIELDS[owner.email]", "owner.email").param("sort", "owner.email"))
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn", "Legolas"));
    }

    @Test
    void findAllGameCharacters_bogusInjectedSortFields_doesNotReturnServerError() {
        createAppUser();
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters").param("SORT_FIELDS[x]", "nonexistent").param("sort", "x")).hasStatusOk();
    }

    @Test
    void findAllGameCharacters_percentInNameFilter_matchesOnlyLiteralPercent() {
        createAppUser();
        createGameCharacter("100% Orc");
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters").param("name", "%")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("100% Orc"));
    }

    @Test
    void findAllGameCharacters_underscoreInNameFilter_matchesOnlyLiteralUnderscore() {
        createAppUser();
        createGameCharacter("snake_case");
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters").param("name", "_")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("snake_case"));
    }

    @Test
    void findAllGameCharacters_percentInCampaignFilter_matchesOnlyLiteralPercent() {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", null, "100% Chaos");
        createCharacterWith("Legolas", "PLAYER", null, "Lost Mine");

        assertThat(mvc.get().uri("/characters").param("campaignName", "%")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn"));
    }

    @Test
    void findAllGameCharacters_underscoreInCampaignFilter_matchesOnlyLiteralUnderscore() {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", null, "dark_sun");
        createCharacterWith("Legolas", "PLAYER", null, "Lost Mine");

        assertThat(mvc.get().uri("/characters").param("campaignName", "_")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn"));
    }

    @Test
    void createGameCharacter_nulCharacterInName_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\\u0000b\",\"kind\":\"PLAYER\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllGameCharacters_nulCharacterInNameFilter_doesNotReturnServerError() {
        createAppUser();
        createGameCharacter("Aragorn");

        int status = mvc.get().uri("/characters").param("name", "a\u0000b").exchange().getResponse().getStatus();

        assertThat(status).isLessThan(500);
    }

    @Test
    void findAllGameCharacters_fiveCharactersWithSameName_walkingPagesReturnsEachOnce() {
        createAppUser();
        List<UUID> created = IntStream.range(0, 5).mapToObj(i -> createGameCharacter("Twin")).toList();

        List<String> seen = idsAcrossPages(3, 2);

        assertThat(seen).containsExactlyInAnyOrderElementsOf(created.stream().map(UUID::toString).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdBy", "name;drop", "owner.email", "1"})
    void findAllGameCharacters_unknownSortKey_ordersByNameInRequestedDirection(String sort) {
        createAppUser();
        createGameCharacter("Legolas");
        createGameCharacter("Aragorn");
        createGameCharacter("Gimli");

        assertThat(mvc.get().uri("/characters").param("sort", sort).param("descending", "true"))
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Legolas", "Gimli", "Aragorn"));
    }

    @Test
    void findAllGameCharacters_sizeOver100_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.get().uri("/characters?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllGameCharacters_pageZero_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.get().uri("/characters?page=0")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllGameCharacters_sortByCampaignNameAscending_putsNullCampaignsLast() {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", null, "Lost Mine");
        createCharacterWith("Legolas", "PLAYER", null, "Curse of Strahd");
        createCharacterWith("Gimli", "PLAYER", null, null);

        assertThat(mvc.get().uri("/characters?sort=campaignName")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Legolas", "Aragorn", "Gimli"));
    }

    @Test
    void findAllGameCharacters_sortByCampaignNameDescending_putsNullCampaignsFirst() {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", null, "Lost Mine");
        createCharacterWith("Legolas", "PLAYER", null, "Curse of Strahd");
        createCharacterWith("Gimli", "PLAYER", null, null);

        assertThat(mvc.get().uri("/characters?sort=campaignName&descending=true")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Gimli", "Aragorn", "Legolas"));
    }

    @Test
    void findAllGameCharacters_campaignNameFilter_returnsOnlyMatchingCampaign() {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", null, "Lost Mine");
        createCharacterWith("Legolas", "PLAYER", null, "Curse of Strahd");

        assertThat(mvc.get().uri("/characters").param("campaignName", "STRAHD")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Legolas"));
    }

    @Test
    void findAllGameCharacters_descendingWithoutSort_ordersByNameDescending() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Legolas");
        createGameCharacter("Gimli");

        assertThat(mvc.get().uri("/characters?descending=true")).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Legolas", "Gimli", "Aragorn"));
    }

    @Test
    void updateGameCharacter_optionalFieldsNull_clearsThem() {
        createAppUser();
        UUID id = createCharacterWith("Aragorn", "PLAYER", "Ranger", "Lost Mine");

        mvc.put().uri("/characters/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\"}").exchange();

        Map<String, Object> stored = jdbc.queryForMap("select description, campaign_name, background_color from game_character where id = ?", id);
        assertThat(stored.values()).containsOnlyNulls();
    }

    @Test
    void updateGameCharacter_bodyWithDifferentKind_keepsOriginalKind() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        mvc.put().uri("/characters/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Aragorn\",\"kind\":\"GAME_MASTER\"}").exchange();

        assertThat(mvc.get().uri("/characters/{id}", id)).bodyJson().extractingPath("$.kind").isEqualTo("PLAYER");
    }

    @Test
    void createGameCharacter_gameMasterKind_storesKind() {
        createAppUser();

        UUID id = createCharacterWith("Narrator", "GAME_MASTER", null, null);

        assertThat(mvc.get().uri("/characters/{id}", id)).bodyJson().extractingPath("$.kind").isEqualTo("GAME_MASTER");
    }

    @Test
    void deleteGameCharacterAvatar_withoutAvatar_returnsOk() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        assertThat(mvc.delete().uri("/characters/{id}/avatar", id)).hasStatusOk();
    }

    @Test
    void deleteGameCharacter_userWithTwoCharacters_keepsDiariesAndPagesOfOtherCharacter() {
        createAppUser();
        UUID deleted = createGameCharacter("Aragorn");
        UUID kept = createGameCharacter("Legolas");
        createDiaryPage(createDiary(deleted, "Gone"), "Gone");
        createDiaryPage(createDiary(kept, "Still here"), "Still here");

        mvc.delete().uri("/characters/{id}", deleted).exchange();

        assertThat(List.of(countRows("game_character"), countRows("diary"), countRows("diary_page"))).containsOnly(1);
    }

    @Test
    void deleteGameCharacter_userWithTwoCharacters_keepsFilesOfOtherCharacter() throws Exception {
        createAppUser();
        UUID deleted = createGameCharacter("Aragorn");
        UUID kept = createGameCharacter("Legolas");
        UUID deletedDiary = createDiary(deleted, "Gone");
        UUID keptDiary = createDiary(kept, "Still here");
        mvc.put().uri("/characters/{id}/avatar", deleted).multipart().file(pngFile("a.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", deletedDiary).multipart().file(pngFile("b.png")).exchange();
        mvc.put().uri("/characters/{id}/avatar", kept).multipart().file(pngFile("c.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", keptDiary).multipart().file(pngFile("d.png")).exchange();

        mvc.delete().uri("/characters/{id}", deleted).exchange();

        assertThat(storedObjectKeys()).hasSize(2);
    }

    @Test
    void updateGameCharacterAvatar_missingFilePart_returnsBadRequest() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        assertThat(mvc.put().uri("/characters/{id}/avatar", id).multipart()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateGameCharacterAvatar_missingFilePart_returnsExceptionResponseBody() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        assertThat(mvc.put().uri("/characters/{id}/avatar", id).multipart()).bodyJson()
                .extractingPath("$.title").isEqualTo("BAD_REQUEST");
    }

    @Test
    void createGameCharacter_plainTextBody_returnsUnsupportedMediaType() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.TEXT_PLAIN).content("Aragorn"))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void findAllGameCharacters_userNotCreated_returnsOk() {
        assertThat(mvc.get().uri("/characters")).hasStatusOk();
    }

    @Test
    void findAllGameCharacters_userNotCreated_returnsEmptyList() {
        assertThat(mvc.get().uri("/characters")).bodyJson().extractingPath("$.list").asList().isEmpty();
    }

    @Test
    void getGameCharacterById_userNotCreated_returnsNotFound() {
        assertThat(mvc.get().uri("/characters/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteGameCharacter_characterWithoutDiaries_deletesCharacter() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(countRows("game_character")).isZero();
    }

    @Test
    void deleteGameCharacter_characterWithoutDiaries_returnsOk() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        assertThat(mvc.delete().uri("/characters/{id}", characterId)).hasStatusOk();
    }

    private static final String INSERT_DIARY = "insert into diary (id, character_id, title, created_at, updated_at, version, "
            + "created_by, updated_by) values (?, ?, 'Racing', now(), now(), 1, 'test', 'test')";
    private static final String INSERT_PAGE = "insert into diary_page (id, page_number, diary_id, created_at, updated_at, version, "
            + "content, created_by, updated_by) values (?, 1, ?, now(), now(), 1, '{\"type\":\"doc\"}'::jsonb, 'test', 'test')";

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private MvcTestResult deleteCharacterWaitingFor(BlockingTransaction concurrent, UUID characterId) {
        try (concurrent) {
            CompletableFuture<MvcTestResult> delete = concurrent.startBlockedCall(
                    () -> mvc.delete().uri("/characters/{id}", characterId).with(currentUser()).exchange());
            concurrent.commit();
            return delete.join();
        }
    }

    private MvcTestResult deleteCharacterWhileDiaryIsAdded(UUID characterId) {
        return deleteCharacterWaitingFor(BlockingTransaction.begin(jdbc).execute(INSERT_DIARY, UUID.randomUUID(), characterId), characterId);
    }

    private MvcTestResult deleteCharacterWhilePageIsAdded(UUID characterId, UUID diaryId) {
        return deleteCharacterWaitingFor(BlockingTransaction.begin(jdbc)
                .execute("select id from diary where id = ? for update", diaryId)
                .execute(INSERT_PAGE, UUID.randomUUID(), diaryId), characterId);
    }

    private MvcTestResult createCharacterWhileUserIsDeleted() {
        try (BlockingTransaction deletion = BlockingTransaction.begin(jdbc)
                .execute("delete from app_user where keycloak_id = ?", TestJwt.USER_SUBJECT)) {
            CompletableFuture<MvcTestResult> create = deletion.startBlockedCall(
                    () -> mvc.post().uri("/characters").with(currentUser()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Aragorn\",\"kind\":\"PLAYER\"}").exchange());
            deletion.commit();
            return create.join();
        }
    }

    @Test
    void deleteGameCharacter_diaryAddedBeforeCommit_returnsConflict() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = deleteCharacterWhileDiaryIsAdded(characterId);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteGameCharacter_diaryAddedBeforeCommit_returnsRetryMessage() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = deleteCharacterWhileDiaryIsAdded(characterId);

        assertThat(result).bodyJson().extractingPath("$.violations[0].message")
                .isEqualTo("The character changed while it was being deleted, try again");
    }

    @Test
    void deleteGameCharacter_diaryAddedBeforeCommit_keepsCharacter() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        deleteCharacterWhileDiaryIsAdded(characterId);

        assertThat(countRows("game_character")).isEqualTo(1);
    }

    @Test
    void deleteGameCharacter_pageAddedBeforeCommit_returnsConflict() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");

        MvcTestResult result = deleteCharacterWhilePageIsAdded(characterId, diaryId);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void deleteGameCharacter_pageAddedBeforeCommit_keepsDiaryAndPage() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");

        deleteCharacterWhilePageIsAdded(characterId, diaryId);

        assertThat(List.of(countRows("diary"), countRows("diary_page"))).containsExactly(1, 1);
    }

    @Test
    void createGameCharacter_userDeletedBeforeCommit_returnsNotFound() {
        createAppUser();

        MvcTestResult result = createCharacterWhileUserIsDeleted();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createGameCharacter_userDeletedBeforeCommit_returnsUserNotFoundMessage() {
        createAppUser();

        MvcTestResult result = createCharacterWhileUserIsDeleted();

        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("User not found");
    }

    @Test
    void updateGameCharacter_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.put().uri("/characters/{id}", othersCharacter).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteGameCharacter_characterOfOtherUser_returnsNotFound() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        assertThat(mvc.delete().uri("/characters/{id}", othersCharacter)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteGameCharacter_characterOfOtherUser_keepsItsDiariesAndPages() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersDiary = createDiary(othersCharacter, "Secret", TestJwt.otherUser());
        createDiaryPage(othersDiary, "Secret page", TestJwt.otherUser());

        mvc.delete().uri("/characters/{id}", othersCharacter).exchange();

        assertThat(countRows("diary")).isEqualTo(1);
        assertThat(countRows("diary_page")).isEqualTo(1);
    }
}

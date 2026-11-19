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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class GameCharacterIT extends AbstractIntegrationTest {

    private static final String INSERT_DIARY = "insert into diary (id, character_id, title, created_at, updated_at, version, "
            + "created_by, updated_by) values (?, ?, 'Racing', now(), now(), 1, 'test', 'test')";
    private static final String INSERT_PAGE = "insert into diary_page (id, page_number, diary_id, created_at, updated_at, version, "
            + "content, created_by, updated_by) values (?, 1, ?, now(), now(), 1, '{\"type\":\"doc\"}'::jsonb, 'test', 'test')";

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private UUID createCharacterWith(String name, String kind, String campaignName) {
        MvcTestResult result = mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":" + quoted(name) + ",\"kind\":\"" + kind + "\",\"campaignName\":" + quoted(campaignName) + "}")
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

    private static RequestPostProcessor currentUser() {
        return TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    }

    private MockMultipartFile oversizedPng() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);
        return new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, oversized);
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
    void createGameCharacter_existingUser_storesCharacterOwnedByUserWithAudit() {
        createAppUser();

        UUID id = createGameCharacter("Aragorn");

        Map<String, Object> stored = jdbc.queryForMap(
                "select u.keycloak_id, c.created_by from game_character c join app_user u on u.id = c.owner_id where c.id = ?", id);
        assertThat(stored).containsEntry("keycloak_id", TestJwt.USER_SUBJECT).containsEntry("created_by", TestJwt.USER_SUBJECT);
    }

    @Test
    void createGameCharacter_gameMasterKind_storesKind() {
        createAppUser();

        UUID id = createCharacterWith("Narrator", "GAME_MASTER", null);

        assertThat(mvc.get().uri("/characters/{id}", id)).bodyJson().extractingPath("$.kind").isEqualTo("GAME_MASTER");
    }

    @Test
    void createGameCharacter_userNotCreated_returnsNotFound() {
        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\",\"kind\":\"PLAYER\"}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createGameCharacter_nulCharacterInName_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\\u0000b\",\"kind\":\"PLAYER\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGameCharacter_plainTextBody_returnsUnsupportedMediaType() {
        createAppUser();

        assertThat(mvc.post().uri("/characters").contentType(MediaType.TEXT_PLAIN).content("Aragorn"))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void createGameCharacter_userDeletedBeforeCommit_returnsNotFoundWithUserNotFoundMessage() {
        createAppUser();

        MvcTestResult result = createCharacterWhileUserIsDeleted();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("User not found");
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
    void getGameCharacterById_userNotCreated_returnsNotFound() {
        assertThat(mvc.get().uri("/characters/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
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
    void findAllGameCharacters_userNotCreated_returnsOkWithEmptyList() {
        MvcTestResult result = mvc.get().uri("/characters").exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.list").asList().isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"name,ARA", "name,'  Aragorn '", "campaignName,STRAHD"})
    void findAllGameCharacters_textFilter_matchesIgnoringCaseAndSurroundingSpaces(String filter, String value) {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", "Curse of Strahd");
        createCharacterWith("Legolas", "PLAYER", "Lost Mine");

        assertThat(mvc.get().uri("/characters").param(filter, value)).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn"));
    }

    @ParameterizedTest
    @CsvSource({"name,%,100% Orc", "name,_,snake_case", "campaignName,%,Aragorn", "campaignName,_,Legolas"})
    void findAllGameCharacters_wildcardInFilter_matchesOnlyTheLiteralCharacter(String filter, String value, String expected) {
        createAppUser();
        createCharacterWith("100% Orc", "PLAYER", "plain");
        createCharacterWith("snake_case", "PLAYER", "plain");
        createCharacterWith("Aragorn", "PLAYER", "100% Chaos");
        createCharacterWith("Legolas", "PLAYER", "dark_sun");

        assertThat(mvc.get().uri("/characters").param(filter, value)).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of(expected));
    }

    @Test
    void findAllGameCharacters_nulCharacterInNameFilter_doesNotReturnServerError() {
        createAppUser();
        createGameCharacter("Aragorn");

        int status = mvc.get().uri("/characters").param("name", "a\u0000b").exchange().getResponse().getStatus();

        assertThat(status).isLessThan(500);
    }

    @Test
    void findAllGameCharacters_noSort_ordersByNameAscending() {
        createAppUser();
        createGameCharacter("Legolas");
        createGameCharacter("Aragorn");

        assertThat(mvc.get().uri("/characters")).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn", "Legolas"));
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

    @ParameterizedTest
    @ValueSource(strings = {"name", "createdBy", "name;drop", "owner.email", "1"})
    void findAllGameCharacters_sortKeyDescending_ordersByNameDescendingEvenIfKeyIsUnknown(String sort) {
        createAppUser();
        createGameCharacter("Legolas");
        createGameCharacter("Aragorn");
        createGameCharacter("Gimli");

        assertThat(mvc.get().uri("/characters").param("sort", sort).param("descending", "true"))
                .bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Legolas", "Gimli", "Aragorn"));
    }

    @ParameterizedTest
    @CsvSource({"campaignName,Legolas,Aragorn,Gimli", "'campaignName&descending=true',Gimli,Aragorn,Legolas"})
    void findAllGameCharacters_sortByCampaignName_putsNullCampaignsAtTheEndOfTheDirection(String sort, String first, String second, String third) {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", "Lost Mine");
        createCharacterWith("Legolas", "PLAYER", "Curse of Strahd");
        createCharacterWith("Gimli", "PLAYER", null);

        assertThat(mvc.get().uri("/characters?sort=" + sort)).bodyJson()
                .extractingPath("$.list[*].name").isEqualTo(List.of(first, second, third));
    }

    @ParameterizedTest
    @CsvSource({"description,description", "owner.email,owner.email", "x,nonexistent"})
    void findAllGameCharacters_injectedSortFields_keepsNameOrder(String key, String property) {
        createAppUser();
        createCharacterWith("Aragorn", "PLAYER", null);
        createCharacterWith("Legolas", "PLAYER", null);

        MvcTestResult result = mvc.get().uri("/characters").param("SORT_FIELDS[" + key + "]", property).param("sort", key).exchange();

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Aragorn", "Legolas"));
    }

    @Test
    void findAllGameCharacters_secondPage_returnsRemainingCharacterAndTotals() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Gimli");
        createGameCharacter("Legolas");

        MvcTestResult result = mvc.get().uri("/characters?page=2&size=2").exchange();

        assertThat(result).bodyJson().extractingPath("$.list[*].name").isEqualTo(List.of("Legolas"));
        assertThat(result).bodyJson().extractingPath("$['page','totalRows','totalPages']").isEqualTo(Map.of("page", 2, "totalRows", 3, "totalPages", 2));
    }

    @Test
    void findAllGameCharacters_fiveCharactersWithSameName_walkingPagesReturnsEachOnce() {
        createAppUser();
        List<UUID> created = IntStream.range(0, 5).mapToObj(i -> createGameCharacter("Twin")).toList();

        List<String> seen = idsAcrossPages(3, 2);

        assertThat(seen).containsExactlyInAnyOrderElementsOf(created.stream().map(UUID::toString).toList());
    }

    @Test
    void findAllGameCharacters_sizeOver100_returnsBadRequest() {
        createAppUser();

        assertThat(mvc.get().uri("/characters?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateGameCharacter_ownCharacter_returnsAndStoresNewValuesKeepingKind() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");
        Map<String, String> expected = Map.of("name", "Strider", "campaignName", "Curse of Strahd", "backgroundColor", "#000000", "kind", "PLAYER");

        MvcTestResult updated = mvc.put().uri("/characters/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Strider\",\"kind\":\"GAME_MASTER\",\"campaignName\":\"Curse of Strahd\",\"backgroundColor\":\"#000000\"}").exchange();

        assertThat(List.of(updated, mvc.get().uri("/characters/{id}", id).exchange()))
                .allSatisfy(result -> assertThat(result).bodyJson()
                        .extractingPath("$['name','campaignName','backgroundColor','kind']").isEqualTo(expected));
    }

    @Test
    void updateGameCharacter_optionalFieldsNull_clearsThem() {
        createAppUser();
        UUID id = createCharacterWith("Aragorn", "PLAYER", "Lost Mine");

        mvc.put().uri("/characters/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aragorn\"}").exchange();

        Map<String, Object> stored = jdbc.queryForMap("select campaign_name, background_color from game_character where id = ?", id);
        assertThat(stored.values()).containsOnlyNulls();
    }

    @Test
    void updateGameCharacter_characterOfOtherUser_returnsNotFoundAndKeepsCharacter() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        MvcTestResult result = mvc.put().uri("/characters/{id}", othersCharacter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select name from game_character where id = ?", String.class, othersCharacter)).isEqualTo("Samwise");
    }

    @Test
    void deleteGameCharacter_characterWithoutDiaries_deletesCharacterAndReturnsOk() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(result).hasStatusOk();
        assertThat(countRows("game_character")).isZero();
    }

    @Test
    void deleteGameCharacter_withDiariesPagesAvatarAndCover_deletesEverythingAndFiles() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");
        createDiaryPage(diaryId, "Day one");
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();

        mvc.delete().uri("/characters/{id}", characterId).exchange();

        assertThat(List.of(countRows("game_character"), countRows("diary"), countRows("diary_page"), countRows("document"))).containsOnly(0);
        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void deleteGameCharacter_userWithTwoCharacters_keepsEverythingOfOtherCharacter() throws Exception {
        createAppUser();
        UUID deleted = createGameCharacter("Aragorn");
        UUID kept = createGameCharacter("Legolas");
        UUID deletedDiary = createDiary(deleted, "Gone");
        UUID keptDiary = createDiary(kept, "Still here");
        createDiaryPage(deletedDiary, "Gone");
        createDiaryPage(keptDiary, "Still here");
        mvc.put().uri("/characters/{id}/avatar", deleted).multipart().file(pngFile("a.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", deletedDiary).multipart().file(pngFile("b.png")).exchange();
        mvc.put().uri("/characters/{id}/avatar", kept).multipart().file(pngFile("c.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", keptDiary).multipart().file(pngFile("d.png")).exchange();

        mvc.delete().uri("/characters/{id}", deleted).exchange();

        assertThat(List.of(countRows("game_character"), countRows("diary"), countRows("diary_page"), countRows("document"), storedObjectKeys().size()))
                .containsExactly(1, 1, 1, 2, 2);
    }

    @Test
    void deleteGameCharacter_characterOfOtherUser_returnsNotFoundAndKeepsData() {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        UUID othersDiary = createDiary(othersCharacter, "Secret", TestJwt.otherUser());
        createDiaryPage(othersDiary, "Secret page", TestJwt.otherUser());

        MvcTestResult result = mvc.delete().uri("/characters/{id}", othersCharacter).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(List.of(countRows("game_character"), countRows("diary"), countRows("diary_page"))).containsOnly(1);
    }

    @Test
    void deleteGameCharacter_diaryAddedBeforeCommit_returnsConflictWithRetryMessageAndKeepsCharacter() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = deleteCharacterWhileDiaryIsAdded(characterId);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.violations[0].message")
                .isEqualTo("The character changed while it was being deleted, try again");
        assertThat(countRows("game_character")).isEqualTo(1);
    }

    @Test
    void deleteGameCharacter_pageAddedBeforeCommit_returnsConflictAndKeepsDiaryAndPage() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");

        MvcTestResult result = deleteCharacterWhilePageIsAdded(characterId, diaryId);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(List.of(countRows("diary"), countRows("diary_page"))).containsExactly(1, 1);
    }

    @Test
    void updateGameCharacterAvatar_validImage_storesOneFile() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("avatar.png")).exchange();

        assertThat(storedObjectKeys()).hasSize(1);
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
    void updateGameCharacterAvatar_contentNotAnImage_leavesNoFileInStorage() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        MockMultipartFile fake = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "<svg onload=alert(1)>".getBytes());

        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(fake).exchange();

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void updateGameCharacterAvatar_fileOverMaxFileSize_returnsBadRequestAndStoresNoFile() throws Exception {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");

        MvcTestResult result = mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(oversizedPng()).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void updateGameCharacterAvatar_missingFilePart_returnsBadRequestExceptionResponse() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        MvcTestResult result = mvc.put().uri("/characters/{id}/avatar", id).multipart().exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.title").isEqualTo("BAD_REQUEST");
    }

    @Test
    void updateGameCharacterAvatar_characterOfOtherUser_returnsNotFoundAndStoresNoFile() throws Exception {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());

        MvcTestResult result = mvc.put().uri("/characters/{id}/avatar", othersCharacter).multipart().file(pngFile("avatar.png")).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedObjectKeys()).isEmpty();
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
    void deleteGameCharacterAvatar_withoutAvatar_returnsOk() {
        createAppUser();
        UUID id = createGameCharacter("Aragorn");

        assertThat(mvc.delete().uri("/characters/{id}/avatar", id)).hasStatusOk();
    }

    @Test
    void deleteGameCharacterAvatar_characterOfOtherUser_returnsNotFoundAndKeepsAvatar() throws Exception {
        createAppUser(TestJwt.otherUser());
        UUID othersCharacter = createGameCharacter("Samwise", TestJwt.otherUser());
        mvc.put().uri("/characters/{id}/avatar", othersCharacter).with(TestJwt.otherUser()).multipart().file(pngFile("avatar.png")).exchange();

        MvcTestResult result = mvc.delete().uri("/characters/{id}/avatar", othersCharacter).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedObjectKeys()).hasSize(1);
    }
}

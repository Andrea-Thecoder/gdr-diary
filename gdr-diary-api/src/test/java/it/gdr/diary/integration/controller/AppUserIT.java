package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.ConcurrentCalls;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.integration.support.WithMockJwt;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WithMockJwt
class AppUserIT extends AbstractIntegrationTest {

    private void patchSettings(String settings) {
        mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":" + settings + "}").exchange();
    }

    private void createUserWithEverything() {
        createAppUser();
        UUID characterId = createGameCharacter("Aragorn");
        UUID diaryId = createDiary(characterId, "Chapter one");
        createDiaryPage(diaryId, "Day one");
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();
        mvc.put().uri("/characters/{id}/avatar", characterId).multipart().file(pngFile("aragorn.png")).exchange();
        mvc.put().uri("/diaries/{id}/cover", diaryId).multipart().file(pngFile("cover.png")).exchange();
    }

    @Test
    void createAppUser_firstCall_storesSubjectAndEmailOfToken() {
        createAppUser();

        Map<String, Object> user = jdbc.queryForMap("select keycloak_id, email from app_user");
        assertThat(user).containsExactlyInAnyOrderEntriesOf(Map.of("keycloak_id", TestJwt.USER_SUBJECT, "email", TestJwt.USER_EMAIL));
    }

    @Test
    void createAppUser_calledTwice_returnsSameIdAndStoresOneUser() {
        UUID first = createAppUser();

        UUID second = createAppUser();

        assertThat(second).isEqualTo(first);
        assertThat(countRows("app_user")).isEqualTo(1);
    }

    @Test
    void createAppUser_emailChangedInToken_alignsStoredEmail() {
        createAppUser(TestJwt.as(TestJwt.USER_SUBJECT, "old@shire.example", "user"));

        createAppUser();

        assertThat(jdbc.queryForObject("select email from app_user", String.class)).isEqualTo(TestJwt.USER_EMAIL);
    }

    @Test
    void getAppUserBySubject_userNotCreated_returnsNotFound() {
        assertThat(mvc.get().uri("/me")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getAppUserBySubject_newUser_returnsEmptySettingsAndIsNotPremium() {
        createAppUser();

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$").asMap()
                .containsEntry("settings", Map.of()).containsEntry("premium", false);
    }

    @Test
    void updateAppUserSettings_twoPatches_mergesTopLevelKeys() {
        createAppUser();
        patchSettings("{\"font\":\"Lora\",\"fontSize\":14}");

        patchSettings("{\"fontSize\":16,\"darkMode\":true}");

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.settings")
                .isEqualTo(Map.of("font", "Lora", "fontSize", 16, "darkMode", true));
    }

    @Test
    void updateAppUserSettings_allKeysRemoved_storesNullInDatabase() {
        createAppUser();
        patchSettings("{\"font\":\"Lora\"}");

        patchSettings("{\"font\":null}");

        assertThat(jdbc.queryForObject("select settings is null from app_user", Boolean.class)).isTrue();
    }

    @Test
    void updateAppUserSettings_nestedObject_storesItAsJsonb() {
        createAppUser();

        patchSettings("{\"editor\":{\"spellcheck\":true,\"zoom\":1.5}}");

        assertThat(jdbc.queryForObject("select settings->'editor'->>'zoom' from app_user", String.class)).isEqualTo("1.5");
    }

    @Test
    void updateAppUserAvatar_validImage_returnsDownloadableUrlAndStoresFileUnderDocumentKey() throws Exception {
        createAppUser();
        String url = json(mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange(), "$.payload");

        HttpResponse<byte[]> download = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());

        assertThat(download.body()).isEqualTo(PNG);
        String objectKey = jdbc.queryForObject("select object_key from document", String.class);
        assertThat(storedObjectKeys()).containsExactly(objectKey);
    }

    @Test
    void updateAppUserAvatar_replacingAvatar_keepsOnlyNewFile() throws Exception {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("first.png")).exchange();

        mvc.put().uri("/me/avatar").multipart().file(pngFile("second.png")).exchange();

        assertThat(jdbc.queryForList("select object_key from document", String.class)).isEqualTo(storedObjectKeys());
    }

    @Test
    void deleteAppUserAvatar_withAvatar_removesFileAndRow() throws Exception {
        createAppUser();
        mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();

        mvc.delete().uri("/me/avatar").exchange();

        assertThat(List.of(storedObjectKeys().size(), countRows("document"))).containsOnly(0);
    }

    @Test
    void deleteAppUser_userWithEverything_deletesAllRowsAndFiles() throws Exception {
        createUserWithEverything();

        mvc.delete().uri("/me").exchange();

        assertThat(List.of(countRows("app_user"), countRows("game_character"), countRows("diary"), countRows("diary_page"),
                countRows("document"), storedObjectKeys().size())).containsOnly(0);
    }

    @Test
    void deleteAppUser_calledAgain_returnsOk() {
        createAppUser();
        mvc.delete().uri("/me").exchange();

        assertThat(mvc.delete().uri("/me")).hasStatusOk();
    }

    @Test
    void deleteAppUser_otherUserWithData_keepsOtherUserData() {
        createAppUser();
        UUID othersDiary = createDiaryWithOwner(TestJwt.otherUser());
        createDiaryPage(othersDiary, "Still mine", TestJwt.otherUser());

        mvc.delete().uri("/me").exchange();

        assertThat(List.of(countRows("app_user"), countRows("game_character"), countRows("diary"), countRows("diary_page"))).containsOnly(1);
    }

    @Test
    void deleteAppUser_loginOlderThanMaxAge_returnsStepUpChallengeAndDeletesNothing() throws Exception {
        createUserWithEverything();

        MvcTestResult result = mvc.delete().uri("/me").with(TestJwt.authenticatedAt(Instant.now().minus(Duration.ofMinutes(10)))).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).headers().hasValue(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"insufficient_user_authentication\", "
                        + "error_description=\"A more recent authentication is required\", max_age=\"300\"");
        assertThat(List.of(countRows("app_user"), countRows("game_character"), countRows("diary"), countRows("diary_page"), storedObjectKeys().size()))
                .doesNotContain(0);
    }

    @Test
    void deleteAppUser_tokenWithoutAuthTime_returnsUnauthorized() {
        createAppUser();

        assertThat(mvc.delete().uri("/me").with(TestJwt.authenticatedAt(null))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteAppUser_userAlreadyDeletedAndOldLogin_returnsUnauthorized() {
        assertThat(mvc.delete().uri("/me").with(TestJwt.authenticatedAt(Instant.now().minus(Duration.ofMinutes(10)))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updateAppUserSettings_overMaxSize_returnsBadRequestAndKeepsStoredSettings() {
        createAppUser();
        patchSettings("{\"font\":\"Lora\"}");

        MvcTestResult result = mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\":{\"notes\":\"" + "a".repeat(11_000) + "\"}}").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.settings").isEqualTo(Map.of("font", "Lora"));
    }

    @Test
    void updateAppUserAvatar_pdfContent_returnsBadRequestAndStoresNothing() throws Exception {
        createAppUser();
        MockMultipartFile pdf = new MockMultipartFile("file", "avatar.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 content".getBytes());

        MvcTestResult result = mvc.put().uri("/me/avatar").multipart().file(pdf).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(List.of(storedObjectKeys().size(), countRows("document"))).containsOnly(0);
    }

    private static final int PARALLEL_REQUESTS = 8;

    @Test
    void createAppUser_parallelFirstCalls_neverFailStoreOneUserAndReturnSameId() {
        List<MvcTestResult> results = ConcurrentCalls.fireAtOnce(PARALLEL_REQUESTS, index -> mvc.post().uri("/me")
                .with(TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user")).exchange());

        List<String> ids = results.stream().filter(result -> result.getResponse().getStatus() == 200)
                .map(result -> json(result, "$.payload")).distinct().toList();
        assertThat(ConcurrentCalls.statuses(results)).allMatch(status -> status < 500);
        assertThat(countRows("app_user")).isEqualTo(1);
        assertThat(ids).hasSize(1);
    }

    @Test
    void updateAppUserSettings_nullForOneKey_removesOnlyThatKey() {
        createAppUser();
        patchSettings("{\"font\":\"Lora\",\"fontSize\":14,\"darkMode\":true}");

        patchSettings("{\"fontSize\":null}");

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.settings").isEqualTo(Map.of("font", "Lora", "darkMode", true));
    }

    @Test
    void updateAppUserSettings_nestedObjectSentAgain_replacesWholeObject() {
        createAppUser();
        patchSettings("{\"editor\":{\"spellcheck\":true,\"zoom\":1.5}}");

        patchSettings("{\"editor\":{\"zoom\":2}}");

        assertThat(mvc.get().uri("/me")).bodyJson().extractingPath("$.settings.editor").isEqualTo(Map.of("zoom", 2));
    }

    @Test
    void updateAppUserSettings_userNotCreated_returnsNotFound() {
        assertThat(mvc.patch().uri("/me/settings").contentType(MediaType.APPLICATION_JSON).content("{\"settings\":{\"font\":\"Lora\"}}"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteAppUserAvatar_withoutAvatar_returnsOk() {
        createAppUser();

        assertThat(mvc.delete().uri("/me/avatar")).hasStatusOk();
    }

    @Test
    void deleteAppUserAvatar_userNotCreated_returnsNotFound() {
        assertThat(mvc.delete().uri("/me/avatar")).hasStatus(HttpStatus.NOT_FOUND);
    }

    private static RequestPostProcessor tokenWithoutEmail() {
        return TestJwt.as(TestJwt.USER_SUBJECT, null, "user");
    }

    private List<MvcTestResult> patchSettingsInParallel() {
        return ConcurrentCalls.fireAtOnce(2, index -> mvc.patch().uri("/me/settings")
                .with(TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\":{\"key" + index + "\":\"value\"}}").exchange());
    }

    @Test
    void updateAppUserAvatar_userNotCreated_returnsNotFoundAndStoresNoFile() throws Exception {
        MvcTestResult result = mvc.put().uri("/me/avatar").multipart().file(pngFile("me.png")).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void createAppUser_afterDeleteWithSameToken_recreatesUserWithNewId() {
        UUID firstId = createAppUser();
        mvc.delete().uri("/me").exchange();

        UUID secondId = createAppUser();

        assertThat(secondId).isNotEqualTo(firstId);
    }

    @Test
    void getAppUserBySubject_afterDeleteWithSameToken_returnsNotFound() {
        createAppUser();
        mvc.delete().uri("/me").exchange();

        assertThat(mvc.get().uri("/me")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createAppUser_tokenWithoutEmailClaim_returnsBadRequestAndKeepsStoredEmail() {
        createAppUser();

        MvcTestResult result = mvc.post().uri("/me").with(tokenWithoutEmail()).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select email from app_user", String.class)).isEqualTo(TestJwt.USER_EMAIL);
    }

    @Test
    void deleteAppUser_charactersWithoutDiaries_returnsOkAndDeletesUserAndCharacters() {
        createAppUser();
        createGameCharacter("Aragorn");
        createGameCharacter("Legolas");

        MvcTestResult result = mvc.delete().uri("/me").exchange();

        assertThat(result).hasStatusOk();
        assertThat(List.of(countRows("app_user"), countRows("game_character"))).containsOnly(0);
    }

    @Test
    void updateAppUserSettings_twoParallelPatches_answerOkOrConflictOnly() {
        createAppUser();

        List<Integer> statuses = ConcurrentCalls.statuses(patchSettingsInParallel());

        assertThat(statuses).isSubsetOf(200, 409);
    }

    private static final String INSERT_CHARACTER = "insert into game_character (id, owner_id, created_at, updated_at, version, "
            + "name, kind, created_by, updated_by) values (?, ?, now(), now(), 1, 'Racing', 'PLAYER', 'test', 'test')";

    @Test
    void deleteAppUser_characterAddedBeforeCommit_returnsConflictWithRetryMessageAndKeepsUser() {
        UUID userId = createAppUser();
        MvcTestResult result;
        try (BlockingTransaction creation = BlockingTransaction.begin(jdbc).execute(INSERT_CHARACTER, UUID.randomUUID(), userId)) {
            CompletableFuture<MvcTestResult> delete = creation.startBlockedCall(
                    () -> mvc.delete().uri("/me").with(TestJwt.authenticatedAt(Instant.now())).exchange());
            creation.commit();
            result = delete.join();
        }

        assertThat(result).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.violations[0].message")
                .isEqualTo("The account changed while it was being deleted, try again");
        assertThat(countRows("app_user")).isEqualTo(1);
    }
}
